package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceCacheControl
import com.example.caprichometro.domain.model.CapriceRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class FirestoreCapriceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : CapriceRepository, CapriceCacheControl {

    private data class CachedCapricesEntry(
        val caprices: List<Caprice>,
        val fetchedLimit: Int,
        val rawDocCount: Int,
        val cachedAtMillis: Long
    )

    companion object {
        internal const val CAPRICE_CACHE_TTL_MS = 30_000L
    }

    private val collectionName = "caprices"
    private val userCapricesCache = ConcurrentHashMap<String, CachedCapricesEntry>()
    private val userFetchLocks = ConcurrentHashMap<String, Mutex>()

    override fun invalidateUser(userId: String) {
        if (userId.isNotBlank()) {
            userCapricesCache.remove(userId)
        }
    }

    override fun invalidateAll() {
        userCapricesCache.clear()
    }

    private fun getCachedIfFresh(userId: String, requiredLimit: Int): List<Caprice>? {
        val entry = userCapricesCache[userId] ?: return null
        if ((clockMillis() - entry.cachedAtMillis) >= CAPRICE_CACHE_TTL_MS) {
            userCapricesCache.remove(userId, entry)
            return null
        }
        val coversLimit = entry.fetchedLimit >= requiredLimit || entry.rawDocCount < entry.fetchedLimit
        return if (coversLimit) entry.caprices else null
    }

    override suspend fun save(caprice: Caprice): Result<String> = withContext(ioDispatcher) {
        try {
            val hasMedia = caprice.hasImage || caprice.imageUrl.isNotBlank()
            val data = mutableMapOf<String, Any>(
                "userId" to caprice.userId,
                "type" to caprice.type,
                "question" to caprice.question,
                "answer1" to caprice.answer1,
                "answer2" to caprice.answer2,
                "timestamp" to caprice.timestamp,
                "hasImage" to hasMedia
            )
            if (caprice.visibleToUserIds.isNotEmpty()) {
                data["visibleToUserIds"] = (listOf(caprice.userId) + caprice.visibleToUserIds)
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            val normalizedNearbyCell = caprice.nearbyCell?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedNearbyCell != null) {
                data["nearbyCell"] = normalizedNearbyCell
            }
            val normalizedLocationCell = caprice.locationCell?.trim()?.takeIf { it.isNotEmpty() }
            if (normalizedLocationCell != null) {
                data["locationCell"] = normalizedLocationCell
            }
            val validPrice = caprice.price?.takeIf { it.isFinite() && it >= 0.0 }
            if (validPrice != null) {
                data["price"] = validPrice
            }
            val docRef = db.collection(collectionName).document()
            val batch = db.batch()
            batch.set(docRef, data)
            if (caprice.imageUrl.isNotBlank()) {
                val mediaRef = docRef
                    .collection(FirestoreCapriceMediaRepository.MEDIA_COLLECTION)
                    .document(FirestoreCapriceMediaRepository.IMAGE_DOCUMENT_ID)
                batch.set(
                    mediaRef,
                    mapOf(
                        "imageUrl" to caprice.imageUrl,
                        "userId" to caprice.userId,
                        "createdAt" to caprice.timestamp
                    )
                )
            }
            batch.commit().await()
            invalidateUser(caprice.userId)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Error guardando capricho: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>> =
        withContext(ioDispatcher) {
            if (userId.isBlank() || limit <= 0) return@withContext Result.success(emptyList())

            getCachedIfFresh(userId, limit)?.let { cached ->
                return@withContext Result.success(cached.take(limit))
            }

            val lock = userFetchLocks.computeIfAbsent(userId) { Mutex() }
            lock.withLock {
                getCachedIfFresh(userId, limit)?.let { cached ->
                    return@withLock Result.success(cached.take(limit))
                }
                try {
                    val startedAt = clockMillis()
                    val (sorted, rawCount) = fetchSortedCaprices(userId = userId, limit = limit, source = null)
                    userCapricesCache[userId] = CachedCapricesEntry(sorted, limit, rawCount, clockMillis())
                    Log.d(
                        "FeedPerf",
                        "caprices(uid=$userId, limit=$limit): ${sorted.size} docs en ${clockMillis() - startedAt} ms"
                    )
                    Result.success(sorted.take(limit))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("FirestoreCapriceRepo", "Error obteniendo caprichos para $userId: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }

    override suspend fun getCachedCaprices(userId: String, limit: Int): List<Caprice> =
        withContext(ioDispatcher) {
            if (userId.isBlank() || limit <= 0) return@withContext emptyList()
            userCapricesCache[userId]
                ?.takeIf { it.fetchedLimit >= limit || it.rawDocCount < it.fetchedLimit }
                ?.let { entry ->
                    return@withContext entry.caprices.take(limit)
                }
            try {
                fetchSortedCaprices(userId = userId, limit = limit, source = Source.CACHE).first.take(limit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun deleteCapricesForUser(userId: String): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId.isBlank()) return@withContext Result.success(Unit)
            try {
                val snapshot = db.collection(collectionName)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val votesSnapshot = doc.reference.collection("votes").get().await()
                    val mediaSnapshot = doc.reference
                        .collection(FirestoreCapriceMediaRepository.MEDIA_COLLECTION)
                        .get()
                        .await()
                    val childRefs = votesSnapshot.documents.map { it.reference } +
                        mediaSnapshot.documents.map { it.reference }
                    if (childRefs.isNotEmpty()) {
                        childRefs.chunked(450).forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEach { childRef ->
                                batch.delete(childRef)
                            }
                            batch.commit().await()
                        }
                    }
                    doc.reference.delete().await()
                }
                invalidateUser(userId)
                Log.d("FirestoreCapriceRepo", "Eliminados ${snapshot.size()} caprichos del usuario $userId")
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FirestoreCapriceRepo", "Error eliminando caprichos del usuario $userId: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun updateFinalDecision(
        capriceId: String,
        decision: Int,
        decidedAt: Long
    ): Result<Unit> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("ID de capricho inválido"))
        }
        try {
            db.collection(collectionName)
                .document(capriceId)
                .update(
                    mapOf(
                        "finalDecision" to decision,
                        "finalDecisionAt" to decidedAt
                    )
                )
                .await()
            userCapricesCache.forEach { (uid, entry) ->
                if (entry.caprices.any { it.id == capriceId }) {
                    userCapricesCache.remove(uid, entry)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Error guardando decisión final para $capriceId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchSortedCaprices(userId: String, limit: Int, source: Source?): Pair<List<Caprice>, Int> {
        val indexedQuery = db.collection(collectionName)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        return try {
            val snapshot = if (source != null) {
                indexedQuery.get(source).await()
            } else {
                indexedQuery.get().await()
            }
            val parsed = snapshot.documents
                .mapNotNull { doc -> doc.toCapriceOrNull("FirestoreCapriceRepo") }
            parsed to snapshot.documents.size
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.FAILED_PRECONDITION) throw e
            Log.w("FirestoreCapriceRepo", "Índice compuesto (userId, timestamp) aún no disponible; usando ordenación local")
            val fallbackQuery = db.collection(collectionName).whereEqualTo("userId", userId)
            val fallbackSnapshot = if (source != null) {
                fallbackQuery.get(source).await()
            } else {
                fallbackQuery.get().await()
            }
            val parsed = fallbackSnapshot.documents
                .mapNotNull { doc -> doc.toCapriceOrNull("FirestoreCapriceRepo") }
                .sortedByDescending { it.timestamp }
                .take(limit)
            parsed to fallbackSnapshot.documents.size
        }
    }
}
