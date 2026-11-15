package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CapriceNearbyTest {

    @Test
    fun `Builder normaliza nearbyCell y isVisibleTo abre visibilidad publica solo si hay nearbyCell`() {
        val publicCaprice = Caprice.Builder()
            .setUserId("author_1")
            .setQuestion("¿Salgo ahora o espero?")
            .setOptions("Sal ya", "Espera")
            .setVisibleToUserIds(listOf("author_1"))
            .setNearbyCell("  v1_2608_3525  ")
            .build()

        assertThat(publicCaprice.nearbyCell).isEqualTo("v1_2608_3525")
        assertThat(publicCaprice.isVisibleTo("stranger_99")).isTrue()
        assertThat(publicCaprice.isVisibleTo("")).isFalse()

        val privateCaprice = Caprice.Builder()
            .setUserId("author_1")
            .setQuestion("¿Solo para mí?")
            .setOptions("A", "B")
            .setVisibleToUserIds(listOf("author_1"))
            .setNearbyCell("   ")
            .build()

        assertThat(privateCaprice.nearbyCell).isNull()
        assertThat(privateCaprice.isVisibleTo("stranger_99")).isFalse()
        assertThat(privateCaprice.isVisibleTo("author_1")).isTrue()
    }

    @Test
    fun `no amigo autenticado puede votar en capricho publico con nearbyCell pero no en privado`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val friendshipRepo = mockk<FriendshipRepository>()

        coEvery { voteRepo.getUserVote(any(), "stranger") } returns Result.success(null)
        coEvery { voteRepo.castVote(any()) } returns Result.success(Unit)
        coEvery { friendshipRepo.isBlocked("author", "stranger") } returns Result.success(false)
        every { friendshipRepo.getFriendships("stranger") } returns flowOf(emptyList())

        val useCase = CastVoteUseCase(voteRepo, friendshipRepo)

        val publicCaprice = Caprice(
            id = "pub_1", userId = "author", question = "¿Público?", answer1 = "Sí", answer2 = "No",
            timestamp = System.currentTimeMillis(), nearbyCell = "v1_2608_3525"
        )
        val friendsOnlyCaprice = Caprice(
            id = "priv_1", userId = "author", question = "¿Amigos?", answer1 = "Sí", answer2 = "No",
            timestamp = System.currentTimeMillis(), nearbyCell = null
        )

        assertThat(useCase.execute(publicCaprice, "stranger", 1).isSuccess).isTrue()
        val deniedPrivate = useCase.execute(friendsOnlyCaprice, "stranger", 1)
        assertThat(deniedPrivate.isFailure).isTrue()
        assertThat(deniedPrivate.exceptionOrNull()?.message).contains("amigos")
    }

    @Test
    fun `bloqueos en ambos sentidos voto unico y limite de 5 minutos se mantienen en Para ti`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val friendshipRepo = mockk<FriendshipRepository>()
        val useCase = CastVoteUseCase(voteRepo, friendshipRepo)

        val activePublic = Caprice(
            id = "pub_2", userId = "author", question = "¿Compra?", answer1 = "Sí", answer2 = "No",
            timestamp = System.currentTimeMillis(), nearbyCell = "v1_2608_3525"
        )

        // 1. Bloqueo bidireccional impide el voto
        coEvery { voteRepo.getUserVote("pub_2", "blocked_voter") } returns Result.success(null)
        coEvery { friendshipRepo.isBlocked("author", "blocked_voter") } returns Result.success(true)
        val blockedResult = useCase.execute(activePublic, "blocked_voter", 2)
        assertThat(blockedResult.isFailure).isTrue()
        assertThat(blockedResult.exceptionOrNull()?.message).contains("bloqueo")
        coVerify(exactly = 0) { voteRepo.castVote(any()) }

        // 2. Voto único impide sobreescribir un voto previo
        coEvery { voteRepo.getUserVote("pub_2", "repeat_voter") } returns Result.success(
            Vote(capriceId = "pub_2", voterId = "repeat_voter", option = 1)
        )
        val duplicateResult = useCase.execute(activePublic, "repeat_voter", 2)
        assertThat(duplicateResult.isFailure).isTrue()
        assertThat(duplicateResult.exceptionOrNull()?.message).contains("Ya has votado")

        // 3. Amigo aceptado sin bloqueo sí puede votar en capricho privado
        val friendsCaprice = activePublic.copy(id = "priv_ok", nearbyCell = null)
        coEvery { voteRepo.getUserVote("priv_ok", "friend") } returns Result.success(null)
        coEvery { friendshipRepo.isBlocked("author", "friend") } returns Result.success(false)
        every { friendshipRepo.getFriendships("friend") } returns flowOf(
            listOf(
                Friendship(
                    id = "author_friend",
                    memberIDs = listOf("author", "friend"),
                    requestedBy = "author",
                    status = FriendshipStatus.ACCEPTED
                )
            )
        )
        coEvery { voteRepo.castVote(any()) } returns Result.success(Unit)
        assertThat(useCase.execute(friendsCaprice, "friend", 1).isSuccess).isTrue()
    }
}
