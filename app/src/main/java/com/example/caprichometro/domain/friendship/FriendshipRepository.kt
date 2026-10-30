package com.example.caprichometro.domain.friendship

import com.example.caprichometro.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface FriendshipRepository {
    suspend fun searchUserByUsername(username: String): Result<UserProfile?>
    suspend fun sendFriendRequest(friendship: Friendship): Result<Unit>
    suspend fun updateFriendshipStatus(friendshipId: String, status: FriendshipStatus): Result<Unit>
    suspend fun deleteFriendshipsForUser(userId: String): Result<Unit>
    fun getFriendships(userId: String): Flow<List<Friendship>>
}
