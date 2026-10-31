package com.example.caprichometro.application.usecase.friendship

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UsernameRules

class SearchUserByUsernameUseCase(
    private val friendshipRepository: FriendshipRepository
) {
    suspend fun execute(username: String): Result<UserProfile?> {
        val cleanUsername = UsernameRules.normalize(username)
        if (!UsernameRules.isValid(cleanUsername)) {
            return Result.failure(IllegalArgumentException("Usa de 3 a 20 letras, números o guiones bajos."))
        }
        return friendshipRepository.searchUserByUsername(cleanUsername)
    }
}
