package com.example.caprichometro.application.usecase.voting

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler

class CastVoteUseCase(
    private val voteRepository: VoteRepository
) {
    suspend fun execute(caprice: Caprice, voterId: String, option: Int): Result<Unit> {
        if (voterId.isBlank()) {
            return Result.failure(IllegalArgumentException("El usuario votante no es válido"))
        }
        if (caprice.userId == voterId) {
            return Result.failure(IllegalStateException("No puedes votar en tu propio capricho"))
        }
        if (!caprice.isVisibleTo(voterId)) {
            return Result.failure(IllegalStateException("No tienes permiso para votar en este capricho"))
        }
        if (PollNotificationScheduler.isPollExpired(caprice.timestamp)) {
            return Result.failure(IllegalStateException("La votación ya ha cerrado (límite de 5 minutos alcanzado)"))
        }
        if (option != 1 && option != 2) {
            return Result.failure(IllegalArgumentException("Opción inválida (debe ser 1 o 2)"))
        }

        val vote = Vote(
            capriceId = caprice.id,
            voterId = voterId,
            option = option,
            timestamp = System.currentTimeMillis()
        )
        return voteRepository.castVote(vote)
    }
}
