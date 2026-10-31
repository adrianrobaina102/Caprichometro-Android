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
    suspend fun execute(email: String, password: String, username: String): Result<User> {
        return try {
            val normalizedUsername = UsernameRules.normalize(username)

            if (!UsernameRules.isValid(normalizedUsername)) {
                return Result.failure(InvalidUsernameException())
            }
            val isAvailableResult = userProfileRepository.isUsernameAvailable(normalizedUsername)
            if (isAvailableResult.isFailure) {
                return Result.failure(isAvailableResult.exceptionOrNull() ?: Exception("Error comprobando el nombre de usuario"))
            }
            if (isAvailableResult.getOrNull() == false) {
                return Result.failure(UsernameTakenException())
            }

            val authResult = authRepository.register(email, password, normalizedUsername)
            if (authResult.isFailure) {
                return Result.failure(authResult.exceptionOrNull() ?: Exception("Error en Firebase Auth"))
            }
            val registeredUser = authResult.getOrNull()!!
            val profile = UserProfile(
                uid = registeredUser.id,
                username = normalizedUsername
            )

            val saveProfileResult = userProfileRepository.saveProfile(profile)
            if (saveProfileResult.isFailure) {
                return Result.failure(saveProfileResult.exceptionOrNull() ?: Exception("Error al guardar el perfil del usuario"))
            }
            Result.success(registeredUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
