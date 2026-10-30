package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository

class RemoveFriendUseCase(
    private val friendshipRepository: FriendshipRepository
) {
    suspend fun execute(userId: String, friendId: String): Result<Unit> {
        if (userId.isBlank() || friendId.isBlank()) {
            return Result.failure(IllegalArgumentException("Los identificadores de usuario son obligatorios"))
        }
        if (userId == friendId) {
            return Result.failure(IllegalArgumentException("No puedes eliminarte a ti mismo"))
        }
        return friendshipRepository.removeFriendship(userId, friendId)
    }
}
