package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
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
    private val friendshipRepository: FriendshipRepository,
    private val userProfileRepository: UserProfileRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun execute(currentUserId: String): Flow<List<UserProfile>> {
        return friendshipRepository.getFriendships(currentUserId).map { friendships ->
            val acceptedFriendships = friendships.filter { it.status == FriendshipStatus.ACCEPTED }
            if (acceptedFriendships.isEmpty()) return@map emptyList()

            // Lectura en lote de bloqueos salientes propios (1 query en lugar de N)
            val ownBlockedIds = friendshipRepository.getBlockedUserIds(currentUserId)
                .getOrThrow()
                .toSet()

            val semaphore = Semaphore(8)
            coroutineScope {
                acceptedFriendships.map { friendship ->
                    async(defaultDispatcher) {
                        val friendId = friendship.memberIDs.firstOrNull { it != currentUserId } ?: return@async null
                        if (friendId in ownBlockedIds) return@async null

                        semaphore.withPermit {
                            val isBlockedResult = friendshipRepository.isBlocked(currentUserId, friendId)
                            // Fail-closed: si no se puede verificar bloqueo, no exponer al usuario
                            if (isBlockedResult.getOrNull() != false) return@withPermit null

                            val profileResult = userProfileRepository.getUserById(friendId)
                            if (profileResult.isFailure) {
                                UserProfile(uid = friendId, username = "Usuario")
                            } else {
                                profileResult.getOrNull()
                            }
                        }
                    }
                }.awaitAll().filterNotNull().sortedBy { it.username.lowercase() }
            }
        }.flowOn(defaultDispatcher)
    }
}
