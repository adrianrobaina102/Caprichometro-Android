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

                // Si existe bloqueo en cualquiera de los dos sentidos, se oculta la relación
                val isBlocked = friendshipRepository.isBlocked(currentUserId, friendId)
                    .getOrDefault(false)
                if (isBlocked) return@mapNotNull null

                // Si el amigo ya no existe en Firestore (cuenta borrada), se omite
                userProfileRepository.getUserById(friendId).getOrNull()
            }.sortedBy { it.username.lowercase() }
        }
    }
}
