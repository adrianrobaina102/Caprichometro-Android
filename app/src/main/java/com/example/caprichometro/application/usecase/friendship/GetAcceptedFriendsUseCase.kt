package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.FriendshipReadRepository
import com.example.caprichometro.domain.repository.UserBlockRepository
import com.example.caprichometro.domain.model.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileReadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GetAcceptedFriendsUseCase(
    private val friendshipReadRepository: FriendshipReadRepository,
    private val userBlockRepository: UserBlockRepository,
    private val userProfileReadRepository: UserProfileReadRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun execute(currentUserId: String): Flow<List<UserProfile>> {
        return friendshipReadRepository.getFriendships(currentUserId).map { friendships ->
            val acceptedFriendships = friendships.filter { it.status == FriendshipStatus.ACCEPTED }
            if (acceptedFriendships.isEmpty()) return@map emptyList()

            // Lectura en lote de bloqueos salientes propios (1 query en lugar de N)
            val ownBlockedIds = runCatching {
                userBlockRepository.getBlockedUserIds(currentUserId).getOrDefault(emptyList()).toSet()
            }.getOrDefault(emptySet())

            val semaphore = Semaphore(8)
            coroutineScope {
                acceptedFriendships.map { friendship ->
                    async(defaultDispatcher) {
                        val friendId = friendship.memberIDs.firstOrNull { it != currentUserId } ?: return@async null
                        if (friendId in ownBlockedIds) return@async null

                        semaphore.withPermit {
                            val isBlocked = userBlockRepository.isBlocked(currentUserId, friendId).getOrThrow()
                            if (isBlocked) return@withPermit null

                            userProfileReadRepository.getUserById(friendId).getOrThrow()
                        }
                    }
                }.awaitAll().filterNotNull().sortedBy { it.username.lowercase() }
            }
        }.flowOn(defaultDispatcher)
    }
}
