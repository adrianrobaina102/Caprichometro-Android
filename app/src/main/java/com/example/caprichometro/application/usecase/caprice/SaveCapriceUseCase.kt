package com.example.caprichometro.application.usecase.caprice

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceRepository

class SaveCapriceUseCase (private val repository: CapriceRepository) {
    suspend fun execute(caprice: Caprice): Result<String>
    {
        return repository.save(caprice);
    }
}