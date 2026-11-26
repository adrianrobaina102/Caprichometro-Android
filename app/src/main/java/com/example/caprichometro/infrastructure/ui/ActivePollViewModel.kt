package com.example.caprichometro.infrastructure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.GetApproximatePlaceNameUseCase
import com.example.caprichometro.application.usecase.UpdateFinalDecisionUseCase
import com.example.caprichometro.application.usecase.voting.CalculateVerdictUseCase
import com.example.caprichometro.application.usecase.voting.CastVoteUseCase
import com.example.caprichometro.application.usecase.voting.ObserveVotesUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollState
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.VerdictResult
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.CapriceDetailsRepository
import com.example.caprichometro.domain.repository.VoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

data class PendingVoteAction(
    val capriceId: String,
    val option: Int,
    val allowChange: Boolean
)

data class ActivePollUiState(
    val caprice: Caprice? = null,
    val authorUsername: String? = null,
    val currentUserId: String = "",
    val pollState: PollState = PollState.Active(PollTiming.POLL_DURATION_MILLIS),
    val votes: List<Vote> = emptyList(),
    val userVote: Vote? = null,
    val isCastingVote: Boolean = false,
    val isSavingFinalDecision: Boolean = false,
    val approximatePlaceName: String? = null,
    val approximateDistanceMeters: Double? = null,
    val isResolvingPlaceName: Boolean = false,
    val voterProfiles: Map<String, UserProfile> = emptyMap(),
    val errorMessage: String? = null,
    val pendingVoteRetry: PendingVoteAction? = null
)

