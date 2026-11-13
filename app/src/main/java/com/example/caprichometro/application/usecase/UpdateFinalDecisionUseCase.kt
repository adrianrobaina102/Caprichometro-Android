package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler

class UpdateFinalDecisionUseCase(
    private val capriceRepository: CapriceRepository,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Registra la decisión final del autor (`1` o `2`) una vez cerrado el capricho.
     */
    suspend fun execute(
        caprice: Caprice,
        currentUserId: String,
        decision: Int
    ): Result<Caprice> {
        if (currentUserId.isBlank() || caprice.userId != currentUserId) {
            return Result.failure(IllegalStateException("Solo el autor puede confirmar la decisión final"))
        }
        if (decision != 1 && decision != 2) {
            return Result.failure(IllegalArgumentException("La decisión final debe ser 1 o 2"))
        }
        if (caprice.finalDecision != null) {
            return Result.failure(IllegalStateException("La decisión final ya fue confirmada"))
        }

        val now = clockMillis()
        if ((now - caprice.timestamp) < PollNotificationScheduler.POLL_DURATION_MILLIS) {
            return Result.failure(IllegalStateException("Espera a que termine la votación para confirmar tu decisión"))
        }

        return capriceRepository.updateFinalDecision(
            capriceId = caprice.id,
            decision = decision,
            decidedAt = now
        ).map {
            caprice.copy(
                finalDecision = decision,
                finalDecisionAt = now
            )
        }
    }
}
