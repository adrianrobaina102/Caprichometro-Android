package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Friendship
import com.example.caprichometro.domain.model.FriendshipStatus

import com.example.caprichometro.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Consultas y búsqueda de amistades y usuarios.
 */
interface FriendshipReadRepository {
    suspend fun searchUserByUsername(username: String): Result<UserProfile?>
    suspend fun searchUsernamesByPrefix(prefix: String, limit: Int = 10): Result<List<String>> = Result.success(emptyList())
    suspend fun getCachedFriendships(userId: String): List<Friendship> = emptyList()
    fun getFriendships(userId: String): Flow<List<Friendship>>
}

/**
 * Escritura y borrado de relaciones de amistad.
 */
interface FriendshipMutationRepository {
    suspend fun sendFriendRequest(friendship: Friendship): Result<Unit>
    suspend fun updateFriendshipStatus(friendshipId: String, status: FriendshipStatus): Result<Unit>
    suspend fun removeFriendship(userId: String, friendId: String): Result<Unit>
    suspend fun deleteFriendshipsForUser(userId: String): Result<Unit>
}

/**
 * Gestión y consulta de bloqueos entre usuarios.
 */
interface UserBlockRepository {
    suspend fun blockUser(blockerId: String, blockedUserId: String): Result<Unit>
    suspend fun unblockUser(blockerId: String, blockedUserId: String): Result<Unit>
    suspend fun getBlockedUserIds(userId: String): Result<List<String>>
    suspend fun isBlocked(userId: String, otherUserId: String): Result<Boolean>
    suspend fun getCachedBlockedUserIds(userId: String): List<String> = emptyList()
    suspend fun isCachedBlocked(userId: String, otherUserId: String): Boolean = false
    suspend fun deleteBlocksForUser(userId: String): Result<Unit> = Result.success(Unit)
}

/**
 * Puerto compuesto mantenido para adaptadores de infraestructura completos.
 */
interface FriendshipRepository :
    FriendshipReadRepository,
    FriendshipMutationRepository,
    UserBlockRepository
