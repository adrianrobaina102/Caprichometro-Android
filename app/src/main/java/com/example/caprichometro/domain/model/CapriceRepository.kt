package com.example.caprichometro.domain.model

interface CapriceRepository {
    suspend fun save(caprice: Caprice): Result<String>

    suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>>

    suspend fun deleteCapricesForUser(userId: String): Result<Unit> = Result.success(Unit)
}
