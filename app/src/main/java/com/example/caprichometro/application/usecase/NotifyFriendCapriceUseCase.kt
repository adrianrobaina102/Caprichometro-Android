package com.example.caprichometro.application.usecase

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.CapriceNotificationHistoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class NotifyFriendCapriceUseCase(
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val capriceRepository: CapriceRepository,
    private val historyRepository: CapriceNotificationHistoryRepository
) {
    /**
     * Consulta el último capricho (limit = 1) de cada amigo aceptado en Firestore de forma concurrente.
     * Si no se había notificado previamente para esta cuenta receptora, invoca [onNotify]
     * y actualiza el checkpoint en el historial local para no repetirlo.
     */
    suspend fun checkAndNotifyUnnotifiedFriendCaprices(
        recipientUserId: String,
        onNotify: (author: UserProfile, caprice: Caprice) -> Unit
    ): List<CapriceFeedItem> {
        if (recipientUserId.isBlank()) return emptyList()

        return try {
            val acceptedFriends = getAcceptedFriendsUseCase.execute(recipientUserId).first()
            if (acceptedFriends.isEmpty()) return emptyList()

            coroutineScope {
                acceptedFriends.map { friend ->
                    async {
                        val capricesResult = capriceRepository.getCaprices(friend.uid, limit = 1)
                        val latestCaprice = capricesResult.getOrNull()?.firstOrNull() ?: return@async null

                        val lastId = historyRepository.getLastNotifiedCapriceId(recipientUserId, friend.uid)
                        val lastTs = historyRepository.getLastNotifiedTimestamp(recipientUserId, friend.uid)

                        if (latestCaprice.id != lastId && latestCaprice.timestamp > lastTs) {
                            onNotify(friend, latestCaprice)
                            historyRepository.setLastNotified(
                                recipientUserId = recipientUserId,
                                friendUserId = friend.uid,
                                capriceId = latestCaprice.id,
                                timestamp = latestCaprice.timestamp
                            )
                            CapriceFeedItem(caprice = latestCaprice, author = friend)
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Permite registrar manualmente una notificación en el historial (por ejemplo, cuando se recibe en directo).
     */
    fun recordNotification(recipientUserId: String, friendUserId: String, capriceId: String, timestamp: Long) {
        historyRepository.setLastNotified(recipientUserId, friendUserId, capriceId, timestamp)
    }
}
