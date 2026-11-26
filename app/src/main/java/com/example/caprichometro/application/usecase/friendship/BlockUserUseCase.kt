package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.UserBlockRepository

class BlockUserUseCase(
    private val userBlockRepository: UserBlockRepository
) {
    suspend fun execute(blockerId: String, blockedUserId: String): Result<Unit> {
        if (blockerId.isBlank() || blockedUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("Los identificadores de usuario son obligatorios"))
        }
        if (blockerId == blockedUserId) {
            return Result.failure(IllegalArgumentException("No puedes bloquearte a ti mismo"))
        }
        return userBlockRepository.blockUser(blockerId, blockedUserId)
    }
}
