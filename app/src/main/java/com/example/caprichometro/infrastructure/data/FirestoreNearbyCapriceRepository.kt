package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CursorPagedNearbyCapriceRepository
import com.example.caprichometro.domain.repository.NearbyCapriceBatch
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
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
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FirestoreNearbyCapriceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NearbyCapriceRepository, CursorPagedNearbyCapriceRepository {

    companion object {
        const val INITIAL_BATCH_SIZE = 5
        const val PAGE_SIZE = 20
        private const val MAX_CACHE_DOCS = 50
        private const val MAX_GLOBAL_DISCOVERABLE_DOCS = 80
        private const val SERVER_QUERY_TIMEOUT_MS = 10_000L
        private const val COLLECTION_NAME = "caprices"
    }

    private data class PageState(
        val lastDocument: DocumentSnapshot,
        val seenIds: Set<String>
    )

    private val pageStates = ConcurrentHashMap<String, PageState>()

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
        val allCollected = mutableListOf<Caprice>()
        val seenIds = HashSet<String>()
        var nextPageToken: String? = null
        var globalDocsCount = 0

        streamFirstDiscoverableCapricePage(
            initialBatchSize = initialBatchSize,
            pageSize = pageSize,
            includeCache = includeCache,
            preferredCells = preferredCells
        ).collect { firstBatch ->
            if (firstBatch.isFromCache) {
                emit(firstBatch)
            } else {
                firstBatch.caprices.forEach { caprice ->
                    if (seenIds.add(caprice.id)) {
                        allCollected.add(caprice)
                    }
                }
                nextPageToken = firstBatch.nextPageToken
                if (firstBatch.isComplete && nextPageToken == null) {
                    emit(NearbyCapriceBatch(caprices = allCollected.toList(), isFromCache = false, isComplete = true))
                } else {
                    emit(NearbyCapriceBatch(caprices = firstBatch.caprices, isFromCache = false, isComplete = false))
                }
            }
        }

        while (nextPageToken != null && globalDocsCount < MAX_GLOBAL_DISCOVERABLE_DOCS) {
            val token = nextPageToken ?: break
            val pageResult = loadNextDiscoverableCapricePage(token, pageSize).getOrThrow()
            globalDocsCount += pageResult.caprices.size.coerceAtLeast(pageSize)
            pageResult.caprices.forEach { caprice ->
                if (seenIds.add(caprice.id)) {
                    allCollected.add(caprice)
                }
            }
            nextPageToken = pageResult.nextPageToken
            val finished = nextPageToken == null || globalDocsCount >= MAX_GLOBAL_DISCOVERABLE_DOCS
            emit(
                NearbyCapriceBatch(
                    caprices = allCollected.toList(),
                    isFromCache = false,
                    isComplete = finished
                )
            )
            if (finished) break
        }
    }.flowOn(ioDispatcher)

    override fun streamFirstDiscoverableCapricePage(
        initialBatchSize: Int,
        pageSize: Int,
        includeCache: Boolean,
        preferredCells: List<String>
    ): Flow<NearbyCapriceBatch> = flow {
        pageStates.clear()
        val baseQuery = db.collection(COLLECTION_NAME)
            .whereNotEqualTo("nearbyCell", "")
            .orderBy("nearbyCell")
        val safePreferredCells = preferredCells.distinct().filter { it.isNotBlank() }.take(30)

        val cachedList: List<Caprice> = if (includeCache) {
            try {
                val cachedQuery = if (safePreferredCells.isNotEmpty()) {
                    db.collection(COLLECTION_NAME)
                        .whereIn("nearbyCell", safePreferredCells)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                } else {
                    baseQuery
                }
                cachedQuery.limit(MAX_CACHE_DOCS.toLong()).get(Source.CACHE).await().documents.mapNotNull { doc ->
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
        val initialLimit = initialBatchSize.coerceAtLeast(1)

        if (safePreferredCells.isNotEmpty()) {
            try {
                val localSnapshot = withTimeout(SERVER_QUERY_TIMEOUT_MS) {
                    db.collection(COLLECTION_NAME)
                        .whereIn("nearbyCell", safePreferredCells)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(initialLimit.toLong())
                        .get(Source.SERVER)
                        .await()
                }
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

        val snapshot = try {
            withTimeout(SERVER_QUERY_TIMEOUT_MS) {
                baseQuery.limit(initialLimit.toLong()).get(Source.SERVER).await()
            }
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

        val lastDoc = documents.lastOrNull()
        val nextPageToken = if (documents.size >= initialLimit && lastDoc != null) {
            UUID.randomUUID().toString().also { token ->
                pageStates[token] = PageState(lastDocument = lastDoc, seenIds = collectedIds.toSet())
            }
        } else {
            null
        }
        emit(
            NearbyCapriceBatch(
                caprices = collected.toList(),
                isFromCache = false,
                isComplete = nextPageToken == null,
                nextPageToken = nextPageToken
            )
        )
    }.flowOn(ioDispatcher)

    override suspend fun loadNextDiscoverableCapricePage(
        pageToken: String,
        pageSize: Int
    ): Result<NearbyCapriceBatch> = withContext(ioDispatcher) {
        val initialState = pageStates[pageToken]
            ?: return@withContext Result.success(
                NearbyCapriceBatch(caprices = emptyList(), isFromCache = false, isComplete = true)
            )
        val baseQuery = db.collection(COLLECTION_NAME)
            .whereNotEqualTo("nearbyCell", "")
            .orderBy("nearbyCell")
        val limit = pageSize.coerceAtLeast(1)
        var currentLastDoc = initialState.lastDocument
        val seenIds = HashSet(initialState.seenIds)

        try {
            while (true) {
                val snapshot = withTimeout(SERVER_QUERY_TIMEOUT_MS) {
                    baseQuery
                        .startAfter(currentLastDoc)
                        .limit(limit.toLong())
                        .get(Source.SERVER)
                        .await()
                }
                val documents = snapshot.documents
                val newCaprices = mutableListOf<Caprice>()
                for (doc in documents) {
                    doc.toCapriceOrNull("FirestoreNearbyCapriceRepo")?.let { caprice ->
                        if (!caprice.nearbyCell.isNullOrBlank() && seenIds.add(caprice.id)) {
                            newCaprices.add(caprice)
                        }
                    }
                }
                val lastDoc = documents.lastOrNull()
                if (newCaprices.isNotEmpty() || documents.size < limit || lastDoc == null) {
                    pageStates.remove(pageToken)
                    val nextToken = if (documents.size >= limit && lastDoc != null) {
                        UUID.randomUUID().toString().also { token ->
                            pageStates[token] = PageState(lastDocument = lastDoc, seenIds = seenIds.toSet())
                        }
                    } else {
                        null
                    }
                    return@withContext Result.success(
                        NearbyCapriceBatch(
                            caprices = newCaprices,
                            isFromCache = false,
                            isComplete = nextToken == null,
                            nextPageToken = nextToken
                        )
                    )
                }
                currentLastDoc = lastDoc
            }
            pageStates.remove(pageToken)
            Result.success(NearbyCapriceBatch(caprices = emptyList(), isFromCache = false, isComplete = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
