package com.example.caprichometro.infrastructure.data

import android.util.Base64
import com.example.caprichometro.domain.repository.ImageStorageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Base64ImageStorageRepository(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ImageStorageRepository {

    companion object {
        private const val MAX_ENCODED_DATA_URI_CHARS = 900_000
    }

    override suspend fun uploadCapriceImage(imageBytes: ByteArray, capriceId: String): Result<String> = withContext(defaultDispatcher) {
        try {
            if (imageBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("La imagen está vacía"))
            }
            val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val dataUri = "data:image/webp;base64,$base64String"
            if (dataUri.length > MAX_ENCODED_DATA_URI_CHARS) {
                return@withContext Result.failure(IllegalArgumentException("La imagen supera el límite permitido para Firestore"))
            }
            Result.success(dataUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
