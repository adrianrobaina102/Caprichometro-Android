package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.IncomingFriendRequest
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

            coroutineScope {
                pendingRequests.map { friendship ->
                    async {
                        val isBlockedResult = friendshipRepository.isBlocked(currentUserId, friendship.requestedBy)
                        // Fail-closed: si falla la comprobación de bloqueo, no mostrar la solicitud
                        if (isBlockedResult.getOrNull() != false) return@async null

                        val profileResult = userProfileRepository.getUserById(friendship.requestedBy)
                        val profile = if (profileResult.isFailure) {
                            UserProfile(uid = friendship.requestedBy, username = "Usuario")
                        } else {
                            profileResult.getOrNull() ?: return@async null
                        }

                        IncomingFriendRequest(
                            friendshipId = friendship.id,
                            sender = profile,
                            createdAt = friendship.createdAt
                        )
                    }
                }.awaitAll().filterNotNull().sortedByDescending { it.createdAt }
            }
        }
    }
}
