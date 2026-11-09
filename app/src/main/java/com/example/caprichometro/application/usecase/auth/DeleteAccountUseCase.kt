package com.example.caprichometro.application.usecase.auth

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.AuthRepository
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val friendshipRepository: FriendshipRepository,
    private val capriceRepository: CapriceRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun execute(userId: String, username: String): Result<Unit> = withContext(dispatcher) {
        try {
            // 1. Eliminación en cascada de caprichos y sus subcolecciones de votos
            val capricesResult = capriceRepository?.deleteCapricesForUser(userId)
            if (capricesResult != null && capricesResult.isFailure) {
                return@withContext capricesResult
            }

            // 2. Eliminamos los bloqueos salientes creados por el usuario
            val blocksResult = friendshipRepository.deleteBlocksForUser(userId)
            if (blocksResult.isFailure) {
                return@withContext blocksResult
            }

            // 3. Eliminamos las relaciones de amistad donde participaba el usuario
            val friendshipsResult = friendshipRepository.deleteFriendshipsForUser(userId)
            if (friendshipsResult.isFailure) {
                return@withContext friendshipsResult
            }

            // 4. Eliminamos el perfil de Firestore (users/{uid} y usernames/{username})
            val profileResult = userProfileRepository.deleteProfile(userId, username)
            if (profileResult.isFailure) {
                return@withContext profileResult
            }

            // 5. Eliminamos la cuenta de Firebase Auth
            val authResult = authRepository.deleteAccount()
            if (authResult.isFailure) {
                return@withContext authResult
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
