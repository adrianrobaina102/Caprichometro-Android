package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.NearbyCapriceBatch
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import com.example.caprichometro.domain.repository.PreferredCellsNearbyCapriceRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
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
) : NearbyCapriceRepository, PreferredCellsNearbyCapriceRepository {

    companion object {
        const val INITIAL_BATCH_SIZE = 5
        const val PAGE_SIZE = 20
        private const val MAX_CACHE_DOCS = 50
        private const val MAX_GLOBAL_DISCOVERABLE_DOCS = 80
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
    ): Flow<NearbyCapriceBatch> = streamDiscoverableCaprices(
        initialBatchSize = initialBatchSize,
        pageSize = pageSize,
        includeCache = includeCache,
        preferredCells = emptyList()
    )

    override fun streamDiscoverableCaprices(
        initialBatchSize: Int,
        pageSize: Int,
        includeCache: Boolean,
        preferredCells: List<String>
    ): Flow<NearbyCapriceBatch> = flow {
        val baseQuery = db.collection(COLLECTION_NAME)
            .whereNotEqualTo("nearbyCell", "")
            .orderBy("nearbyCell")

        val cachedList: List<Caprice> = if (includeCache) {
            try {
                baseQuery.limit(MAX_CACHE_DOCS.toLong()).get(Source.CACHE).await().documents.mapNotNull { doc ->
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

        val safePreferredCells = preferredCells.distinct().filter { it.isNotBlank() }.take(30)
        if (safePreferredCells.isNotEmpty()) {
            try {
                val localSnapshot = db.collection(COLLECTION_NAME)
                    .whereIn("nearbyCell", safePreferredCells)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(pageSize.coerceAtLeast(1).toLong())
                    .get(Source.SERVER)
                    .await()
                for (doc in localSnapshot.documents) {
                    doc.toCapriceOrNull("FirestoreNearbyCapriceRepo")?.let { caprice ->
                        if (!caprice.nearbyCell.isNullOrBlank() && collectedIds.add(caprice.id)) {
                            collected.add(caprice)
                        }
                    }
                }
                if (collected.isNotEmpty()) {
                    val localBatch = if (cachedList.isNotEmpty()) {
                        collected + cachedList.filter { it.id !in collectedIds }
                    } else {
                        collected.toList()
                    }
                    emit(NearbyCapriceBatch(caprices = localBatch, isFromCache = false, isComplete = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    if (cachedList.isNotEmpty()) {
                        emit(NearbyCapriceBatch(caprices = cachedList, isFromCache = true, isComplete = true))
                        return@flow
                    }
                    throw e
                }
            } catch (e: Exception) {
                if (cachedList.isNotEmpty()) {
                    emit(NearbyCapriceBatch(caprices = cachedList, isFromCache = true, isComplete = true))
                    return@flow
                }
                throw e
            }
        }

        var lastDocument: DocumentSnapshot? = null
        var isFirstPage = true
        var globalDocsScanned = 0

        while (globalDocsScanned < MAX_GLOBAL_DISCOVERABLE_DOCS) {
            val remainingBudget = MAX_GLOBAL_DISCOVERABLE_DOCS - globalDocsScanned
            val currentLimit = minOf(
                remainingBudget,
                if (isFirstPage) initialBatchSize.coerceAtLeast(1) else pageSize.coerceAtLeast(1)
            )
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
            globalDocsScanned += documents.size
            for (doc in documents) {
                doc.toCapriceOrNull("FirestoreNearbyCapriceRepo")?.let { caprice ->
                    if (!caprice.nearbyCell.isNullOrBlank() && collectedIds.add(caprice.id)) {
                        collected.add(caprice)
                    }
                }
            }

            val hasMorePages = documents.size >= currentLimit &&
                documents.lastOrNull() != null &&
                globalDocsScanned < MAX_GLOBAL_DISCOVERABLE_DOCS
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
