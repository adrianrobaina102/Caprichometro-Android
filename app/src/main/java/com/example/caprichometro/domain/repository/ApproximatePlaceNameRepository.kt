package com.example.caprichometro.domain.repository

interface ApproximatePlaceNameRepository {
    suspend fun resolvePlaceName(cellId: String): String?
}
