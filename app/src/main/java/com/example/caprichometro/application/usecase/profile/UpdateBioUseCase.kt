package com.example.caprichometro.application.usecase.profile

import com.example.caprichometro.domain.repository.UserProfileWriteRepository

class UpdateBioUseCase(
    private val userProfileWriteRepository: UserProfileWriteRepository
) {
    suspend fun execute(userId: String, bio: String): Result<Unit> {
        val trimmedBio = bio.trim()
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID de usuario no valido."))
        }
        if (trimmedBio.length > 160) {
            return Result.failure(IllegalArgumentException("La biografía no puede superar los 160 caracteres."))
        }
        return userProfileWriteRepository.updateBio(userId, trimmedBio)
    }
}
