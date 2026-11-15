package com.example.caprichometro.domain.model

/**
 * Reglas puras de dominio para la duración (5 minutos) y estado temporal de las encuestas.
 */
object PollTiming {
    const val POLL_DURATION_MILLIS: Long = 5 * 60 * 1000L

    fun calculateTriggerTime(timestamp: Long): Long = timestamp + POLL_DURATION_MILLIS

    fun isPollExpired(
        timestamp: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean = (calculateTriggerTime(timestamp) - currentTimeMillis) <= 0L

    fun remainingMillis(
        timestamp: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Long = (calculateTriggerTime(timestamp) - currentTimeMillis).coerceAtLeast(0L)
}
