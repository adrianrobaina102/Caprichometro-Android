package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.repository.CapriceMediaRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class FirestoreCapriceMediaRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CapriceMediaRepository {

    companion object {
        const val MEDIA_COLLECTION = "media"
        const val IMAGE_DOCUMENT_ID = "image"
    }

    private val imageCache = ConcurrentHashMap<String, String>()

    override suspend fun getCapriceImageUrl(capriceId: String): Result<String?> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) return@withContext Result.success(null)
        imageCache[capriceId]?.let { cached ->
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
            if (url != null) {
                imageCache[capriceId] = url
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
