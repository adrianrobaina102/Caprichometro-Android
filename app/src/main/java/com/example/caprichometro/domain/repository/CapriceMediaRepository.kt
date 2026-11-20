package com.example.caprichometro.domain.repository

interface CapriceMediaRepository {
    suspend fun getCapriceImageUrl(capriceId: String): Result<String?>
}
