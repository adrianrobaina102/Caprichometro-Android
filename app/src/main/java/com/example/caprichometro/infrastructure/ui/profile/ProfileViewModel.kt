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

    fun reset() {
        _state.value = ProfileState()
        verdictCache.clear()
    }

    fun loadProfile(targetUserId: String, currentUserId: String = targetUserId) {
        if (targetUserId.isBlank()) return

        val isOwn = targetUserId == currentUserId
        val email = if (isOwn) getCurrentUserUseCase.execute()?.email.orEmpty() else ""

        _state.update {
            it.copy(
                isLoading = true,
                email = email,
                isOwnProfile = isOwn,
                isSignedOut = false,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            // Concurrencia y paralelismo: lanzamos todas las consultas independientes al mismo tiempo
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
                    try {
                        getAcceptedFriendsUseCase.execute(targetUserId)
                            .catch { emit(emptyList()) }
                            .first()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                val loadedCaprices = capricesDeferred.await()

                // Paralelismo masivo para veredictos con cache en memoria:
                val verdictsDeferred = async {
                    if (voteRepository != null && loadedCaprices.isNotEmpty()) {
                        coroutineScope {
                            loadedCaprices.map { caprice ->
                                async {
                                    val isExpired = (System.currentTimeMillis() - caprice.timestamp) >= PollNotificationScheduler.POLL_DURATION_MILLIS
                                    if (!isExpired) {
                                        caprice.id to null // Duda aun activa en directo
                                    } else {
                                        val cached = verdictCache[caprice.id]
                                        if (cached != null) {
                                            val accepted = when (cached) {
                                                CachedVerdict.ACCEPTED -> true
                                                CachedVerdict.REJECTED -> false
                                                CachedVerdict.TIE_OR_NONE -> null
                                            }
                                            caprice.id to accepted
                                        } else {
                                            // 2. Consulta directa 'one-shot' (getVotes) sin sobrecarga de snapshot listeners
                                            val votes = voteRepository.getVotes(caprice.id).getOrDefault(emptyList())
                                            val opt1Votes = votes.count { it.option == 1 }
                                            val opt2Votes = votes.count { it.option == 2 }
                                            val cachedVerdict = when {
                                                opt1Votes > opt2Votes -> CachedVerdict.ACCEPTED
                                                opt2Votes > opt1Votes -> CachedVerdict.REJECTED
                                                else -> CachedVerdict.TIE_OR_NONE
                                            }
                                            // Guardamos en cache thread-safe (sin riesgo de NPE con nulos)
                                            verdictCache[caprice.id] = cachedVerdict
                                            val accepted = when (cachedVerdict) {
                                                CachedVerdict.ACCEPTED -> true
                                                CachedVerdict.REJECTED -> false
                                                CachedVerdict.TIE_OR_NONE -> null
                                            }
                                            caprice.id to accepted
                                        }
                                    }
                                }
                            }.awaitAll().toMap()
                        }
                    } else emptyMap()
                }

                ProfileLoadResult(
                    profile = profileDeferred.await(),
                    caprices = loadedCaprices,
                    blockedUsers = blockedDeferred.await(),
                    friends = friendsDeferred.await(),
                    verdicts = verdictsDeferred.await()
                )
            }

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
        }
    }

    fun updateBio(userId: String, newBio: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            val result = updateBioUseCase.execute(userId, newBio)
            result.fold(
                onSuccess = {
                    val trimmed = newBio.trim()
                    _state.update { current ->
                        current.copy(
                            profile = current.profile?.copy(bio = trimmed)
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "No se pudo actualizar la biografia") }
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
