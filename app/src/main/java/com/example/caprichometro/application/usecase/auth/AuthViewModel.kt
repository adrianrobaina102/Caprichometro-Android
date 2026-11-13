package com.example.caprichometro.application.usecase.auth

import com.example.caprichometro.domain.model.AuthRepository
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.UsernameRules

class InvalidUsernameException(message: String = "El nombre de usuario no es válido (3-20 letras, números o guiones bajos).") : Exception(message)
class UsernameTakenException(message: String = "Este usuario ya está en uso.") : Exception(message)

class SignInUseCase(private val authRepository: AuthRepository) {
    suspend fun execute(email: String, password: String): Result<User> {
        return authRepository.signIn(email, password)
    }
}

class RegisterUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Registra al usuario en Firebase Auth antes de consultar `usernames/{username}` para cumplir `isSignedIn()`,
     * revirtiendo la cuenta si el @username está ocupado o falla la creación del perfil.
     */
    suspend fun execute(email: String, password: String, username: String): Result<User> {
        var createdAuthAccount = false
        return try {
            val normalizedUsername = UsernameRules.normalize(username)

            if (!UsernameRules.isValid(normalizedUsername)) {
                return Result.failure(InvalidUsernameException())
            }

            val authResult = authRepository.register(email, password, normalizedUsername)
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull() ?: Exception("Error en Firebase Auth"))
            }
            val registeredUser = authResult.getOrNull()
                ?: return Result.failure(Exception("Error al obtener el usuario registrado"))
            createdAuthAccount = true

            val isAvailableResult = userProfileRepository.isUsernameAvailable(normalizedUsername)
            if (isAvailableResult.isFailure) {
                rollbackUnfinishedRegistration()
                return Result.failure(
                    isAvailableResult.exceptionOrNull() ?: Exception("Error comprobando el nombre de usuario")
                )
            }
            if (isAvailableResult.getOrNull() == false) {
                rollbackUnfinishedRegistration()
                return Result.failure(UsernameTakenException())
            }

            val profile = UserProfile(
                uid = registeredUser.id,
                username = normalizedUsername
            )

            val saveProfileResult = userProfileRepository.saveProfile(profile)
            if (saveProfileResult.isFailure) {
                rollbackUnfinishedRegistration()
                val saveError = saveProfileResult.exceptionOrNull()
                if (saveError is IllegalStateException) {
                    return Result.failure(
                        UsernameTakenException(saveError.message ?: "Este usuario ya está en uso.")
                    )
                }
                return Result.failure(saveError ?: Exception("Error al guardar el perfil del usuario"))
            }
            Result.success(registeredUser)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (createdAuthAccount) {
                rollbackUnfinishedRegistration()
            }
            Result.failure(e)
        }
    }

    private suspend fun rollbackUnfinishedRegistration() {
        val deleteResult = runCatching { authRepository.deleteAccount() }.getOrNull()
        if (deleteResult == null || deleteResult.isFailure) {
            runCatching { authRepository.signOut() }
        }
    }
}
