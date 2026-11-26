package com.example.caprichometro.application.usecase.voting

import com.example.caprichometro.domain.repository.FriendshipRepository
import com.example.caprichometro.domain.model.FriendshipStatus
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollTiming
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import kotlinx.coroutines.flow.first

class CastVoteUseCase(
    private val voteRepository: VoteRepository,
    private val friendshipRepository: FriendshipRepository? = null
) {
    suspend fun execute(
        caprice: Caprice,
        voterId: String,
        option: Int,
        allowChange: Boolean = false
    ): Result<Unit> {
        if (voterId.isBlank()) {
            return Result.failure(IllegalArgumentException("El usuario votante no es válido"))
        }
        if (caprice.userId == voterId) {
            return Result.failure(IllegalStateException("No puedes votar en tu propio capricho"))
        }
        if (!caprice.isVisibleTo(voterId)) {
            return Result.failure(IllegalStateException("No tienes permiso para votar en este capricho"))
        }
        if (PollTiming.isPollExpired(caprice.timestamp)) {
            return Result.failure(IllegalStateException("La votación ya ha cerrado (límite de 5 minutos alcanzado)"))
        }
        if (option != 1 && option != 2) {
            return Result.failure(IllegalArgumentException("Opción inválida (debe ser 1 o 2)"))
        }

        val userVoteResult = voteRepository.getUserVote(caprice.id, voterId)
        if (allowChange && userVoteResult.isFailure) {
            return Result.failure(
                userVoteResult.exceptionOrNull()
                    ?: IllegalStateException("No se pudo comprobar tu voto anterior")
            )
        }
        val existingVote = userVoteResult.getOrNull()
        if (existingVote != null) {
            if (!allowChange) {
                return Result.failure(IllegalStateException("Ya has votado en este capricho"))
            }
            if (existingVote.option == option) {
                return Result.success(Unit)
            }
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
                val friendships = try {
                    friendshipRepository.getFriendships(voterId).first()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return Result.failure(e)
                }
                val isFriend = friendships
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
        return if (allowChange && existingVote != null) {
            voteRepository.updateVote(vote)
        } else {
            voteRepository.castVote(vote)
        }
    }
}
