package com.example.caprichometro.auth

import android.content.Context
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.RegisterUseCase
import com.example.caprichometro.application.usecase.auth.SignInUseCase
import com.example.caprichometro.application.usecase.auth.SignInWithGoogleUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.infrastructure.ui.auth.AuthViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val signInUseCase: SignInUseCase = mockk()
    private val registerUseCase: RegisterUseCase = mockk()
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase = mockk()
    private val signOutUseCase: SignOutUseCase = mockk()
    private val deleteAccountUseCase: DeleteAccountUseCase = mockk()
    private val getCurrentUserUseCase: GetCurrentUserUseCase = mockk()
    private val mockContext: Context = mockk()

    private lateinit var viewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getCurrentUserUseCase.execute() } returns null
        viewModel = AuthViewModel(
            signInUseCase,
            registerUseCase,
            signInWithGoogleUseCase,
            signOutUseCase,
            deleteAccountUseCase,
            getCurrentUserUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `inicializa isAuthSuccessful a true si ya habia un usuario con sesion iniciada`() {
        val existingUser = User(id = "uid_stored", email = "stored@gmail.com", name = "Stored User")
        val mockGetCurrentUser: GetCurrentUserUseCase = mockk()
        every { mockGetCurrentUser.execute() } returns existingUser

        val autologinViewModel = AuthViewModel(
            signInUseCase,
            registerUseCase,
            signInWithGoogleUseCase,
            signOutUseCase,
            deleteAccountUseCase,
            mockGetCurrentUser
        )

        assertThat(autologinViewModel.state.value.isAuthSuccessful).isTrue()
    }

    @Test
    fun `signIn rechaza formato de correo invalido sin llamar al caso de uso`() = runTest {
        viewModel.updateEmail("correo_sin_arroba")
        viewModel.updatePassword("123456")

        viewModel.signIn()

        assertThat(viewModel.state.value.errorMessage).isEqualTo("Por favor, introduce un correo electrónico válido")
        coVerify(exactly = 0) { signInUseCase.execute(any(), any()) }
    }

    @Test
    fun `register exige al menos 6 caracteres en la contrasena`() = runTest {
        viewModel.updateName("adrian_dev")
        viewModel.updateEmail("adrian@gmail.com")
        viewModel.updatePassword("12345") // 5 caracteres -> inválido
        viewModel.updateBirthday(LocalDate.of(2002, 5, 15))

        viewModel.register()

        assertThat(viewModel.state.value.errorMessage).isEqualTo("La contraseña debe tener al menos 6 caracteres")
        coVerify(exactly = 0) { registerUseCase.execute(any(), any(), any()) }
    }

    @Test
    fun `register rechaza fecha de nacimiento futura`() = runTest {
        viewModel.updateName("adrian_dev")
        viewModel.updateEmail("adrian@gmail.com")
        viewModel.updatePassword("123456")
        viewModel.updateBirthday(LocalDate.now().plusDays(1))

        viewModel.register()

        assertThat(viewModel.state.value.errorMessage).isEqualTo("La fecha de nacimiento no puede ser en el futuro")
        coVerify(exactly = 0) { registerUseCase.execute(any(), any(), any()) }
    }

    @Test
    fun `signInWithGoogle actualiza el estado a éxito cuando Google devuelve un usuario valido`() = runTest {
        val expectedUser = User(id = "123", email = "test@gmail.com", name = "Test User")
        coEvery { signInWithGoogleUseCase.execute(any()) } returns Result.success(expectedUser)

        viewModel.signInWithGoogle(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.isAuthSuccessful).isTrue()
        assertThat(viewModel.state.value.isSubmitting).isFalse()
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `signInWithGoogle muestra un error cuando el usuario cancela la ventana nativa`() = runTest {
        val expectedErrorMessage = "Ventana cancelada por el usuario"
        coEvery { signInWithGoogleUseCase.execute(any()) } returns Result.failure(Exception(expectedErrorMessage))

        viewModel.signInWithGoogle(mockContext)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.isAuthSuccessful).isFalse()
        assertThat(viewModel.state.value.isSubmitting).isFalse()
        assertThat(viewModel.state.value.errorMessage).isEqualTo(expectedErrorMessage)
    }

    @Test
    fun `signOut llama al caso de uso y restablece isAuthSuccessful a false`() = runTest {
        coEvery { signOutUseCase.execute() } returns Result.success(Unit)

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.isAuthSuccessful).isFalse()
        coVerify(exactly = 1) { signOutUseCase.execute() }
    }

    @Test
    fun `deleteAccount con exito restablece isAuthSuccessful a false`() = runTest {
        coEvery { deleteAccountUseCase.execute("uid123", "user123") } returns Result.success(Unit)

        viewModel.deleteAccount("uid123", "user123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.isAuthSuccessful).isFalse()
        assertThat(viewModel.state.value.isSubmitting).isFalse()
        coVerify(exactly = 1) { deleteAccountUseCase.execute("uid123", "user123") }
    }
}
