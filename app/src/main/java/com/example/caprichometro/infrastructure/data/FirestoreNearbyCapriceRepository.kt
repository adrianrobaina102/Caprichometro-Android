package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.NearbyCapriceBatch
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreNearbyCapriceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NearbyCapriceRepository {

    companion object {
        const val INITIAL_BATCH_SIZE = 5
        const val PAGE_SIZE = 20
        private const val COLLECTION_NAME = "caprices"
    }

    override suspend fun getDiscoverableCaprices(): Result<List<Caprice>> = withContext(ioDispatcher) {
        try {
            val finalBatch = streamDiscoverableCaprices(
                initialBatchSize = PAGE_SIZE,
                pageSize = PAGE_SIZE,
                includeCache = false
            ).last()
            Result.success(finalBatch.caprices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun streamDiscoverableCaprices(
        initialBatchSize: Int,
        pageSize: Int,
        includeCache: Boolean
    ): Flow<NearbyCapriceBatch> = flow {
        val baseQuery = db.collection(COLLECTION_NAME)
            .whereNotEqualTo("nearbyCell", "")
            .orderBy("nearbyCell")

        val cachedList: List<Caprice> = if (includeCache) {
            try {
                baseQuery.get(Source.CACHE).await().documents.mapNotNull { doc ->
                    doc.toCapriceOrNull("FirestoreNearbyCapriceRepo")
                        ?.takeIf { !it.nearbyCell.isNullOrBlank() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        if (cachedList.isNotEmpty()) {
            emit(NearbyCapriceBatch(caprices = cachedList, isFromCache = true, isComplete = false))
        }

        val collected = mutableListOf<Caprice>()
        val collectedIds = HashSet<String>()
        var lastDocument: DocumentSnapshot? = null
        var isFirstPage = true

        while (true) {
            val currentLimit = if (isFirstPage) {
                initialBatchSize.coerceAtLeast(1)
            } else {
                pageSize.coerceAtLeast(1)
            }
            var query = baseQuery.limit(currentLimit.toLong())
            if (lastDocument != null) {
                query = query.startAfter(lastDocument)
            }

            val snapshot = try {
                query.get(Source.SERVER).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cachedList.isNotEmpty() || collected.isNotEmpty()) {
                    emit(
                        NearbyCapriceBatch(
                            caprices = collected.ifEmpty { cachedList },
                            isFromCache = collected.isEmpty(),
                            isComplete = true
                        )
                    )
                    return@flow
                }
                throw e
            }
            val documents = snapshot.documents
            for (doc in documents) {
                doc.toCapriceOrNull("FirestoreNearbyCapriceRepo")?.let { caprice ->
                    if (!caprice.nearbyCell.isNullOrBlank() && collectedIds.add(caprice.id)) {
                        collected.add(caprice)
                    }
                }
            }

            val hasMorePages = documents.size >= currentLimit && documents.lastOrNull() != null
            val batchCaprices = if (hasMorePages && cachedList.isNotEmpty()) {
                // Evita parpadeos conservando los elementos en caché aún no alcanzados en páginas posteriores
                collected + cachedList.filter { it.id !in collectedIds }
            } else {
                collected.toList()
            }
            emit(
                NearbyCapriceBatch(
                    caprices = batchCaprices,
                    isFromCache = false,
                    isComplete = !hasMorePages
                )
            )
            if (!hasMorePages) break
            lastDocument = documents.lastOrNull() ?: break
            isFirstPage = false
        }
    }.flowOn(ioDispatcher)
}
