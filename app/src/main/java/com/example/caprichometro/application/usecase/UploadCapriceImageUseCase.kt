package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.repository.ImageStorageRepository

class UploadCapriceImageUseCase(
    private val imageStorageRepository: ImageStorageRepository
) {
    suspend fun execute(imageBytes: ByteArray, capriceId: String): Result<String> {
        return imageStorageRepository.uploadCapriceImage(imageBytes, capriceId)
    }
}
