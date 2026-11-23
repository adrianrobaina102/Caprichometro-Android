package com.example.caprichometro.infrastructure.data

import android.util.Log
import android.util.LruCache
import com.example.caprichometro.domain.repository.CapriceMediaRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreCapriceMediaRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CapriceMediaRepository {

    companion object {
        const val MEDIA_COLLECTION = "media"
        const val IMAGE_DOCUMENT_ID = "image"
        private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 1024 * 1024
    }

    private val imageCache = object : LruCache<String, String>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: String): Int = value.length * 2
    }

    fun clearCache() {
        imageCache.evictAll()
    }

    override suspend fun getCapriceImageUrl(capriceId: String): Result<String?> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) return@withContext Result.success(null)
        imageCache.get(capriceId)?.let { cached ->
            return@withContext Result.success(cached)
        }
        try {
            val snapshot = db.collection("caprices")
                .document(capriceId)
                .collection(MEDIA_COLLECTION)
                .document(IMAGE_DOCUMENT_ID)
                .get()
                .await()
            val url = snapshot.getString("imageUrl")?.trim()?.takeIf { it.isNotEmpty() }
            if (url != null && url.length * 2 <= MAX_ENTRY_BYTES) {
                imageCache.put(capriceId, url)
            }
            Result.success(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreCapriceMedia", "Error obteniendo imagen de capricho $capriceId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
