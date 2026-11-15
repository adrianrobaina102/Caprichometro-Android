package com.example.caprichometro.application.usecase.auth

import com.example.caprichometro.domain.repository.AuthRepository

class SignOutUseCase(private val authRepository: AuthRepository) {
    suspend fun execute(): Result<Unit> {
        return authRepository.signOut()
    }
}
