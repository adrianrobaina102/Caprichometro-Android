package com.example.caprichometro.application.usecase.auth

import android.content.Context
import com.example.caprichometro.domain.repository.AuthRepository
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.UsernameRules
import kotlinx.coroutines.CancellationException
import java.util.Date
import kotlin.random.Random

class SignInWithGoogleUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) {
    suspend fun execute(context: Context): Result<User> {
        val authResult = authRepository.signInWithGoogle(context)
        if (authResult.isFailure) {
            return authResult
        }

        val user = authResult.getOrNull() ?: return Result.failure(Exception("Usuario no autenticado"))

        return try {
            // 1. Verificamos si el usuario ya tiene un perfil registrado en Firestore
            val existingProfileResult = userProfileRepository.getUserById(user.id)
            if (existingProfileResult.isFailure) {
                return Result.failure(
                    existingProfileResult.exceptionOrNull() ?: Exception("Error al comprobar el perfil existente")
                )
            }

            val existingProfile = existingProfileResult.getOrNull()
            if (existingProfile != null) {
                // Usuario recurrente: el perfil ya existe en Firestore
                return Result.success(user)
            }

            // 2. Nuevo usuario: Generamos un nombre de usuario normalizado y único
            val baseCandidate = generateBaseCandidate(user)
            val finalUsername = findAvailableUsername(baseCandidate)

            // 3. Guardamos el perfil en Firestore (users/{uid} y usernames/{username})
            val profile = UserProfile(
                uid = user.id,
                username = finalUsername,
                createdAt = Date()
            )

            val saveResult = userProfileRepository.saveProfile(profile)
            if (saveResult.isFailure) {
                return Result.failure(
                    saveResult.exceptionOrNull() ?: Exception("Error al registrar el perfil de Google en la base de datos")
                )
            }

            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateBaseCandidate(user: User): String {
        val rawName = if (user.email.isNotBlank()) {
            user.email.substringBefore("@")
        } else {
            user.name
        }

        val clean = UsernameRules.normalize(rawName).replace("[^a-z0-9_]".toRegex(), "")
        return when {
            clean.length in 3..15 -> clean
            clean.length > 15 -> clean.take(12)
            else -> "user"
        }
    }

    private suspend fun findAvailableUsername(base: String): String {
        val isAvailable = userProfileRepository.isUsernameAvailable(base).getOrDefault(false)
        if (isAvailable) {
            return base
        }

        val prefix = if (base.length > 11) base.take(11) else base
        for (i in 1..10) {
            val randomSuffix = Random.nextInt(100, 999)
            val candidate = "$prefix$randomSuffix"
            val available = userProfileRepository.isUsernameAvailable(candidate).getOrDefault(false)
            if (available) {
                return candidate
            }
        }
        return "${prefix.take(9)}${System.currentTimeMillis() % 100000}"
    }
}
