package com.example.caprichometro.application.usecase.voting

import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ObserveVotesUseCase(
    private val voteRepository: VoteRepository
) {
    fun execute(capriceId: String): Flow<List<Vote>> {
        if (capriceId.isBlank()) return emptyFlow()
        return voteRepository.observeVotes(capriceId)
    }
}
