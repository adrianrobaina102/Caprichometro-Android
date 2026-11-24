package com.example.caprichometro.domain.model

interface CapriceRepository {
    suspend fun save(caprice: Caprice): Result<String>

    suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>>

    suspend fun getCachedCaprices(userId: String, limit: Int): List<Caprice> = emptyList()

    suspend fun deleteCapricesForUser(userId: String): Result<Unit> = Result.success(Unit)

    suspend fun updateFinalDecision(
        capriceId: String,
        decision: Int,
        decidedAt: Long
    ): Result<Unit> = Result.success(Unit)
}

/**
 * Capacidad opcional para consultar caprichos de múltiples autores en lotes (`whereIn`).
 */
interface BatchCapriceRepository {
    suspend fun getCapricesForUsers(userIds: List<String>, limit: Int): Result<List<Caprice>>
}
