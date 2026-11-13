package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Caprice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CapriceObservationRepository {
    /**
     * Escucha en tiempo real nuevos caprichos publicados por [friendUserId]
     * con una marca de tiempo superior a [sinceTimestamp].
     */
    fun observeFriendCaprices(friendUserId: String, sinceTimestamp: Long): Flow<Caprice>

    /**
     * Escucha en tiempo real confirmaciones de decisión final (`finalDecision`)
     * de [friendUserId] posteriores a [sinceTimestamp].
     */
    fun observeFriendFinalDecisions(friendUserId: String, sinceTimestamp: Long): Flow<Caprice> = emptyFlow()
}
