package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Vote
import kotlinx.coroutines.flow.Flow

/**
 * Puerto de Dominio para la persistencia y observacion en tiempo real de votos.
 */
interface VoteRepository {
    suspend fun castVote(vote: Vote): Result<Unit>
    fun observeVotes(capriceId: String): Flow<List<Vote>>
    suspend fun getVotes(capriceId: String): Result<List<Vote>>
    suspend fun getUserVote(capriceId: String, voterId: String): Result<Vote?>
}
