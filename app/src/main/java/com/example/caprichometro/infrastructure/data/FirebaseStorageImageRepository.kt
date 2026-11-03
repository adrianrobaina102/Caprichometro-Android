package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.repository.ImageStorageRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await

class FirebaseStorageImageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : ImageStorageRepository {

    override suspend fun uploadCapriceImage(imageBytes: ByteArray, capriceId: String): Result<String> {
        return try {
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
