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

            // Filtramos: Solo las pendientes y que hayan sido solicitadas por la otra persona
            val pendingRequests = friendships.filter { friendship ->
                friendship.status == FriendshipStatus.PENDING && friendship.requestedBy != currentUserId
            }

            // Según la regla acordada en common.md:
            // Si el remitente no tiene perfil en users, se omite esa solicitud específica
            // sin inventar perfiles de reemplazo ("Usuario") y se muestran las demás.
            pendingRequests.mapNotNull { friendship ->
                val profileResult = userProfileRepository.getUserById(friendship.requestedBy)
                val profile = profileResult.getOrNull() ?: return@mapNotNull null

                IncomingFriendRequest(
                    friendshipId = friendship.id,
                    sender = profile,
                    createdAt = friendship.createdAt
                )
            }.sortedByDescending { it.createdAt }
        }
    }
}
