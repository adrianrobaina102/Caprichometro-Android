package com.example.caprichometro.domain.repository

interface ImageStorageRepository {
    suspend fun uploadCapriceImage(imageBytes: ByteArray, capriceId: String): Result<String>
}
