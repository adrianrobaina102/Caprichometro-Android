package com.example.caprichometro.application.usecase.profile

import com.example.caprichometro.domain.model.UserProfileRepository

/**
 * Actualiza o elimina la foto de perfil del usuario (URL o Data URI WebP comprimido).
 */
class UpdateProfilePhotoUseCase(
    private val userProfileRepository: UserProfileRepository
) {
    suspend fun execute(userId: String, photoUrl: String): Result<Unit> {
        val trimmedUrl = photoUrl.trim()
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID de usuario no valido."))
        }
        return userProfileRepository.updatePhotoUrl(userId, trimmedUrl)
    }
}
