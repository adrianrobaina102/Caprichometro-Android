package com.example.caprichometro.infrastructure.data

import android.util.Base64
import com.example.caprichometro.domain.repository.ImageStorageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Base64ImageStorageRepository(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ImageStorageRepository {

    override suspend fun uploadCapriceImage(imageBytes: ByteArray, capriceId: String): Result<String> = withContext(defaultDispatcher) {
        try {
            val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            Result.success("data:image/webp;base64,$base64String")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
