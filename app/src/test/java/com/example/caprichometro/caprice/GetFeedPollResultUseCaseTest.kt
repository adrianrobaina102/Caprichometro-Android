package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.voting.GetFeedPollResultUseCase
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetFeedPollResultUseCaseTest {

    @Test
    fun `calcula porcentajes ganador y top 3 votantes ordenados reutilizando knownProfiles`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val userRepo = mockk<UserProfileRepository>()

        val votes = listOf(
            Vote(capriceId = "cap_1", voterId = "u4", option = 1, timestamp = 400L),
            Vote(capriceId = "cap_1", voterId = "u1", option = 1, timestamp = 100L),
            Vote(capriceId = "cap_1", voterId = "u3", option = 1, timestamp = 300L),
            Vote(capriceId = "cap_1", voterId = "u2", option = 1, timestamp = 200L),
            Vote(capriceId = "cap_1", voterId = "u5", option = 2, timestamp = 250L)
        )
        coEvery { voteRepo.getVotes("cap_1") } returns Result.success(votes)
        coEvery { userRepo.getUserById("u3") } returns Result.success(UserProfile(uid = "u3", username = "carlos"))
        coEvery { userRepo.getUserById("u5") } returns Result.failure(IllegalStateException("no profile"))

        val known = mapOf(
            "u1" to UserProfile(uid = "u1", username = "ana"),
            "u2" to UserProfile(uid = "u2", username = "bruno")
        )

        val useCase = GetFeedPollResultUseCase(voteRepo, userRepo)
        val result = useCase.execute("cap_1", knownProfiles = known).getOrThrow()

        assertThat(result.totalVotes).isEqualTo(5)
        assertThat(result.option1Percentage).isEqualTo(80)
        assertThat(result.option2Percentage).isEqualTo(20)
        assertThat(result.winningOption).isEqualTo(1)
        assertThat(result.option1TotalVoters).isEqualTo(4)
        assertThat(result.option2TotalVoters).isEqualTo(1)
        assertThat(result.option1VoterAvatars.map { it.uid }).containsExactly("u1", "u2", "u3")
        assertThat(result.option2VoterAvatars.map { it.uid }).containsExactly("u5")
        coVerify(exactly = 0) { userRepo.getUserById("u1") }
        coVerify(exactly = 0) { userRepo.getUserById("u2") }
        coVerify(exactly = 0) { userRepo.getUserById("u4") }
    }

    @Test
    fun `empate o sin votos no marcan ganadora`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val userRepo = mockk<UserProfileRepository>()
        coEvery { voteRepo.getVotes("cap_tie") } returns Result.success(
            listOf(
                Vote(capriceId = "cap_tie", voterId = "u1", option = 1, timestamp = 100L),
                Vote(capriceId = "cap_tie", voterId = "u2", option = 2, timestamp = 200L)
            )
        )
        coEvery { voteRepo.getVotes("cap_empty") } returns Result.success(emptyList())
        coEvery { userRepo.getUserById(any()) } returns Result.success(null)

        val useCase = GetFeedPollResultUseCase(voteRepo, userRepo)

        val tie = useCase.execute("cap_tie").getOrThrow()
        assertThat(tie.winningOption).isNull()
        assertThat(tie.option1Percentage).isEqualTo(50)
        assertThat(tie.option2Percentage).isEqualTo(50)

        val empty = useCase.execute("cap_empty").getOrThrow()
        assertThat(empty.totalVotes).isEqualTo(0)
        assertThat(empty.winningOption).isNull()
    }
}
