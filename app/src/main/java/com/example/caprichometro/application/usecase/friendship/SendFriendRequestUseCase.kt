package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.model.Friendship
import com.example.caprichometro.domain.repository.FriendshipMutationRepository
import com.example.caprichometro.domain.model.FriendshipStatus

class SendFriendRequestUseCase (
    private val friendshipMutationRepository: FriendshipMutationRepository
) {
    suspend fun execute(currentUserId: String, targetUserId: String): Result<Unit>
    {
        if (currentUserId == targetUserId) {
            return Result.failure(Exception("No puedes enviarte una solicitud a ti mismo"))
        }
        val friendshipId = if (currentUserId < targetUserId) {
            "${currentUserId}_$targetUserId"
        } else {
            "${targetUserId}_$currentUserId"
        }

        val friendship = Friendship(
            id = friendshipId,
            memberIDs = listOf(currentUserId, targetUserId),
            requestedBy = currentUserId,
            status = FriendshipStatus.PENDING
        )
        return friendshipMutationRepository.sendFriendRequest(friendship)
    }
}