package com.example.caprichometro.infrastructure.data

import android.util.Base64
import com.example.caprichometro.domain.repository.ImageStorageRepository

class Base64ImageStorageRepository : ImageStorageRepository {
    override suspend fun uploadCapriceImage(imageBytes: ByteArray, capriceId: String): Result<String> {
        return try {
            val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            Result.success("data:image/webp;base64,$base64String")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
