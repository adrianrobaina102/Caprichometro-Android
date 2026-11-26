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

    @Test
    fun `CastVoteUseCase permite rectificar voto solo cuando allowChange es true`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val activeCaprice = Caprice(
            id = "cap_active",
            userId = "author_1",
            question = "¿Azul o rojo?",
            answer1 = "Azul",
            answer2 = "Rojo",
            timestamp = System.currentTimeMillis() - 30_000L
        )
        coEvery { voteRepo.getUserVote("cap_active", "voter_1") } returns Result.success(
            Vote(capriceId = "cap_active", voterId = "voter_1", option = 1)
        )
        coEvery { voteRepo.updateVote(any()) } returns Result.success(Unit)

        val useCase = CastVoteUseCase(voteRepo, friendshipRepository = null)

        val blockedWithoutFlag = useCase.execute(activeCaprice, "voter_1", 2)
        assertThat(blockedWithoutFlag.isFailure).isTrue()

        val allowedWithChange = useCase.execute(activeCaprice, "voter_1", 2, allowChange = true)
        assertThat(allowedWithChange.isSuccess).isTrue()
        coVerify(exactly = 1) {
            voteRepo.updateVote(match { it.voterId == "voter_1" && it.option == 2 })
        }
    }

    @Test
    fun `castVote guarda pendingVoteRetry al fallar y retryPendingVoteIfAny lo reintenta con exito`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val castVoteUseCase = mockk<CastVoteUseCase>()
        val observeVotesUseCase = mockk<ObserveVotesUseCase>()
        val calculateVerdictUseCase = CalculateVerdictUseCase()
        val activeCaprice = Caprice(
            id = "cap_retry",
            userId = "author_1",
            question = "¿Lo pillo?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = System.currentTimeMillis() - 15_000L
        )
        coEvery { voteRepo.getUserVote("cap_retry", "voter_1") } returns Result.success(null)
        every { observeVotesUseCase.execute("cap_retry") } returns flowOf(emptyList())
        coEvery { castVoteUseCase.execute(activeCaprice, "voter_1", 1, allowChange = false) } returnsMany listOf(
            Result.failure(RuntimeException("Sin conexión")),
            Result.success(Unit)
        )

        val vm = ActivePollViewModel(
            castVoteUseCase = castVoteUseCase,
            observeVotesUseCase = observeVotesUseCase,
            calculateVerdictUseCase = calculateVerdictUseCase,
            voteRepository = voteRepo
        )
        vm.initialize(activeCaprice, currentUserId = "voter_1")
        testScheduler.runCurrent()

        vm.castVote(option = 1)
        testScheduler.runCurrent()

        assertThat(vm.state.value.errorMessage).isEqualTo("Sin conexión")
        assertThat(vm.state.value.pendingVoteRetry?.capriceId).isEqualTo("cap_retry")
        assertThat(vm.state.value.pendingVoteRetry?.option).isEqualTo(1)

        vm.retryPendingVoteIfAny()
        testScheduler.runCurrent()

        assertThat(vm.state.value.errorMessage).isNull()
        assertThat(vm.state.value.pendingVoteRetry).isNull()
        assertThat(vm.state.value.userVote?.option).isEqualTo(1)
    }

    @Test
    fun `refreshFinalDecision actualiza finalDecision desde servidor si coincide el capricho`() = runTest {
        val voteRepo = mockk<VoteRepository>()
        val detailsRepo = mockk<com.example.caprichometro.domain.repository.CapriceDetailsRepository>()
        val closedCaprice = Caprice(
            id = "cap_closed",
            userId = "author_1",
            question = "¿Vamos?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = System.currentTimeMillis() - 400_000L
        )
        val serverCaprice = closedCaprice.copy(finalDecision = 2, finalDecisionAt = 123_456L)
        coEvery { voteRepo.getUserVote("cap_closed", "voter_1") } returns Result.success(null)
        coEvery { detailsRepo.getCapriceById("cap_closed") } returns Result.success(serverCaprice)

        val vm = ActivePollViewModel(
            castVoteUseCase = mockk(relaxed = true),
            observeVotesUseCase = mockk {
                every { execute("cap_closed") } returns flowOf(emptyList())
            },
            calculateVerdictUseCase = CalculateVerdictUseCase(),
            voteRepository = voteRepo,
            capriceDetailsRepository = detailsRepo
        )
        vm.initialize(closedCaprice, currentUserId = "voter_1")
        testScheduler.runCurrent()

        vm.refreshFinalDecision()
        testScheduler.runCurrent()

        assertThat(vm.state.value.caprice?.finalDecision).isEqualTo(2)
        assertThat(vm.state.value.caprice?.finalDecisionAt).isEqualTo(123_456L)
    }
}
