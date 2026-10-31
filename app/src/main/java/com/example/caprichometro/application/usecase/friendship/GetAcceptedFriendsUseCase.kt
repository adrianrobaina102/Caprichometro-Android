package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetAcceptedFriendsUseCase(
    private val friendshipRepository: FriendshipRepository,
    private val userProfileRepository: UserProfileRepository
) {
    fun execute(currentUserId: String): Flow<List<UserProfile>> {
        return friendshipRepository.getFriendships(currentUserId).map { friendships ->
            val acceptedFriendships = friendships.filter { it.status == FriendshipStatus.ACCEPTED }

            acceptedFriendships.mapNotNull { friendship ->
                val friendId = friendship.memberIDs.firstOrNull { it != currentUserId } ?: return@mapNotNull null

                val isBlocked = friendshipRepository.isBlocked(currentUserId, friendId)
                    .getOrThrow()
                if (isBlocked) return@mapNotNull null

                userProfileRepository.getUserById(friendId)
                    .getOrThrow()
            }.sortedBy { it.username.lowercase() }
        }
    }
}
