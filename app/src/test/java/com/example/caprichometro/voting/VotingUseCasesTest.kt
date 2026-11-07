package com.example.caprichometro.voting

import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.StandardVerdictStrategy
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVoteRepository : VoteRepository {
    val votes = mutableListOf<Vote>()

    override suspend fun castVote(vote: Vote): Result<Unit> {
        votes.removeAll { it.capriceId == vote.capriceId && it.voterId == vote.voterId }
        votes.add(vote)
        return Result.success(Unit)
    }

    override fun observeVotes(capriceId: String): Flow<List<Vote>> {
        return flowOf(votes.filter { it.capriceId == capriceId })
    }

    override suspend fun getVotes(capriceId: String): Result<List<Vote>> {
        return Result.success(votes.filter { it.capriceId == capriceId })
    }

    override suspend fun getUserVote(capriceId: String, voterId: String): Result<Vote?> {
        return Result.success(votes.firstOrNull { it.capriceId == capriceId && it.voterId == voterId })
    }
}

class VotingUseCasesTest {

    private val fakeRepo = FakeVoteRepository()
    private val castVoteUseCase = CastVoteUseCase(fakeRepo)
    private val observeVotesUseCase = ObserveVotesUseCase(fakeRepo)
    private val calculateVerdictUseCase = CalculateVerdictUseCase(StandardVerdictStrategy())

    private val testCaprice = Caprice(
        id = "caprice_1",
        userId = "author_123",
        type = "Compra",
        question = "¿Me compro esta chaqueta?",
        answer1 = "Sí, cómpratela",
        answer2 = "No, es carísima",
        imageUrl = "",
        timestamp = System.currentTimeMillis() // Activo ahora mismo
    )

    @Test
    fun `amigo puede emitir voto validamente en capricho activo`() = runTest {
        val result = castVoteUseCase.execute(testCaprice, voterId = "friend_456", option = 1)
        assertTrue(result.isSuccess)
        assertEquals(1, fakeRepo.votes.size)
        assertEquals(1, fakeRepo.votes.first().option)
    }

    @Test
    fun `autor no puede votar en su propio capricho`() = runTest {
        val result = castVoteUseCase.execute(testCaprice, voterId = "author_123", option = 1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("propio capricho") == true)
    }

    @Test
    fun `no se puede votar en capricho expirado`() = runTest {
        val expiredCaprice = testCaprice.copy(timestamp = System.currentTimeMillis() - 400_000L)
        val result = castVoteUseCase.execute(expiredCaprice, voterId = "friend_456", option = 1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("cerrado") == true)
    }

    @Test
    fun `observeVotes emite la lista de votos en tiempo real`() = runTest {
        fakeRepo.castVote(Vote(capriceId = "caprice_1", voterId = "f1", option = 1))
        fakeRepo.castVote(Vote(capriceId = "caprice_1", voterId = "f2", option = 2))

        val votes = observeVotesUseCase.execute("caprice_1").first()
        assertEquals(2, votes.size)
    }

    @Test
    fun `calcular veredicto con mayoria de opcion 1`() {
        val votes = listOf(
            Vote(capriceId = "caprice_1", voterId = "f1", option = 1),
            Vote(capriceId = "caprice_1", voterId = "f2", option = 1),
            Vote(capriceId = "caprice_1", voterId = "f3", option = 2)
        )
        val verdict = calculateVerdictUseCase.execute(testCaprice, votes)
        assertEquals(1, verdict.winningOption)
        assertEquals(3, verdict.totalVotes)
        assertTrue(verdict.option1Percentage > 60f)
    }

    @Test
    fun `calcular veredicto con empate tecnico`() {
        val votes = listOf(
            Vote(capriceId = "caprice_1", voterId = "f1", option = 1),
            Vote(capriceId = "caprice_1", voterId = "f2", option = 2)
        )
        val verdict = calculateVerdictUseCase.execute(testCaprice, votes)
        assertNull(verdict.winningOption)
        assertEquals(50f, verdict.option1Percentage, 0.01f)
        assertEquals(50f, verdict.option2Percentage, 0.01f)
        assertTrue(verdict.headline.contains("Empate"))
    }

    @Test
    fun `calcular veredicto sin ningun voto`() {
        val verdict = calculateVerdictUseCase.execute(testCaprice, emptyList())
        assertNull(verdict.winningOption)
        assertEquals(0, verdict.totalVotes)
        assertTrue(verdict.subtitle.contains("duda"))
    }
}
