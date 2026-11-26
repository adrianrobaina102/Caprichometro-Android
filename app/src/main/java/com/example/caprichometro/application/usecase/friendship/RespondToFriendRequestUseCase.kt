package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.FriendshipMutationRepository
import com.example.caprichometro.domain.model.FriendshipStatus

class RespondToFriendRequestUseCase(
    private val friendshipMutationRepository: FriendshipMutationRepository
) {
    suspend fun execute(friendshipId: String, accept: Boolean): Result<Unit> {
        val newStatus = if (accept) FriendshipStatus.ACCEPTED else FriendshipStatus.DECLINED
        return friendshipMutationRepository.updateFriendshipStatus(friendshipId, newStatus)
    }
}
