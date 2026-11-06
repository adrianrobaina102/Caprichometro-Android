package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Caprice

interface CapriceDetailsRepository {
    /**
     * Obtiene un capricho concreto a partir de su identificador de documento en Firestore.
     */
    suspend fun getCapriceById(capriceId: String): Result<Caprice?>
}
