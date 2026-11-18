package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.UpdateFinalDecisionUseCase
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.ui.ActivePollViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivePollViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val castVoteUseCase: CastVoteUseCase = mockk()
    private val observeVotesUseCase: ObserveVotesUseCase = mockk()
    private val voteRepository: VoteRepository = mockk()
    private val updateFinalDecisionUseCase: UpdateFinalDecisionUseCase = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `castVote actualiza el estado a Voted y en fallo muestra error limpiable`() = runTest(testDispatcher) {
        val activeCaprice = Caprice(
            id = "cap_live",
            userId = "author_1",
            question = "¿Vamos al cine?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = System.currentTimeMillis() - 30_000L
        )
        coEvery { voteRepository.getUserVote("cap_live", "voter_1") } returns Result.success(null)
        every { observeVotesUseCase.execute("cap_live") } returns flowOf(emptyList())

        val vm = ActivePollViewModel(
            castVoteUseCase = castVoteUseCase,
            observeVotesUseCase = observeVotesUseCase,
            calculateVerdictUseCase = CalculateVerdictUseCase(),
            voteRepository = voteRepository,
            updateFinalDecisionUseCase = updateFinalDecisionUseCase
        )

        try {
            vm.initialize(activeCaprice, "voter_1", authorUsername = "adrian")
            testScheduler.runCurrent()

            coEvery { castVoteUseCase.execute(activeCaprice, "voter_1", 1) } returns
                Result.failure(IllegalStateException("Error de red"))

            vm.castVote(1)
            testScheduler.runCurrent()

            assertThat(vm.state.value.isCastingVote).isFalse()
            assertThat(vm.state.value.errorMessage).isEqualTo("Error de red")
            vm.clearError()
            assertThat(vm.state.value.errorMessage).isNull()

            coEvery { castVoteUseCase.execute(activeCaprice, "voter_1", 2) } returns Result.success(Unit)
            vm.castVote(2)
            testScheduler.runCurrent()

            assertThat(vm.state.value.isCastingVote).isFalse()
            assertThat(vm.state.value.userVote?.capriceId).isEqualTo("cap_live")
            assertThat(vm.state.value.userVote?.voterId).isEqualTo("voter_1")
            assertThat(vm.state.value.userVote?.option).isEqualTo(2)
            assertThat(vm.state.value.pollState).isInstanceOf(PollState.Voted::class.java)
        } finally {
            vm.reset()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `confirmFinalDecision guarda decision final e invoca callback en encuesta cerrada`() = runTest(testDispatcher) {
        val closedTimestamp = System.currentTimeMillis() - PollTiming.POLL_DURATION_MILLIS - 10_000L
        val expiredCaprice = Caprice(
            id = "cap_done",
            userId = "author_1",
            question = "¿Compro entradas?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = closedTimestamp
        )
        val savedCaprice = expiredCaprice.copy(finalDecision = 1, finalDecisionAt = closedTimestamp + 350_000L)

        coEvery { voteRepository.getUserVote("cap_done", "author_1") } returns Result.success(null)
        every { observeVotesUseCase.execute("cap_done") } returns flowOf(
            listOf(Vote("cap_done", "friend_1", 1), Vote("cap_done", "friend_2", 2))
        )
        coEvery { updateFinalDecisionUseCase.execute(expiredCaprice, "author_1", 1) } returns Result.success(savedCaprice)

        val vm = ActivePollViewModel(
            castVoteUseCase = castVoteUseCase,
            observeVotesUseCase = observeVotesUseCase,
            calculateVerdictUseCase = CalculateVerdictUseCase(),
            voteRepository = voteRepository,
            updateFinalDecisionUseCase = updateFinalDecisionUseCase
        )

        try {
            vm.initialize(expiredCaprice, "author_1")
            testScheduler.runCurrent()

            var callbackCaprice: Caprice? = null
            vm.confirmFinalDecision(1) { callbackCaprice = it }
            testScheduler.runCurrent()

            assertThat(vm.state.value.isSavingFinalDecision).isFalse()
            assertThat(vm.state.value.caprice?.finalDecision).isEqualTo(1)
            assertThat(callbackCaprice).isEqualTo(savedCaprice)
            assertThat(vm.state.value.errorMessage).isNull()
        } finally {
            vm.reset()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun `resolveVoterProfilesIfNeeded reutiliza perfiles conocidos y no repite lecturas`() = runTest {
        val userRepo = mockk<UserProfileRepository>()
        coEvery { userRepo.getUserById("u2") } returns Result.success(UserProfile(uid = "u2", username = "bruno"))

        val vm = ActivePollViewModel(
            castVoteUseCase = mockk(relaxed = true),
            observeVotesUseCase = mockk(relaxed = true),
            calculateVerdictUseCase = CalculateVerdictUseCase(),
            voteRepository = mockk(relaxed = true),
            userProfileRepository = userRepo
        )

        val votes = listOf(
            Vote(capriceId = "cap_1", voterId = "u1", option = 1, timestamp = 100L),
            Vote(capriceId = "cap_1", voterId = "u2", option = 2, timestamp = 200L)
        )
        val known = mapOf("u1" to UserProfile(uid = "u1", username = "ana"))

        vm.resolveVoterProfilesIfNeeded(votes, knownProfiles = known)
        testScheduler.runCurrent()
        vm.resolveVoterProfilesIfNeeded(votes, knownProfiles = known)
        testScheduler.runCurrent()

        assertThat(vm.state.value.voterProfiles.keys).containsExactlyInAnyOrder("u1", "u2")
        assertThat(vm.state.value.voterProfiles["u2"]?.username).isEqualTo("bruno")
        coVerify(exactly = 0) { userRepo.getUserById("u1") }
        coVerify(exactly = 1) { userRepo.getUserById("u2") }
    }
}
