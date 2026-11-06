package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Caprice
import kotlinx.coroutines.flow.Flow

interface CapriceObservationRepository {
    /**
     * Escucha en tiempo real nuevos caprichos publicados por [friendUserId]
     * con una marca de tiempo superior a [sinceTimestamp].
     */
    fun observeFriendCaprices(friendUserId: String, sinceTimestamp: Long): Flow<Caprice>
}
