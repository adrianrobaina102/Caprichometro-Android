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
import com.example.caprichometro.domain.model.InviteDeepLink
import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.UsernameRules
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

data class FriendshipSearchState(
    val searchQuery: String = "",
    val usernameSuggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSendingRequest: Boolean = false,
    val directRequestSendingUid: String? = null,
    val directRequestSentUids: Set<String> = emptySet(),
    val directRequestError: String? = null,
    val respondingRequestIds: Set<String> = emptySet(),
    val userFound: UserProfile? = null,
    val isFriendRequestBlocked: Boolean = false,
    val errorMessage: String? = null,
    val requestSentSuccess: Boolean = false,
    val incomingRequests: List<IncomingFriendRequest> = emptyList(),
    val friends: List<UserProfile> = emptyList(),
    val hasLoadedFriends: Boolean = false,
    val currentUsername: String? = null,
    val currentUserProfile: UserProfile? = null
)

class FriendshipViewModel(
    private val searchUserByUsernameUseCase: SearchUserByUsernameUseCase,
    private val sendFriendRequestUseCase: SendFriendRequestUseCase,
    private val getIncomingFriendRequestsUseCase: GetIncomingFriendRequestsUseCase,
    private val respondToFriendRequestUseCase: RespondToFriendRequestUseCase,
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    private val userProfileRepository: UserProfileRepository? = null
) : ViewModel() {

    private val _state = MutableStateFlow(FriendshipSearchState())
    val state: StateFlow<FriendshipSearchState> = _state.asStateFlow()

    private var listenRequestsJob: Job? = null
    private var refreshProfilesJob: Job? = null
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null
    private var sendRequestJob: Job? = null
    private var listeningUserId: String? = null

    fun listenToIncomingRequests(currentUserId: String) {
        if (currentUserId.isBlank()) return
        if (listeningUserId == currentUserId && listenRequestsJob?.isActive == true) return

        listenRequestsJob?.cancel()
        listeningUserId = currentUserId
        listenRequestsJob = viewModelScope.launch {
            launch {
                userProfileRepository?.getUserById(currentUserId)?.onSuccess { profile ->
                    if (profile != null) {
                        _state.update { it.copy(currentUsername = profile.username, currentUserProfile = profile) }
                    }
                }
            }

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
                        _state.update { it.copy(errorMessage = "Error al recibir amigos: ${e.message}") }
                    }
                    .collect { friendsList ->
                        _state.update { it.copy(friends = friendsList, hasLoadedFriends = true) }
                    }
            }
        }
    }

    fun stopListening() {
        listenRequestsJob?.cancel()
        listenRequestsJob = null
        refreshProfilesJob?.cancel()
        refreshProfilesJob = null
        searchJob?.cancel()
        searchJob = null
        suggestionsJob?.cancel()
        suggestionsJob = null
        sendRequestJob?.cancel()
        sendRequestJob = null
        listeningUserId = null
        _state.update {
            it.copy(
                searchQuery = "",
                usernameSuggestions = emptyList(),
                isLoading = false,
                isSendingRequest = false,
                directRequestSendingUid = null,
                directRequestSentUids = emptySet(),
                directRequestError = null,
                respondingRequestIds = emptySet(),
                errorMessage = null,
                userFound = null,
                isFriendRequestBlocked = false,
                requestSentSuccess = false,
                incomingRequests = emptyList(),
                friends = emptyList(),
                hasLoadedFriends = false,
                currentUsername = null,
                currentUserProfile = null
            )
        }
    }

    fun refreshProfiles(currentUserId: String = "") {
        val repo = userProfileRepository ?: return
        val cacheControl = repo as? ProfileCacheControl
        val currentFriends = _state.value.friends
        val currentRequests = _state.value.incomingRequests
        if (currentUserId.isBlank() && currentFriends.isEmpty() && currentRequests.isEmpty()) return
        val targetUserId = currentUserId.ifBlank { listeningUserId.orEmpty() }

        refreshProfilesJob?.cancel()
        refreshProfilesJob = viewModelScope.launch {
            if (targetUserId.isNotBlank() && listeningUserId != targetUserId) return@launch
            val semaphore = Semaphore(4)
            val refreshedOwn = if (currentUserId.isNotBlank()) {
                cacheControl?.invalidateUser(currentUserId)
                repo.getUserById(currentUserId).getOrNull()
            } else {
                null
            }
            val updatedFriends = coroutineScope {
                currentFriends.map { friend ->
                    async {
                        semaphore.withPermit {
                            cacheControl?.invalidateUser(friend.uid)
                            repo.getUserById(friend.uid).getOrNull() ?: friend
                        }
                    }
                }.awaitAll()
            }
            val updatedRequests = coroutineScope {
                currentRequests.map { req ->
                    async {
                        semaphore.withPermit {
                            cacheControl?.invalidateUser(req.sender.uid)
                            val refreshedSender = repo.getUserById(req.sender.uid).getOrNull()
                            if (refreshedSender != null) req.copy(sender = refreshedSender) else req
                        }
                    }
                }.awaitAll()
            }
            if (targetUserId.isNotBlank() && listeningUserId != targetUserId) return@launch
            _state.update {
                it.copy(
                    currentUsername = refreshedOwn?.username ?: it.currentUsername,
                    currentUserProfile = refreshedOwn ?: it.currentUserProfile,
                    friends = updatedFriends,
                    incomingRequests = updatedRequests
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        searchJob = null
        suggestionsJob?.cancel()
        suggestionsJob = null
        val extracted = InviteDeepLink.extractUsername(query)
        val effectiveQuery = extracted ?: query
        _state.update {
            it.copy(
                searchQuery = effectiveQuery,
                usernameSuggestions = emptyList(),
                isLoading = false,
                errorMessage = null,
                userFound = null,
                isFriendRequestBlocked = false,
                requestSentSuccess = false
            )
        }
        // Si el usuario acaba de pegar un enlace o mensaje de invitación, lanzamos la búsqueda al instante
        if (extracted != null) {
            searchUser()
            return
        }
        val normalizedPrefix = UsernameRules.normalize(query)
        if (normalizedPrefix.length >= 3 && !query.contains("://")) {
            suggestionsJob = viewModelScope.launch {
                delay(300)
                val suggestions = searchUserByUsernameUseCase.searchSuggestions(normalizedPrefix)
                    .getOrDefault(emptyList())
                    .filterNot { it.equals(_state.value.currentUsername, ignoreCase = true) }
                if (_state.value.searchQuery == query && _state.value.userFound == null) {
                    _state.update { it.copy(usernameSuggestions = suggestions) }
                }
            }
        }
    }

    fun selectSuggestedUsername(username: String) {
        suggestionsJob?.cancel()
        suggestionsJob = null
        _state.update {
            it.copy(
                searchQuery = username,
                usernameSuggestions = emptyList()
            )
        }
        searchUser()
    }

    fun openInviteForUsername(rawInviteOrUsername: String) {
        val clean = InviteDeepLink.extractUsername(rawInviteOrUsername)
            ?: UsernameRules.normalize(rawInviteOrUsername)
        if (clean.isBlank()) return
        _state.update {
            it.copy(
                searchQuery = clean,
                errorMessage = null,
                userFound = null,
                isFriendRequestBlocked = false,
                requestSentSuccess = false
            )
        }
        searchUser()
    }

    fun clearRequestSentSuccess() {
        _state.update { it.copy(requestSentSuccess = false) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun searchUser() {
        val rawQuery = _state.value.searchQuery
        if (rawQuery.isBlank()) return
        val query = InviteDeepLink.extractUsername(rawQuery) ?: rawQuery

        _state.update {
            it.copy(
                searchQuery = query,
                usernameSuggestions = emptyList(),
                isLoading = true,
                errorMessage = null,
                userFound = null,
                isFriendRequestBlocked = false,
                requestSentSuccess = false
            )
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
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
        if (_state.value.isSendingRequest || _state.value.isFriendRequestBlocked || targetUser.uid == currentUserId) return

        _state.update { it.copy(isSendingRequest = true, errorMessage = null) }

        sendRequestJob?.cancel()
        sendRequestJob = viewModelScope.launch {
            val result = sendFriendRequestUseCase.execute(
                currentUserId = currentUserId,
                targetUserId = targetUser.uid
            )
            if (listeningUserId != currentUserId) return@launch

            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isSendingRequest = false,
                            requestSentSuccess = true,
                            userFound = null,
                            searchQuery = ""
                        )
                    }
                },
                onFailure = { error ->
                    val isBlockedError = error.message?.contains("bloqueado", ignoreCase = true) == true
                    _state.update {
                        it.copy(
                            isSendingRequest = false,
                            isFriendRequestBlocked = isBlockedError,
                            errorMessage = error.message ?: "Error al enviar la solicitud."
                        )
                    }
                }
            )
        }
    }

    fun sendFriendRequestToUser(currentUserId: String, targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || targetUserId == currentUserId) return
        if (_state.value.directRequestSendingUid == targetUserId || targetUserId in _state.value.directRequestSentUids) return

        _state.update { it.copy(directRequestSendingUid = targetUserId, directRequestError = null) }
        viewModelScope.launch {
            val result = sendFriendRequestUseCase.execute(
                currentUserId = currentUserId,
                targetUserId = targetUserId
            )
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            directRequestSendingUid = null,
                            directRequestSentUids = state.directRequestSentUids + targetUserId,
                            directRequestError = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            directRequestSendingUid = null,
                            directRequestError = error.message ?: "No se pudo enviar la solicitud."
                        )
                    }
                }
            )
        }
    }

    fun clearDirectRequestError() {
        _state.update { it.copy(directRequestError = null) }
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
            if (listeningUserId != currentUserId) return@launch
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            friends = state.friends.filterNot { it.uid == friendId },
                            isFriendRequestBlocked = if (state.userFound?.uid == friendId) false else state.isFriendRequestBlocked
                        )
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
            if (listeningUserId != currentUserId) return@launch
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        val matchesSearchedUser = state.userFound?.uid == blockedUserId
                        state.copy(
                            friends = state.friends.filterNot { it.uid == blockedUserId },
                            incomingRequests = state.incomingRequests.filterNot { it.sender.uid == blockedUserId },
                            isFriendRequestBlocked = if (matchesSearchedUser) true else state.isFriendRequestBlocked
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
        if (friendshipId.isBlank() || friendshipId in _state.value.respondingRequestIds) return
        _state.update { it.copy(respondingRequestIds = it.respondingRequestIds + friendshipId) }
        viewModelScope.launch {
            try {
                val result = respondToFriendRequestUseCase.execute(friendshipId, accept)
                result.fold(
                    onSuccess = {
                        // SnapshotListener actualiza la lista automáticamente
                    },
                    onFailure = { error ->
                        _state.update { it.copy(errorMessage = error.message ?: "Error al responder a la solicitud") }
                    }
                )
            } finally {
                _state.update { it.copy(respondingRequestIds = it.respondingRequestIds - friendshipId) }
            }
        }
    }
}
