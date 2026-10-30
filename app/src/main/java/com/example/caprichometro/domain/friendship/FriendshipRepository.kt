package com.example.caprichometro.domain.friendship

import com.example.caprichometro.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface FriendshipRepository {
    suspend fun searchUserByUsername(username: String): Result<UserProfile?>
    suspend fun sendFriendRequest(friendship: Friendship): Result<Unit>
    suspend fun updateFriendshipStatus(friendshipId: String, status: FriendshipStatus): Result<Unit>
    suspend fun removeFriendship(userId: String, friendId: String): Result<Unit>
    suspend fun blockUser(blockerId: String, blockedUserId: String): Result<Unit>
    suspend fun isBlocked(userId: String, otherUserId: String): Result<Boolean>
    suspend fun deleteFriendshipsForUser(userId: String): Result<Unit>
    fun getFriendships(userId: String): Flow<List<Friendship>>
}
