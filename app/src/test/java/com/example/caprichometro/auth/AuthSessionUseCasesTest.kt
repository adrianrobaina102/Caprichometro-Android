package com.example.caprichometro.auth

import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthSessionUseCasesTest {

    private val authRepository: AuthRepository = mockk()

    @Test
    fun `GetCurrentUserUseCase devuelve el usuario actual del repositorio`() {
        val user = User(id = "u1", email = "u1@test.com", name = "u1")
        every { authRepository.getCurrentUser() } returns user

        val result = GetCurrentUserUseCase(authRepository).execute()

        assertThat(result).isEqualTo(user)
        verify(exactly = 1) { authRepository.getCurrentUser() }
    }

    @Test
    fun `SignOutUseCase delega el cierre de sesion en AuthRepository`() = runTest {
        coEvery { authRepository.signOut() } returns Result.success(Unit)

        val result = SignOutUseCase(authRepository).execute()

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { authRepository.signOut() }
    }
}
