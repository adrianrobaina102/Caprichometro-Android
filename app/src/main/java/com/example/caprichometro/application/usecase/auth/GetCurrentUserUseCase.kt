package com.example.caprichometro.application.usecase.auth

import com.example.caprichometro.domain.repository.AuthRepository
import com.example.caprichometro.domain.model.User

class GetCurrentUserUseCase(private val authRepository: AuthRepository) {
    fun execute(): User? = authRepository.getCurrentUser()
}
