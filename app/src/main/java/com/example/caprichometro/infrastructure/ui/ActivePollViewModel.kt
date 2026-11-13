package com.example.caprichometro.infrastructure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.UpdateFinalDecisionUseCase
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.domain.model.VerdictResult
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivePollUiState(
    val caprice: Caprice? = null,
    val authorUsername: String? = null,
    val currentUserId: String = "",
    val pollState: PollState = PollState.Active(PollNotificationScheduler.POLL_DURATION_MILLIS),
    val votes: List<Vote> = emptyList(),
    val userVote: Vote? = null,
    val isCastingVote: Boolean = false,
    val isSavingFinalDecision: Boolean = false,
    val errorMessage: String? = null
)

class ActivePollViewModel(
    private val castVoteUseCase: CastVoteUseCase,
    private val observeVotesUseCase: ObserveVotesUseCase,
    private val calculateVerdictUseCase: CalculateVerdictUseCase,
    private val voteRepository: VoteRepository,
    private val updateFinalDecisionUseCase: UpdateFinalDecisionUseCase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ActivePollUiState())
    val state: StateFlow<ActivePollUiState> = _state.asStateFlow()

    private var votesJob: Job? = null
    private var tickerJob: Job? = null

    fun reset() {
        votesJob?.cancel()
        tickerJob?.cancel()
        votesJob = null
        tickerJob = null
        _state.value = ActivePollUiState()
    }

    fun initialize(caprice: Caprice, currentUserId: String, authorUsername: String? = null) {
        val previousCaprice = _state.value.caprice
        val isSameUserAndCaprice = previousCaprice?.id == caprice.id && _state.value.currentUserId == currentUserId
        if (isSameUserAndCaprice && votesJob?.isActive == true && tickerJob?.isActive == true) {
            return
        }

        votesJob?.cancel()
        tickerJob?.cancel()

        _state.value = ActivePollUiState(
            caprice = caprice,
            authorUsername = authorUsername,
            currentUserId = currentUserId,
            pollState = computeInitialPollState(caprice, currentUserId)
        )

        // 1. Cargamos el voto emitido previamente por el usuario actual (si existe)
        viewModelScope.launch {
            val userVoteResult = voteRepository.getUserVote(caprice.id, currentUserId)
            val userVote = userVoteResult.getOrNull()
            _state.update { current ->
                val updatedVotes = if (userVote != null && current.votes.none { it.voterId == userVote.voterId }) {
                    current.votes + userVote
                } else current.votes
                current.copy(
                    userVote = userVote,
                    pollState = resolveCurrentPollState(current.caprice ?: caprice, current.currentUserId, userVote, updatedVotes)
                )
            }
        }

        // 2. Escucha en tiempo real de todos los votos en la subcolección de Firestore
        votesJob = viewModelScope.launch {
            observeVotesUseCase.execute(caprice.id).collect { votesList ->
                _state.update { current ->
                    val myVote = current.userVote ?: votesList.firstOrNull { it.voterId == current.currentUserId }
                    current.copy(
                        votes = votesList,
                        userVote = myVote,
                        pollState = resolveCurrentPollState(current.caprice ?: caprice, current.currentUserId, myVote, votesList)
                    )
                }
            }
        }

        // 3. Temporizador de cuenta atrás reactivo que actualiza cada 1s y dispara expiración
        tickerJob = viewModelScope.launch {
            val duration = PollNotificationScheduler.POLL_DURATION_MILLIS
            while (true) {
                val elapsed = System.currentTimeMillis() - caprice.timestamp
                val remaining = (duration - elapsed).coerceAtLeast(0L)

                _state.update { current ->
                    current.copy(
                        pollState = resolveCurrentPollState(
                            caprice = current.caprice ?: caprice,
                            currentUserId = current.currentUserId,
                            userVote = current.userVote,
                            votes = current.votes,
                            remainingOverride = remaining
                        )
                    )
                }

                if (remaining <= 0L) {
                    break
                }
                delay(1000L)
            }
        }
    }

    fun castVote(option: Int) {
        val caprice = _state.value.caprice ?: return
        val currentUserId = _state.value.currentUserId
        if (currentUserId.isBlank() || _state.value.isCastingVote) return

        viewModelScope.launch {
            _state.update { it.copy(isCastingVote = true, errorMessage = null) }

            val result = castVoteUseCase.execute(caprice, currentUserId, option)

            result.fold(
                onSuccess = {
                    val newVote = Vote(capriceId = caprice.id, voterId = currentUserId, option = option)
                    _state.update { current ->
                        val updatedVotes = current.votes.filterNot { it.voterId == currentUserId } + newVote
                        current.copy(
                            isCastingVote = false,
                            userVote = newVote,
                            votes = updatedVotes,
                            pollState = resolveCurrentPollState(caprice, currentUserId, newVote, updatedVotes)
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isCastingVote = false,
                            errorMessage = error.message ?: "No se pudo emitir el voto"
                        )
                    }
                }
            )
        }
    }

    fun confirmFinalDecision(decision: Int, onUpdated: ((Caprice) -> Unit)? = null) {
        val caprice = _state.value.caprice ?: return
        val currentUserId = _state.value.currentUserId
        val useCase = updateFinalDecisionUseCase ?: return
        if (currentUserId.isBlank() || _state.value.isSavingFinalDecision) return

        viewModelScope.launch {
            _state.update { it.copy(isSavingFinalDecision = true, errorMessage = null) }
            val result = useCase.execute(caprice, currentUserId, decision)
            result.fold(
                onSuccess = { updatedCaprice ->
                    _state.update {
                        it.copy(
                            caprice = updatedCaprice,
                            isSavingFinalDecision = false,
                            errorMessage = null
                        )
                    }
                    onUpdated?.invoke(updatedCaprice)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSavingFinalDecision = false,
                            errorMessage = error.message ?: "No se pudo guardar tu decisión final"
                        )
                    }
                }
            )
        }
    }

    private fun computeInitialPollState(caprice: Caprice, currentUserId: String): PollState {
        val remaining = (PollNotificationScheduler.POLL_DURATION_MILLIS - (System.currentTimeMillis() - caprice.timestamp))
            .coerceAtLeast(0L)
        return if (remaining <= 0L) {
            PollState.Expired(calculateVerdictUseCase.execute(caprice, emptyList()), remainingMillis = 0L)
        } else if (caprice.userId == currentUserId) {
            PollState.Author(remainingMillis = remaining, option1Percentage = 0f, option2Percentage = 0f, totalVotes = 0)
        } else {
            PollState.Active(remainingMillis = remaining)
        }
    }

    private fun resolveCurrentPollState(
        caprice: Caprice,
        currentUserId: String,
        userVote: Vote?,
        votes: List<Vote>,
        remainingOverride: Long? = null
    ): PollState {
        val remaining = remainingOverride ?: (PollNotificationScheduler.POLL_DURATION_MILLIS - (System.currentTimeMillis() - caprice.timestamp))
            .coerceAtLeast(0L)

        if (remaining <= 0L) {
            val verdict = calculateVerdictUseCase.execute(caprice, votes)
            return PollState.Expired(verdictResult = verdict, remainingMillis = 0L)
        }

        val totalVotes = votes.size
        val countOpt1 = votes.count { it.option == 1 }
        val countOpt2 = votes.count { it.option == 2 }

        val pct1 = if (totalVotes > 0) (countOpt1.toFloat() / totalVotes) * 100f else 0f
        val pct2 = if (totalVotes > 0) (countOpt2.toFloat() / totalVotes) * 100f else 0f

        return if (caprice.userId == currentUserId) {
            PollState.Author(
                remainingMillis = remaining,
                option1Percentage = pct1,
                option2Percentage = pct2,
                totalVotes = totalVotes
            )
        } else if (userVote != null) {
            PollState.Voted(
                selectedOption = userVote.option,
                remainingMillis = remaining,
                option1Percentage = pct1,
                option2Percentage = pct2,
                totalVotes = totalVotes
            )
        } else {
            PollState.Active(remainingMillis = remaining)
        }
    }

    override fun onCleared() {
        super.onCleared()
        votesJob?.cancel()
        tickerJob?.cancel()
    }
}
