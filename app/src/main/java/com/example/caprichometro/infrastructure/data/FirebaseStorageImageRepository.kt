package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.repository.ImageStorageRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseStorageImageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ImageStorageRepository {

    override suspend fun uploadCapriceImage(imageBytes: ByteArray, capriceId: String): Result<String> = withContext(ioDispatcher) {
        try {
            val imageRef = storage.reference.child("caprices/$capriceId.webp")
            val metadata = StorageMetadata.Builder()
                .setContentType("image/webp")
                .build()

            imageRef.putBytes(imageBytes, metadata).await()
            val downloadUrl = imageRef.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
