package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.repository.ApproximatePlaceNameRepository

class GetApproximatePlaceNameUseCase(
    private val repository: ApproximatePlaceNameRepository
) {
    suspend fun execute(cellId: String): String? {
        if (cellId.isBlank()) return null
        return repository.resolvePlaceName(cellId)
    }
}
