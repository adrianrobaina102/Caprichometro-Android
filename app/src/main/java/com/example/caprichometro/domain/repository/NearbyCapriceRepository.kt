package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Caprice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class NearbyCapriceBatch(
    val caprices: List<Caprice>,
    val isFromCache: Boolean,
    val isComplete: Boolean,
    val nextPageToken: String? = null
)

interface NearbyCapriceRepository {
    suspend fun getDiscoverableCaprices(): Result<List<Caprice>>

    fun streamDiscoverableCaprices(
        initialBatchSize: Int = 5,
        pageSize: Int = 20,
        includeCache: Boolean = true
    ): Flow<NearbyCapriceBatch> = flow {
        val caprices = getDiscoverableCaprices().getOrThrow()
        emit(
            NearbyCapriceBatch(
                caprices = caprices,
                isFromCache = false,
                isComplete = true
            )
        )
    }
}

/**
 * Capacidad opcional para consultar primero celdas cercanas mediante `whereIn` + `orderBy(timestamp DESC)`.
 */
interface PreferredCellsNearbyCapriceRepository {
    fun streamDiscoverableCaprices(
        initialBatchSize: Int = 5,
        pageSize: Int = 20,
        includeCache: Boolean = true,
        preferredCells: List<String> = emptyList()
    ): Flow<NearbyCapriceBatch>
}

/**
 * Paginación progresiva por cursor Firestore en General: carga inicial mínima y páginas de 20 bajo demanda.
 */
interface CursorPagedNearbyCapriceRepository : NearbyCapriceRepository, PreferredCellsNearbyCapriceRepository {
    fun streamFirstDiscoverableCapricePage(
        initialBatchSize: Int = 5,
        pageSize: Int = 20,
        includeCache: Boolean = true,
        preferredCells: List<String> = emptyList()
    ): Flow<NearbyCapriceBatch>

    suspend fun loadNextDiscoverableCapricePage(
        pageToken: String,
        pageSize: Int = 20
    ): Result<NearbyCapriceBatch>
}
