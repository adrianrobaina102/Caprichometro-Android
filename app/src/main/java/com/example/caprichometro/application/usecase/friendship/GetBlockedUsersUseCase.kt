package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.repository.UserBlockRepository
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileReadRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GetBlockedUsersUseCase(
    private val userBlockRepository: UserBlockRepository,
    private val userProfileReadRepository: UserProfileReadRepository
) {
    suspend fun execute(userId: String): Result<List<UserProfile>> {
        if (userId.isBlank()) {
            return Result.success(emptyList())
        }
        val blockedIdsResult = userBlockRepository.getBlockedUserIds(userId)
        if (blockedIdsResult.isFailure) {
            return Result.failure(blockedIdsResult.exceptionOrNull() ?: Exception("Error al cargar usuarios bloqueados"))
        }
        val blockedIds = blockedIdsResult.getOrDefault(emptyList())
        val profiles = coroutineScope {
            blockedIds.map { blockedId ->
                async {
                    userProfileReadRepository.getUserById(blockedId).getOrNull()
                }
            }.awaitAll().filterNotNull().sortedBy { it.username.lowercase() }
        }
        return Result.success(profiles)
    }
}
