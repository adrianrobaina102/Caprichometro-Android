package com.example.caprichometro.infrastructure.ui.friendship

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetIncomingFriendRequestsUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.RespondToFriendRequestUseCase
import com.example.caprichometro.application.usecase.friendship.SearchUserByUsernameUseCase
import com.example.caprichometro.application.usecase.friendship.SendFriendRequestUseCase
import com.example.caprichometro.domain.model.IncomingFriendRequest
import com.example.caprichometro.domain.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendshipSearchState(
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val userFound: UserProfile? = null,
    val errorMessage: String? = null,
    val requestSentSuccess: Boolean = false,
    val incomingRequests: List<IncomingFriendRequest> = emptyList(),
    val friends: List<UserProfile> = emptyList()
)

class FriendshipViewModel(
    private val searchUserByUsernameUseCase: SearchUserByUsernameUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val getIncomingFriendRequestsUseCase: GetIncomingFriendRequestsUseCase,
    private val respondToFriendRequestUseCase: RespondToFriendRequestUseCase,
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val blockUserUseCase: BlockUserUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(FriendshipSearchState())
    val state: StateFlow<FriendshipSearchState> = _state.asStateFlow()

    private var listenRequestsJob: Job? = null

    fun listenToIncomingRequests(currentUserId: String) {
        if (currentUserId.isBlank()) return

        listenRequestsJob?.cancel()
        listenRequestsJob = viewModelScope.launch {
            launch {
                getIncomingFriendRequestsUseCase.execute(currentUserId)
                    .catch { e ->
                        android.util.Log.e("FriendshipViewModel", "Error al escuchar solicitudes: ${e.message}", e)
                        _state.update { it.copy(errorMessage = "Error al recibir solicitudes: ${e.message}") }
                    }
                    .collect { requests ->
                        _state.update { it.copy(incomingRequests = requests) }
                    }
            }

            launch {
                getAcceptedFriendsUseCase.execute(currentUserId)
                    .catch { e ->
                        android.util.Log.e("FriendshipViewModel", "Error al escuchar amigos: ${e.message}", e)
                    }
                    .collect { friendsList ->
                        _state.update { it.copy(friends = friendsList) }
                    }
            }
        }
    }

    fun stopListening() {
        listenRequestsJob?.cancel()
        listenRequestsJob = null
        _state.update { it.copy(incomingRequests = emptyList(), friends = emptyList()) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query, errorMessage = null, userFound = null, requestSentSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun searchUser() {
        val query = _state.value.searchQuery
        if (query.isBlank()) return

        _state.update { it.copy(isLoading = true, errorMessage = null, userFound = null) }

        viewModelScope.launch {
            val result = searchUserByUsernameUseCase.execute(query)

            result.fold(
                onSuccess = { profile ->
                    if (profile != null) {
                        _state.update { it.copy(isLoading = false, userFound = profile) }
                    } else {
                        _state.update { it.copy(isLoading = false, errorMessage = "No se ha encontrado ningún usuario con ese nombre.") }
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "Error al buscar usuario.") }
                }
            )
        }
    }

    fun sendRequest(currentUserId: String) {
        val targetUser = _state.value.userFound ?: return

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = sendFriendRequestUseCase.execute(
                currentUserId = currentUserId,
                targetUserId = targetUser.uid
            )

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, requestSentSuccess = true, userFound = null, searchQuery = "") }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "Error al enviar la solicitud.") }
                }
            )
        }
    }

    fun acceptRequest(friendshipId: String) {
        respondToRequest(friendshipId, accept = true)
    }

    fun declineRequest(friendshipId: String) {
        respondToRequest(friendshipId, accept = false)
    }

    fun removeFriend(currentUserId: String, friendId: String) {
        viewModelScope.launch {
            _state.update { it.copy(errorMessage = null) }
            val result = removeFriendUseCase.execute(currentUserId, friendId)
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(friends = state.friends.filterNot { it.uid == friendId })
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "No se pudo eliminar la amistad.") }
                }
            )
        }
    }

    fun blockUser(currentUserId: String, blockedUserId: String) {
        viewModelScope.launch {
            _state.update { it.copy(errorMessage = null) }
            val result = blockUserUseCase.execute(currentUserId, blockedUserId)
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            friends = state.friends.filterNot { it.uid == blockedUserId },
                            incomingRequests = state.incomingRequests.filterNot { it.sender.uid == blockedUserId },
                            userFound = if (state.userFound?.uid == blockedUserId) null else state.userFound
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "No se pudo bloquear al usuario.") }
                }
            )
        }
    }

    private fun respondToRequest(friendshipId: String, accept: Boolean) {
        viewModelScope.launch {
            val result = respondToFriendRequestUseCase.execute(friendshipId, accept)
            result.fold(
                onSuccess = {
                    // SnapshotListener actualiza la lista automáticamente
                },
                onFailure = { error ->
                    _state.update { it.copy(errorMessage = error.message ?: "Error al responder a la solicitud") }
                }
            )
        }
    }
}
