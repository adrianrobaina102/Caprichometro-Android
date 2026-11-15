package com.example.caprichometro.application.usecase.voting

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import kotlinx.coroutines.flow.first

class CastVoteUseCase(
    private val voteRepository: VoteRepository,
    private val friendshipRepository: FriendshipRepository? = null
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

        val existingVote = voteRepository.getUserVote(caprice.id, voterId).getOrNull()
        if (existingVote != null) {
            return Result.failure(IllegalStateException("Ya has votado en este capricho"))
        }

        if (friendshipRepository != null) {
            val isBlockedResult = friendshipRepository.isBlocked(caprice.userId, voterId)
            if (isBlockedResult.isFailure) {
                return Result.failure(
                    isBlockedResult.exceptionOrNull()
                        ?: IllegalStateException("No se pudo comprobar el estado de bloqueo")
                )
            }
            if (isBlockedResult.getOrDefault(false)) {
                return Result.failure(IllegalStateException("No puedes votar en este capricho por un bloqueo entre ambos usuarios"))
            }

            if (caprice.nearbyCell.isNullOrBlank()) {
                val isFriend = friendshipRepository.getFriendships(voterId).first()
                    .any { it.status == FriendshipStatus.ACCEPTED && caprice.userId in it.memberIDs }
                if (!isFriend) {
                    return Result.failure(IllegalStateException("Solo los amigos del autor pueden votar en este capricho"))
                }
            }
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