class ActivePollViewModel(
    private val castVoteUseCase: CastVoteUseCase,
    private val observeVotesUseCase: ObserveVotesUseCase,
    private val calculateVerdictUseCase: CalculateVerdictUseCase,
    private val voteRepository: VoteRepository,
    private val updateFinalDecisionUseCase: UpdateFinalDecisionUseCase? = null,
    private val getApproximatePlaceNameUseCase: GetApproximatePlaceNameUseCase? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val capriceDetailsRepository: CapriceDetailsRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ActivePollUiState())
    val state: StateFlow<ActivePollUiState> = _state.asStateFlow()

    private var votesJob: Job? = null
    private var userVoteJob: Job? = null
    private var tickerJob: Job? = null
    private var placeNameJob: Job? = null
    private val requestedVoterIds = ConcurrentHashMap.newKeySet<String>()
    private var hasLoadedVotesForCurrentPoll: Boolean = false

    fun clearError() {
        _state.update { it.copy(errorMessage = null, pendingVoteRetry = null) }
    }

    fun resolveVoterProfilesIfNeeded(
        votes: List<Vote>,
        knownProfiles: Map<String, UserProfile> = emptyMap()
    ) {
        if (votes.isEmpty()) return
        val existing = _state.value.voterProfiles
        val mergedKnown = existing + knownProfiles.filterKeys { it.isNotBlank() }
        if (mergedKnown.size > existing.size) {
            _state.update { it.copy(voterProfiles = it.voterProfiles + mergedKnown) }
        }
        val repo = userProfileRepository ?: return
        val missingIds = votes.map { it.voterId }
            .distinct()
            .filter { it.isNotBlank() && !mergedKnown.containsKey(it) && requestedVoterIds.add(it) }
        if (missingIds.isEmpty()) return

        viewModelScope.launch {
            val semaphore = Semaphore(6)
            val fetched = coroutineScope {
                missingIds.map { uid ->
                    async {
                        semaphore.withPermit {
                            val profile = runCatching { repo.getUserById(uid).getOrNull() }.getOrNull()
                            if (profile == null) {
                                requestedVoterIds.remove(uid)
                                null
                            } else {
                                uid to profile
                            }
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
            if (fetched.isNotEmpty()) {
                _state.update { current ->
                    current.copy(voterProfiles = current.voterProfiles + fetched)
                }
            }
        }
    }

    fun reset() {
        votesJob?.cancel()
        userVoteJob?.cancel()
        tickerJob?.cancel()
        placeNameJob?.cancel()
        votesJob = null
        userVoteJob = null
        tickerJob = null
        placeNameJob = null
        requestedVoterIds.clear()
        hasLoadedVotesForCurrentPoll = false
        _state.value = ActivePollUiState()
    }

    fun initialize(
        caprice: Caprice,
        currentUserId: String,
        authorUsername: String? = null,
        approximateDistanceMeters: Double? = null,
        authorLocationCell: String? = null
    ) {
        val previousState = _state.value
        val previousCaprice = previousState.caprice
        val isSameUserAndCaprice = previousCaprice?.id == caprice.id && previousState.currentUserId == currentUserId
        val isAlreadyTracking = votesJob?.isActive == true ||
            userVoteJob?.isActive == true ||
            (previousState.pollState is PollState.Expired && hasLoadedVotesForCurrentPoll)
        if (isSameUserAndCaprice && isAlreadyTracking) {
            val mergedCaprice = if (caprice.finalDecision == null && previousCaprice.finalDecision != null) {
                caprice.copy(
                    finalDecision = previousCaprice.finalDecision,
                    finalDecisionAt = previousCaprice.finalDecisionAt
                )
            } else {
                caprice
            }
            val cell = mergedCaprice.effectiveDistanceCell(authorLocationCell)
            val shouldResolvePlaceName = cell != null &&
                previousState.approximatePlaceName == null &&
                placeNameJob?.isActive != true &&
                getApproximatePlaceNameUseCase != null
            if ((approximateDistanceMeters != null && previousState.approximateDistanceMeters == null) ||
                previousCaprice != mergedCaprice ||
                (authorUsername != null && previousState.authorUsername != authorUsername) ||
                shouldResolvePlaceName
            ) {
                _state.update { current ->
                    current.copy(
                        caprice = mergedCaprice,
                        authorUsername = authorUsername ?: current.authorUsername,
                        approximateDistanceMeters = current.approximateDistanceMeters ?: approximateDistanceMeters,
                        isResolvingPlaceName = current.isResolvingPlaceName || shouldResolvePlaceName,
                        pollState = if (previousCaprice != mergedCaprice) {
                            resolveCurrentPollState(mergedCaprice, current.currentUserId, current.userVote, current.votes)
                        } else current.pollState
                    )
                }
            }
            if (cell != null &&
                getApproximatePlaceNameUseCase != null &&
                previousState.approximatePlaceName == null &&
                placeNameJob?.isActive != true
            ) {
                placeNameJob = viewModelScope.launch {
                    val resolved = try {
                        getApproximatePlaceNameUseCase.execute(cell)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    _state.update { current ->
                        if (current.caprice?.id == caprice.id) {
                            current.copy(
                                approximatePlaceName = resolved,
                                isResolvingPlaceName = false
                            )
                        } else current
                    }
                }
            }
            return
        }

        votesJob?.cancel()
        userVoteJob?.cancel()
        tickerJob?.cancel()
        placeNameJob?.cancel()
        requestedVoterIds.clear()
        hasLoadedVotesForCurrentPoll = false

        val cell = caprice.effectiveDistanceCell(authorLocationCell)
        _state.value = ActivePollUiState(
            caprice = caprice,
            authorUsername = authorUsername,
            currentUserId = currentUserId,
            pollState = computeInitialPollState(caprice, currentUserId),
            approximateDistanceMeters = approximateDistanceMeters,
            isResolvingPlaceName = cell != null && getApproximatePlaceNameUseCase != null
        )

        if (cell != null && getApproximatePlaceNameUseCase != null) {
            placeNameJob = viewModelScope.launch {
                val resolved = try {
                    getApproximatePlaceNameUseCase.execute(cell)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                _state.update { current ->
                    if (current.caprice?.id == caprice.id) {
                        current.copy(
                            approximatePlaceName = resolved,
                            isResolvingPlaceName = false
                        )
                    } else current
                }
            }
        }

        // 1. Cargamos el voto emitido previamente por el usuario actual (si existe)
        userVoteJob = viewModelScope.launch {
            val userVoteResult = voteRepository.getUserVote(caprice.id, currentUserId)
            val loadedVote = userVoteResult.getOrNull()
            _state.update { current ->
                if (current.caprice?.id != caprice.id || current.currentUserId != currentUserId) return@update current
                val effectiveUserVote = current.userVote ?: loadedVote
                val updatedVotes = if (effectiveUserVote != null && current.votes.none { it.voterId == effectiveUserVote.voterId }) {
                    current.votes + effectiveUserVote
                } else current.votes
                current.copy(
                    votes = updatedVotes,
                    userVote = effectiveUserVote,
                    pollState = resolveCurrentPollState(current.caprice, current.currentUserId, effectiveUserVote, updatedVotes)
                )
            }
        }

        // 2. Escucha en tiempo real de todos los votos en la subcolección de Firestore
        votesJob = viewModelScope.launch {
            observeVotesUseCase.execute(caprice.id)
                .catch { e ->
                    if (e is CancellationException) throw e
                    hasLoadedVotesForCurrentPoll = false
                    _state.update { current ->
                        if (current.caprice?.id != caprice.id || current.currentUserId != currentUserId) return@update current
                        current.copy(errorMessage = e.message ?: "No se pudieron sincronizar los votos")
                    }
                }
                .collect { votesList ->
                    hasLoadedVotesForCurrentPoll = true
                    _state.update { current ->
                        if (current.caprice?.id != caprice.id || current.currentUserId != currentUserId) return@update current
                        val remoteVote = votesList.firstOrNull { it.voterId == current.currentUserId }
                        val localVote = current.userVote
                        val myVote = when {
                            remoteVote != null && localVote != null && localVote.timestamp > remoteVote.timestamp -> localVote
                            remoteVote != null -> remoteVote
                            else -> localVote
                        }
                        val effectiveVotes = when {
                            myVote == null -> votesList
                            votesList.none { it.voterId == myVote.voterId } -> votesList + myVote
                            remoteVote != myVote -> votesList.filterNot { it.voterId == myVote.voterId } + myVote
                            else -> votesList
                        }
                        current.copy(
                            votes = effectiveVotes,
                            userVote = myVote,
                            pollState = resolveCurrentPollState(current.caprice, current.currentUserId, myVote, effectiveVotes)
                        )
                    }
                }
        }

        // 3. Temporizador de cuenta atrás reactivo que actualiza cada 1s y dispara expiración
        tickerJob = viewModelScope.launch {
            val duration = PollTiming.POLL_DURATION_MILLIS
            while (true) {
                val elapsed = System.currentTimeMillis() - caprice.timestamp
                val remaining = (duration - elapsed).coerceAtLeast(0L)

                _state.update { current ->
                    if (current.caprice?.id != caprice.id) return@update current
                    current.copy(
                        pollState = resolveCurrentPollState(
                            caprice = current.caprice,
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

    fun castVote(option: Int, allowChange: Boolean = false, suppressAlreadyVotedError: Boolean = false) {
        val caprice = _state.value.caprice ?: return
        val currentUserId = _state.value.currentUserId
        if (currentUserId.isBlank() || _state.value.isCastingVote) return

        viewModelScope.launch {
            _state.update { it.copy(isCastingVote = true, errorMessage = null, pendingVoteRetry = null) }

            val result = castVoteUseCase.execute(caprice, currentUserId, option, allowChange = allowChange)

            result.fold(
                onSuccess = {
                    val newVote = Vote(capriceId = caprice.id, voterId = currentUserId, option = option)
                    _state.update { current ->
                        if (current.caprice?.id != caprice.id || current.currentUserId != currentUserId) return@update current
                        val updatedVotes = current.votes.filterNot { it.voterId == currentUserId } + newVote
                        current.copy(
                            isCastingVote = false,
                            userVote = newVote,
                            votes = updatedVotes,
                            pendingVoteRetry = null,
                            pollState = resolveCurrentPollState(caprice, currentUserId, newVote, updatedVotes)
                        )
                    }
                },
                onFailure = { error ->
                    val alreadyVoted = suppressAlreadyVotedError &&
                        error.message?.contains("Ya has votado", ignoreCase = true) == true
                    _state.update { current ->
                        if (current.caprice?.id != caprice.id || current.currentUserId != currentUserId) return@update current
                        if (alreadyVoted) {
                            current.copy(
                                isCastingVote = false,
                                errorMessage = null,
                                pendingVoteRetry = null
                            )
                        } else {
                            current.copy(
                                isCastingVote = false,
                                errorMessage = error.message ?: "No se pudo emitir el voto",
                                pendingVoteRetry = PendingVoteAction(
                                    capriceId = caprice.id,
                                    option = option,
                                    allowChange = allowChange
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    fun retryPendingVoteIfAny() {
        val pending = _state.value.pendingVoteRetry ?: return
        val currentCaprice = _state.value.caprice ?: return
        if (currentCaprice.id != pending.capriceId) return
        if (PollTiming.isPollExpired(currentCaprice.timestamp)) {
            _state.update { it.copy(pendingVoteRetry = null) }
            return
        }
        castVote(option = pending.option, allowChange = pending.allowChange, suppressAlreadyVotedError = true)
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
                    _state.update { current ->
                        if (current.caprice?.id != caprice.id) return@update current
                        current.copy(
                            caprice = updatedCaprice,
                            isSavingFinalDecision = false,
                            errorMessage = null
                        )
                    }
                    if (_state.value.caprice?.id == caprice.id) {
                        onUpdated?.invoke(updatedCaprice)
                    }
                },
                onFailure = { error ->
                    _state.update { current ->
                        if (current.caprice?.id != caprice.id) return@update current
                        current.copy(
                            isSavingFinalDecision = false,
                            errorMessage = error.message ?: "No se pudo guardar tu decisión final"
                        )
                    }
                }
            )
        }
    }

    fun refreshFinalDecision() {
        val repo = capriceDetailsRepository ?: return
        val targetCaprice = _state.value.caprice ?: return
        val targetUserId = _state.value.currentUserId
        if (targetCaprice.id.isBlank() || targetUserId.isBlank()) return

        viewModelScope.launch {
            val latest = try {
                repo.getCapriceById(targetCaprice.id).getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch

            if (latest.id != targetCaprice.id || latest.userId != targetCaprice.userId) return@launch
            val validDecision = latest.finalDecision?.takeIf { it == 1 || it == 2 }
            val validDecidedAt = if (validDecision != null) latest.finalDecisionAt else null

            _state.update { current ->
                if (current.caprice?.id != targetCaprice.id || current.currentUserId != targetUserId) {
                    return@update current
                }
                val currentCaprice = current.caprice
                if (currentCaprice.finalDecision == validDecision &&
                    currentCaprice.finalDecisionAt == validDecidedAt
                ) {
                    return@update current
                }
                current.copy(
                    caprice = currentCaprice.copy(
                        finalDecision = validDecision,
                        finalDecisionAt = validDecidedAt
                    )
                )
            }
        }
    }

    private fun computeInitialPollState(caprice: Caprice, currentUserId: String): PollState {
        val remaining = PollTiming.remainingMillis(caprice.timestamp)
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
        val remaining = remainingOverride ?: PollTiming.remainingMillis(caprice.timestamp)

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
        votesJob?.cancel()
        userVoteJob?.cancel()
        tickerJob?.cancel()
        placeNameJob?.cancel()
    }
}
