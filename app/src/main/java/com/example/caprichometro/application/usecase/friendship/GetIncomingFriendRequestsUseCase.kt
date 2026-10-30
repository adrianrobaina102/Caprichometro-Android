package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.IncomingFriendRequest
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetIncomingFriendRequestsUseCase(
    private val friendshipRepository: FriendshipRepository,
    private val userProfileRepository: UserProfileRepository
) {
    fun execute(currentUserId: String): Flow<List<IncomingFriendRequest>> {
        return friendshipRepository.getFriendships(currentUserId).map { friendships ->

            val pendingRequests = friendships.filter { friendship ->
                friendship.status == FriendshipStatus.PENDING && friendship.requestedBy != currentUserId
            }

            pendingRequests.mapNotNull { friendship ->
                // Si existe bloqueo en cualquiera de los dos sentidos, se oculta la solicitud
                val isBlocked = friendshipRepository.isBlocked(currentUserId, friendship.requestedBy)
                    .getOrDefault(false)
                if (isBlocked) return@mapNotNull null

                // Si el remitente no tiene perfil en users, se omite esa solicitud específica
                val profile = userProfileRepository.getUserById(friendship.requestedBy).getOrNull()
                    ?: return@mapNotNull null

                IncomingFriendRequest(
                    friendshipId = friendship.id,
                    sender = profile,
                    createdAt = friendship.createdAt
                )
            }.sortedByDescending { it.createdAt }
        }
    }
}
