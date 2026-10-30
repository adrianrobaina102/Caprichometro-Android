package com.example.caprichometro.infrastructure.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.RegisterUseCase
import com.example.caprichometro.application.usecase.auth.SignInUseCase
import com.example.caprichometro.application.usecase.auth.SignInWithGoogleUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AuthState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val birthday: LocalDate? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isAuthSuccessful: Boolean = false
)

class AuthViewModel(
    private val signInUseCase: SignInUseCase,
    private val registerUseCase: RegisterUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase
) : ViewModel() {

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private val _state = MutableStateFlow(
        AuthState(isAuthSuccessful = getCurrentUserUseCase.execute() != null)
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun updateEmail(email: String) { _state.update { it.copy(email = email) } }
    fun updatePassword(password: String) { _state.update { it.copy(password = password) } }
    fun updateName(name: String) { _state.update { it.copy(name = name) } }
    fun clearError() { _state.update { it.copy(errorMessage = null) } }
    fun updateBirthday(date: LocalDate) { _state.update { it.copy(birthday = date) } }

    private fun isValidEmail(email: String): Boolean {
        val pattern = android.util.Patterns.EMAIL_ADDRESS
        return pattern?.matcher(email)?.matches() ?: emailRegex.matches(email.trim())
    }

    fun signIn() {
        val currentState = _state.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _state.update { it.copy(errorMessage = "El email y la contraseña son obligatorios") }
            return
        }

        if (!isValidEmail(currentState.email)) {
            _state.update { it.copy(errorMessage = "Por favor, introduce un correo electrónico válido") }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = signInUseCase.execute(currentState.email, currentState.password)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isAuthSuccessful = true) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Error al iniciar sesión"
                        )
                    }
                }
            )
        }
    }

    fun register() {
        val currentState = _state.value

        if (currentState.name.isBlank() || currentState.email.isBlank() || currentState.password.isBlank() || currentState.birthday == null) {
            _state.update { it.copy(errorMessage = "Todos los campos, incluida la fecha, son obligatorios") }
            return
        }

        if (currentState.birthday.isAfter(LocalDate.now())) {
            _state.update { it.copy(errorMessage = "La fecha de nacimiento no puede ser en el futuro") }
            return
        }

        if (!isValidEmail(currentState.email)) {
            _state.update { it.copy(errorMessage = "Por favor, introduce un correo electrónico válido") }
            return
        }

        if (currentState.password.length < 6) {
            _state.update { it.copy(errorMessage = "La contraseña debe tener al menos 6 caracteres") }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = registerUseCase.execute(
                currentState.email,
                currentState.password,
                currentState.name
            )
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isAuthSuccessful = true) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Error al registrar la cuenta"
                        )
                    }
                }
            )
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }

            val result = signInWithGoogleUseCase.execute(context)

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isSubmitting = false, isAuthSuccessful = true) }
                },
                onFailure = { exception ->
                    _state.update {
                        it.copy(isSubmitting = false, errorMessage = exception.message ?: "Error al iniciar sesión con Google")
                    }
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase.execute()
            _state.update { AuthState(isAuthSuccessful = false) }
        }
    }

    fun deleteAccount(userId: String, username: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = deleteAccountUseCase.execute(userId, username)
            result.fold(
                onSuccess = {
                    _state.update { AuthState(isAuthSuccessful = false) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Error al borrar la cuenta"
                        )
                    }
                }
            )
        }
    }
}
