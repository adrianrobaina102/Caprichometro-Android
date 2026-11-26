package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.FriendshipMutationRepository

class RemoveFriendUseCase(
    private val friendshipMutationRepository: FriendshipMutationRepository
) {
    suspend fun execute(userId: String, friendId: String): Result<Unit> {
        if (userId.isBlank() || friendId.isBlank()) {
            return Result.failure(IllegalArgumentException("Los identificadores de usuario son obligatorios"))
        }
        if (userId == friendId) {
            return Result.failure(IllegalArgumentException("No puedes eliminarte a ti mismo"))
        }
        return friendshipMutationRepository.removeFriendship(userId, friendId)
    }
}
