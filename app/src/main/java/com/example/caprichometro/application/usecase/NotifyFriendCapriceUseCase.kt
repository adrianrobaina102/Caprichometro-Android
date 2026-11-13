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
     * Consulta los caprichos recientes de cada amigo aceptado en Firestore de forma concurrente
     * y toma el más reciente visible para [recipientUserId].
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
                        val capricesResult = capriceRepository.getCaprices(friend.uid, limit = 20)
                        val latestCaprice = capricesResult.getOrNull()
                            ?.firstOrNull { it.isVisibleTo(recipientUserId) }
                            ?: return@async null

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

    /**
     * Comprueba si algún amigo aceptado ha confirmado su decisión final (`finalDecision`)
     * en un capricho visible y aún no notificado localmente.
     */
    suspend fun checkAndNotifyFriendFinalDecisions(
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
                        val caprices = capriceRepository.getCaprices(friend.uid, limit = 20).getOrNull()
                            ?: return@async null
                        val latestDecided = caprices
                            .filter { it.isVisibleTo(recipientUserId) && (it.finalDecision == 1 || it.finalDecision == 2) }
                            .maxByOrNull { it.finalDecisionAt ?: it.timestamp }
                            ?: return@async null

                        val decidedAt = latestDecided.finalDecisionAt ?: latestDecided.timestamp
                        val lastId = historyRepository.getLastNotifiedDecisionCapriceId(recipientUserId, friend.uid)
                        val lastTs = historyRepository.getLastNotifiedDecisionTimestamp(recipientUserId, friend.uid)

                        if (latestDecided.id != lastId && decidedAt > lastTs) {
                            onNotify(friend, latestDecided)
                            historyRepository.setLastNotifiedDecision(
                                recipientUserId = recipientUserId,
                                friendUserId = friend.uid,
                                capriceId = latestDecided.id,
                                timestamp = decidedAt
                            )
                            CapriceFeedItem(caprice = latestDecided, author = friend)
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

    fun recordDecisionNotification(recipientUserId: String, friendUserId: String, capriceId: String, timestamp: Long) {
        historyRepository.setLastNotifiedDecision(recipientUserId, friendUserId, capriceId, timestamp)
    }
}
