package com.example.caprichometro.domain.repository

interface CapriceNotificationHistoryRepository {
    /**
     * Devuelve el ID del último capricho notificado para la combinación de cuenta receptora y amigo.
     */
    fun getLastNotifiedCapriceId(recipientUserId: String, friendUserId: String): String?

    /**
     * Devuelve la fecha/hora en milisegundos del último capricho notificado para dicha combinación.
     */
    fun getLastNotifiedTimestamp(recipientUserId: String, friendUserId: String): Long

    /**
     * Registra en almacenamiento local el último capricho notificado con éxito para no volver a avisar.
     */
    fun setLastNotified(
        recipientUserId: String,
        friendUserId: String,
        capriceId: String,
        timestamp: Long
    )

    fun getLastNotifiedDecisionCapriceId(recipientUserId: String, friendUserId: String): String? = null

    fun getLastNotifiedDecisionTimestamp(recipientUserId: String, friendUserId: String): Long = 0L

    fun setLastNotifiedDecision(
        recipientUserId: String,
        friendUserId: String,
        capriceId: String,
        timestamp: Long
    ) {}
}
