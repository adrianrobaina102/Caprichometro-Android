package com.example.caprichometro.application.usecase.profile

import com.example.caprichometro.domain.model.UserProfileRepository

class UpdateBioUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    suspend fun execute(userId: String, bio: String): Result<Unit> {
        val trimmedBio = bio.trim()
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID de usuario no valido."))
        }
        return userProfileRepository.updateBio(userId, trimmedBio)
    }
}
