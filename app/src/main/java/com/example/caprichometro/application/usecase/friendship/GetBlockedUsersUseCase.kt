package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GetBlockedUsersUseCase(
    private val friendshipRepository: FriendshipRepository,
    private val userProfileRepository: UserProfileRepository
) {
    suspend fun execute(userId: String): Result<List<UserProfile>> {
        if (userId.isBlank()) {
            return Result.success(emptyList())
        }
        val blockedIdsResult = friendshipRepository.getBlockedUserIds(userId)
        if (blockedIdsResult.isFailure) {
            return Result.failure(blockedIdsResult.exceptionOrNull() ?: Exception("Error al cargar usuarios bloqueados"))
        }
        val blockedIds = blockedIdsResult.getOrDefault(emptyList())
        val profiles = coroutineScope {
            blockedIds.map { blockedId ->
                async {
                    userProfileRepository.getUserById(blockedId).getOrNull()
                }
            }.awaitAll().filterNotNull().sortedBy { it.username.lowercase() }
        }
        return Result.success(profiles)
    }
}
