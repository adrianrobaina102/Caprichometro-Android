package com.example.caprichometro.application.usecase.profile

import com.example.caprichometro.domain.repository.UserProfileRepository

class UpdateBioUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    suspend fun execute(userId: String, bio: String): Result<Unit> {
        val trimmedBio = bio.trim()
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID de usuario no valido."))
        }
        if (trimmedBio.length > 160) {
            return Result.failure(IllegalArgumentException("La biografía no puede superar los 160 caracteres."))
        }
        return userProfileRepository.updateBio(userId, trimmedBio)
    }
}
