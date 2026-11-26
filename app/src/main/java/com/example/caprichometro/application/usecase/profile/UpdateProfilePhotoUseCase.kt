package com.example.caprichometro.application.usecase.profile

import com.example.caprichometro.domain.repository.UserProfileWriteRepository

/**
 * Actualiza o elimina la foto de perfil del usuario (URL o Data URI WebP comprimido).
 */
class UpdateProfilePhotoUseCase(
    private val userProfileWriteRepository: UserProfileWriteRepository
) {
    suspend fun execute(userId: String, photoUrl: String): Result<Unit> {
        val trimmedUrl = photoUrl.trim()
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("ID de usuario no valido."))
        }
        if (trimmedUrl.isNotEmpty()) {
            val isDataImage = trimmedUrl.startsWith("data:image/webp;base64,", ignoreCase = true) ||
                trimmedUrl.startsWith("data:image/jpeg;base64,", ignoreCase = true) ||
                trimmedUrl.startsWith("data:image/jpg;base64,", ignoreCase = true) ||
                trimmedUrl.startsWith("data:image/png;base64,", ignoreCase = true)
            if (!isDataImage || trimmedUrl.length > 900_000) {
                return Result.failure(IllegalArgumentException("Formato o tamaño de foto no válido."))
            }
        }
        return userProfileWriteRepository.updatePhotoUrl(userId, trimmedUrl)
    }
}
