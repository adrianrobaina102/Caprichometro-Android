package com.example.caprichometro.infrastructure.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.GetBlockedUsersUseCase
import com.example.caprichometro.application.usecase.friendship.UnblockUserUseCase
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val email: String = "",
    val blockedUsers: List<UserProfile> = emptyList(),
    val errorMessage: String? = null,
    val isSignedOut: Boolean = false
)

class ProfileViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    private val unblockUserUseCase: UnblockUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun loadProfile(userId: String) {
        if (userId.isBlank()) return
        val currentUser = getCurrentUserUseCase.execute()
        val email = currentUser?.email.orEmpty()

        _state.update { it.copy(isLoading = true, email = email, errorMessage = null) }

        viewModelScope.launch {
            val profileResult = userProfileRepository.getUserById(userId)
            val blockedResult = getBlockedUsersUseCase.execute(userId)

            val profile = profileResult.getOrNull()
            val blocked = blockedResult.getOrDefault(emptyList())

            _state.update {
                it.copy(
                    isLoading = false,
                    profile = profile,
                    blockedUsers = blocked
                )
            }
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
            _state.update { it.copy(isSignedOut = true) }
        }
    }

    fun deleteAccount(userId: String, username: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = deleteAccountUseCase.execute(userId, username)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, isSignedOut = true) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, errorMessage = error.message ?: "Error al eliminar la cuenta") }
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
