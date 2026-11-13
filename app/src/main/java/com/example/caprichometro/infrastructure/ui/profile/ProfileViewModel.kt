package com.example.caprichometro.infrastructure.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetBlockedUsersUseCase
import com.example.caprichometro.application.usecase.friendship.UnblockUserUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.profile.UpdateBioUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

private enum class CachedVerdict { ACCEPTED, REJECTED, TIE_OR_NONE }

data class ProfileState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val email: String = "",
    val caprices: List<Caprice> = emptyList(),
    val friends: List<UserProfile> = emptyList(),
    val blockedUsers: List<UserProfile> = emptyList(),
    val verdicts: Map<String, Boolean?> = emptyMap(), // capriceId -> true (opcion 1 ganadora), false (opcion 2 ganadora), null (empate/sin votos/activa)
    val isOwnProfile: Boolean = true,
    val isSavingBio: Boolean = false,
    val bioErrorMessage: String? = null,
    val errorMessage: String? = null,
    val isSignedOut: Boolean = false
)

private data class ProfileLoadResult(
    val profile: UserProfile?,
    val caprices: List<Caprice>,
    val blockedUsers: List<UserProfile>,
    val friends: List<UserProfile>,
    val verdicts: Map<String, Boolean?>
)

class ProfileViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    private val unblockUserUseCase: UnblockUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val getCapricesUseCase: GetCapricesUseCase,
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val voteRepository: VoteRepository? = null,
    private val updateBioUseCase: UpdateBioUseCase = UpdateBioUseCase(userProfileRepository),
    private val removeFriendUseCase: RemoveFriendUseCase? = null,
    private val blockUserUseCase: BlockUserUseCase? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    // Cache en memoria thread-safe para veredictos de dudas cerradas (inmutables)
    private val verdictCache = ConcurrentHashMap<String, CachedVerdict>()
    private var sessionGeneration = 0L
    private var profileLoadJob: Job? = null
    private var verdictsJob: Job? = null

    fun reset() {
        sessionGeneration++
        profileLoadJob?.cancel()
        profileLoadJob = null
        verdictsJob?.cancel()
        verdictsJob = null
        _state.value = ProfileState()
        verdictCache.clear()
    }

    private fun computeVerdictStatus(caprice: Caprice, votes: List<com.example.caprichometro.domain.model.Vote>): CachedVerdict {
        val decision = caprice.finalDecision ?: return CachedVerdict.TIE_OR_NONE
        val opt1Votes = votes.count { it.option == 1 }
        val opt2Votes = votes.count { it.option == 2 }
        val winningOption = when {
            opt1Votes > opt2Votes -> 1
            opt2Votes > opt1Votes -> 2
            else -> return CachedVerdict.TIE_OR_NONE
        }
        return if (decision == winningOption) CachedVerdict.ACCEPTED else CachedVerdict.REJECTED
    }

    private fun CachedVerdict.toAcceptedOrNull(): Boolean? = when (this) {
        CachedVerdict.ACCEPTED -> true
        CachedVerdict.REJECTED -> false
        CachedVerdict.TIE_OR_NONE -> null
    }

    fun loadProfile(targetUserId: String, currentUserId: String = targetUserId) {
        if (targetUserId.isBlank()) return

        profileLoadJob?.cancel()
        verdictsJob?.cancel()

        val isOwn = targetUserId == currentUserId
        val email = if (isOwn) getCurrentUserUseCase.execute()?.email.orEmpty() else ""

        val isDifferentUser = _state.value.profile?.uid != targetUserId
        if (isDifferentUser) {
            sessionGeneration++
        }
        val requestSession = sessionGeneration

        _state.update {
            it.copy(
                isLoading = true,
                profile = if (isDifferentUser) null else it.profile,
                caprices = if (isDifferentUser) emptyList() else it.caprices,
                friends = if (isDifferentUser || !isOwn) emptyList() else it.friends,
                blockedUsers = if (isDifferentUser || !isOwn) emptyList() else it.blockedUsers,
                verdicts = if (isDifferentUser) emptyMap() else it.verdicts,
                email = email,
                isOwnProfile = isOwn,
                isSignedOut = false,
                errorMessage = null
            )
        }

        profileLoadJob = viewModelScope.launch {
            // Fase 1: Cargar datos principales en paralelo (~200ms) sin bloquear por subcolecciones de votos
            val result = coroutineScope {
                val profileDeferred = async { userProfileRepository.getUserById(targetUserId).getOrNull() }
                val capricesDeferred = async { getCapricesUseCase.execute(targetUserId, limit = 50).getOrDefault(emptyList()) }
                val blockedDeferred = async {
                    if (isOwn) {
                        getBlockedUsersUseCase.execute(targetUserId).getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
                val friendsDeferred = async {
                    if (isOwn) {
                        try {
                            getAcceptedFriendsUseCase.execute(targetUserId)
                                .catch { emit(emptyList()) }
                                .first()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }

                val rawCaprices = capricesDeferred.await()
                val loadedCaprices = if (isOwn) {
                    rawCaprices
                } else {
                    rawCaprices.filter { it.isVisibleTo(currentUserId) }
                }
                val cachedVerdicts = loadedCaprices.mapNotNull { caprice ->
                    verdictCache[caprice.id]?.let { caprice.id to it.toAcceptedOrNull() }
                }.toMap()

                ProfileLoadResult(
                    profile = profileDeferred.await(),
                    caprices = loadedCaprices,
                    blockedUsers = blockedDeferred.await(),
                    friends = friendsDeferred.await(),
                    verdicts = cachedVerdicts
                )
            }

            if (requestSession != sessionGeneration) return@launch

            _state.update {
                it.copy(
                    isLoading = false,
                    profile = result.profile,
                    caprices = result.caprices,
                    friends = result.friends,
                    blockedUsers = result.blockedUsers,
                    verdicts = result.verdicts
                )
            }

            // Fase 2: Cargar en segundo plano solo los veredictos cerrados no cacheados (pool de 4)
            val repo = voteRepository ?: return@launch
            val pendingCaprices = result.caprices.filter { caprice ->
                val isExpired = (System.currentTimeMillis() - caprice.timestamp) >= PollNotificationScheduler.POLL_DURATION_MILLIS
                isExpired && !verdictCache.containsKey(caprice.id)
            }
            if (pendingCaprices.isEmpty()) return@launch

            verdictsJob = launch {
                val semaphore = Semaphore(4)
                val computedVerdicts = coroutineScope {
                    pendingCaprices.map { caprice ->
                        async {
                            semaphore.withPermit {
                                val votes = repo.getVotes(caprice.id).getOrDefault(emptyList())
                                val cachedVerdict = computeVerdictStatus(caprice, votes)
                                if (caprice.finalDecision != null) {
                                    verdictCache[caprice.id] = cachedVerdict
                                }
                                caprice.id to cachedVerdict.toAcceptedOrNull()
                            }
                        }
                    }.awaitAll().toMap()
                }

                if (requestSession != sessionGeneration) return@launch

                _state.update { current ->
                    current.copy(verdicts = current.verdicts + computedVerdicts)
                }
            }
        }
    }

    fun clearBioError() {
        _state.update { it.copy(bioErrorMessage = null) }
    }

    fun markFinalDecision(capriceId: String, decision: Int, decidedAt: Long) {
        var updatedCaprice: Caprice? = null
        _state.update { current ->
            current.copy(
                caprices = current.caprices.map { caprice ->
                    if (caprice.id == capriceId) {
                        caprice.copy(finalDecision = decision, finalDecisionAt = decidedAt).also {
                            updatedCaprice = it
                        }
                    } else caprice
                }
            )
        }
        val targetCaprice = updatedCaprice ?: return
        val repo = voteRepository ?: return
        viewModelScope.launch {
            val votes = repo.getVotes(capriceId).getOrDefault(emptyList())
            val cachedVerdict = computeVerdictStatus(targetCaprice, votes)
            verdictCache[capriceId] = cachedVerdict
            _state.update { current ->
                current.copy(verdicts = current.verdicts + (capriceId to cachedVerdict.toAcceptedOrNull()))
            }
        }
    }

    fun updateBio(userId: String, newBio: String, onSuccess: () -> Unit = {}) {
        if (userId.isBlank() || _state.value.isSavingBio) return
        val requestSession = sessionGeneration
        _state.update { it.copy(isSavingBio = true, bioErrorMessage = null) }

        viewModelScope.launch {
            val result = updateBioUseCase.execute(userId, newBio)
            // Descartar respuestas tardías si se cerró sesión o cambió la cuenta activa
            val currentLoadedUid = _state.value.profile?.uid
            if (requestSession != sessionGeneration || (currentLoadedUid != null && currentLoadedUid != userId)) {
                return@launch
            }
            result.fold(
                onSuccess = {
                    val trimmed = newBio.trim()
                    _state.update { current ->
                        current.copy(
                            isSavingBio = false,
                            bioErrorMessage = null,
                            profile = current.profile?.copy(bio = trimmed)
                        )
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSavingBio = false,
                            bioErrorMessage = error.message ?: "No se pudo actualizar la biografía."
                        )
                    }
                }
            )
        }
    }

    fun unblockUser(currentUserId: String, blockedUserId: String) {
        if (currentUserId.isBlank() || blockedUserId.isBlank()) return

        viewModelScope.launch {
            val result = unblockUserUseCase.execute(currentUserId, blockedUserId)
            result.fold(
                onSuccess = {
                    _state.update { current ->
                        current.copy(
                            blockedUsers = current.blockedUsers.filterNot { it.uid == blockedUserId }
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "No se pudo desbloquear al usuario") }
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase.execute()
            reset()
            _state.update { it.copy(isSignedOut = true) }
        }
    }

    fun deleteAccount(currentUserId: String, username: String) {
        if (currentUserId.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = deleteAccountUseCase.execute(currentUserId, username)
            result.fold(
                onSuccess = {
                    reset()
                    _state.update { it.copy(isSignedOut = true) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Error al eliminar la cuenta"
                        )
                    }
                }
            )
        }
    }

    fun removeFriend(currentUserId: String, friendUserId: String, onSuccess: () -> Unit = {}) {
        if (currentUserId.isBlank() || friendUserId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = removeFriendUseCase?.execute(currentUserId, friendUserId) ?: Result.success(Unit)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "Error al eliminar amigo") }
                }
            )
        }
    }

    fun blockUser(currentUserId: String, targetUserId: String, onSuccess: () -> Unit = {}) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = blockUserUseCase?.execute(currentUserId, targetUserId) ?: Result.success(Unit)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "Error al bloquear usuario") }
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
