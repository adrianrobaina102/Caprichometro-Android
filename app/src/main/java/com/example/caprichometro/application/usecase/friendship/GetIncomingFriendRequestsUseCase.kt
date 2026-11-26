package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.FriendshipReadRepository
import com.example.caprichometro.domain.repository.UserBlockRepository
import com.example.caprichometro.domain.model.FriendshipStatus
import com.example.caprichometro.domain.model.IncomingFriendRequest
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileReadRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetIncomingFriendRequestsUseCase(
    private val friendshipReadRepository: FriendshipReadRepository,
    private val userBlockRepository: UserBlockRepository,
    private val userProfileReadRepository: UserProfileReadRepository
) {
    fun execute(currentUserId: String): Flow<List<IncomingFriendRequest>> {
        return friendshipReadRepository.getFriendships(currentUserId).map { friendships ->

            val pendingRequests = friendships.filter { friendship ->
                friendship.status == FriendshipStatus.PENDING && friendship.requestedBy != currentUserId
            }

            coroutineScope {
                pendingRequests.map { friendship ->
                    async {
                        val isBlocked = userBlockRepository.isBlocked(currentUserId, friendship.requestedBy).getOrThrow()
                        if (isBlocked) return@async null

                        val profile = userProfileReadRepository.getUserById(friendship.requestedBy).getOrThrow()
                            ?: return@async null

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
