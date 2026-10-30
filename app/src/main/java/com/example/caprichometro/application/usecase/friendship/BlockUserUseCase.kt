package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository

class BlockUserUseCase(
    private val friendshipRepository: FriendshipRepository
) {
    suspend fun execute(blockerId: String, blockedUserId: String): Result<Unit> {
        if (blockerId.isBlank() || blockedUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("Los identificadores de usuario son obligatorios"))
        }
        if (blockerId == blockedUserId) {
            return Result.failure(IllegalArgumentException("No puedes bloquearte a ti mismo"))
        }
        return friendshipRepository.blockUser(blockerId, blockedUserId)
    }
}
