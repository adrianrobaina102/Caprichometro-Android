package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.UserBlockRepository

class UnblockUserUseCase(
    private val userBlockRepository: UserBlockRepository
) {
    suspend fun execute(blockerId: String, blockedUserId: String): Result<Unit> {
        if (blockerId.isBlank() || blockedUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("Los identificadores no pueden estar vacíos"))
        }
        if (blockerId == blockedUserId) {
            return Result.failure(IllegalArgumentException("No puedes desbloquearte a ti mismo"))
        }
        return userBlockRepository.unblockUser(blockerId, blockedUserId)
    }
}
