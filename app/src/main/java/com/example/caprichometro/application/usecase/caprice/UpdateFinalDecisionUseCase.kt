package com.example.caprichometro.application.usecase.caprice

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceRepository
import com.example.caprichometro.domain.model.PollTiming

class UpdateFinalDecisionUseCase(
    private val capriceRepository: CapriceRepository,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Registra o rectifica la decisión final del autor (`1` o `2`) una vez cerrado el capricho.
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
        if (caprice.finalDecision == decision) {
            return Result.success(caprice)
        }

        val now = clockMillis()
        if (!PollTiming.isPollExpired(caprice.timestamp, now)) {
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
