package com.example.caprichometro.application.usecase

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.repository.CapriceDetailsRepository
import kotlinx.coroutines.flow.first

class GetNotificationCapriceUseCase(
    private val capriceDetailsRepository: CapriceDetailsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase
) {
    /**
     * Carga el capricho exacto por [capriceId] y verifica que [recipientUserId] tenga permiso para verlo:
     * - Si es propio, devuelve el capricho con el perfil del usuario.
     * - Si es de un amigo, comprueba que sea un amigo aceptado (y no bloqueado) y devuelve el capricho con su autor.
     * - Si el capricho no existe o no tiene permiso, devuelve null.
     */
    suspend fun execute(capriceId: String, recipientUserId: String): Result<CapriceFeedItem?> = runCatching {
        if (capriceId.isBlank() || recipientUserId.isBlank()) return@runCatching null

        val caprice = capriceDetailsRepository.getCapriceById(capriceId).getOrThrow()
            ?: return@runCatching null

        // 1. Capricho propio
        if (caprice.userId == recipientUserId) {
            val ownProfile = userProfileRepository.getUserById(recipientUserId).getOrNull()
            return@runCatching CapriceFeedItem(
                caprice = caprice,
                author = ownProfile ?: UserProfile(
                    uid = recipientUserId,
                    username = "tú"
                )
            )
        }

        // 2. Capricho de un amigo: verificar que la relación esté aceptada y sin bloqueos
        val acceptedFriends = getAcceptedFriendsUseCase.execute(recipientUserId).first()
        val authorFriend = acceptedFriends.find { it.uid == caprice.userId }

        if (authorFriend != null) {
            CapriceFeedItem(caprice = caprice, author = authorFriend)
        } else {
            // Acceso denegado: el autor no es un amigo aceptado
            null
        }
    }
}
