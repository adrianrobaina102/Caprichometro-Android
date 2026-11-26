package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceRepository

class GetCapricesUseCase(
    private val repository: CapriceRepository
) {
    suspend fun execute(userId: String, limit: Int): Result<List<Caprice>> {
        return repository.getCaprices(userId, limit)
    }
}