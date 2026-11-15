package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceCacheControl
import com.example.caprichometro.domain.model.CapriceRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

    private fun getCachedIfFresh(userId: String): List<Caprice>? {
        val entry = userCapricesCache[userId] ?: return null
        return if ((clockMillis() - entry.cachedAtMillis) < CAPRICE_CACHE_TTL_MS) {
            entry.caprices
        } else {
            userCapricesCache.remove(userId, entry)
            null
        }
    }

    override suspend fun save(caprice: Caprice): Result<String> = withContext(ioDispatcher) {
        try {
            val data = mutableMapOf<String, Any>(
                "userId" to caprice.userId,
                "type" to caprice.type,
                "question" to caprice.question,
                "answer1" to caprice.answer1,
                "answer2" to caprice.answer2,
                "timestamp" to caprice.timestamp
            )
            if (caprice.imageUrl.isNotBlank()) {
                data["imageUrl"] = caprice.imageUrl
            }
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
            val document = db.collection(collectionName).add(data).await()
            invalidateUser(caprice.userId)
            Result.success(document.id)
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

            getCachedIfFresh(userId)?.let { cached ->
                return@withContext Result.success(cached.take(limit))
            }

            val lock = userFetchLocks.computeIfAbsent(userId) { Mutex() }
            lock.withLock {
                getCachedIfFresh(userId)?.let { cached ->
                    return@withLock Result.success(cached.take(limit))
                }
                try {
                    val startedAt = clockMillis()
                    val allSorted = fetchSortedCaprices(userId = userId, source = null)
                    userCapricesCache[userId] = CachedCapricesEntry(allSorted, clockMillis())
                    Log.d(
                        "FeedPerf",
                        "caprices(uid=$userId): ${allSorted.size} docs en ${clockMillis() - startedAt} ms"
                    )
                    Result.success(allSorted.take(limit))
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
            userCapricesCache[userId]?.let { entry ->
                return@withContext entry.caprices.take(limit)
            }
            try {
                fetchSortedCaprices(userId = userId, source = Source.CACHE).take(limit)
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
                    if (!votesSnapshot.isEmpty) {
                        votesSnapshot.documents.chunked(450).forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEach { voteDoc ->
                                batch.delete(voteDoc.reference)
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

    private suspend fun fetchSortedCaprices(userId: String, source: Source?): List<Caprice> {
        val query = db.collection(collectionName).whereEqualTo("userId", userId)
        val snapshot = if (source != null) {
            query.get(source).await()
        } else {
            query.get().await()
        }
        return snapshot.documents
            .mapNotNull { doc -> doc.toCapriceOrNull("FirestoreCapriceRepo") }
            .sortedByDescending { it.timestamp }
    }
}
