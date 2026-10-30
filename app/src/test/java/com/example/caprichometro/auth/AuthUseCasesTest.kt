package com.example.caprichometro.auth

import android.content.Context
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.InvalidUsernameException
import com.example.caprichometro.application.usecase.auth.RegisterUseCase
import com.example.caprichometro.application.usecase.auth.SignInWithGoogleUseCase
import com.example.caprichometro.application.usecase.auth.UsernameTakenException
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.AuthRepository
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AuthUseCasesTest {

    private val authRepository: AuthRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val friendshipRepository: FriendshipRepository = mockk()

    @Nested
    inner class RegisterTests {
        private lateinit var registerUseCase: RegisterUseCase

        @BeforeEach
        fun setUp() {
            registerUseCase = RegisterUseCase(authRepository, userProfileRepository)
        }

        @Test
        fun `falla si el nombre de usuario es invalido`() = runTest {
            val result = registerUseCase.execute("test@mail.com", "123456", "ad")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is InvalidUsernameException)
            coVerify(exactly = 0) { userProfileRepository.isUsernameAvailable(any()) }
        }

        @Test
        fun `falla si el nombre de usuario ya esta ocupado`() = runTest {
            coEvery { userProfileRepository.isUsernameAvailable("adrian_dev") } returns Result.success(false)
            val result = registerUseCase.execute("test@mail.com", "123456", "adrian_dev")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is UsernameTakenException)
        }
    }

    @Nested
    inner class GoogleSignInTests {
        private val mockContext = mockk<Context>()
        private lateinit var googleSignInUseCase: SignInWithGoogleUseCase

        @BeforeEach
        fun setUp() {
            googleSignInUseCase = SignInWithGoogleUseCase(authRepository, userProfileRepository)
        }

        @Test
        fun `usuario existente no guarda perfil nuevo`() = runTest {
            val user = User(id = "uid123", email = "test@gmail.com", name = "Test")
            val existingProfile = UserProfile(uid = "uid123", username = "testuser")

            coEvery { authRepository.signInWithGoogle(mockContext) } returns Result.success(user)
            coEvery { userProfileRepository.getUserById("uid123") } returns Result.success(existingProfile)

            val result = googleSignInUseCase.execute(mockContext)
            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { userProfileRepository.saveProfile(any()) }
        }

        @Test
        fun `usuario nuevo crea perfil con username derivado disponible`() = runTest {
            val user = User(id = "uid456", email = "nuevo@gmail.com", name = "Nuevo")

            coEvery { authRepository.signInWithGoogle(mockContext) } returns Result.success(user)
            coEvery { userProfileRepository.getUserById("uid456") } returns Result.success(null)
            coEvery { userProfileRepository.isUsernameAvailable("nuevo") } returns Result.success(true)
            coEvery { userProfileRepository.saveProfile(any()) } returns Result.success(Unit)

            val result = googleSignInUseCase.execute(mockContext)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { userProfileRepository.saveProfile(match { it.username == "nuevo" }) }
        }

        @Test
        fun `username ocupado busca uno libre con sufijo numerico`() = runTest {
            val user = User(id = "uid789", email = "ocupado@gmail.com", name = "Ocupado")

            coEvery { authRepository.signInWithGoogle(mockContext) } returns Result.success(user)
            coEvery { userProfileRepository.getUserById("uid789") } returns Result.success(null)
            coEvery { userProfileRepository.isUsernameAvailable("ocupado") } returns Result.success(false)
            coEvery { userProfileRepository.isUsernameAvailable(neq("ocupado")) } returns Result.success(true)
            coEvery { userProfileRepository.saveProfile(any()) } returns Result.success(Unit)

            val result = googleSignInUseCase.execute(mockContext)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                userProfileRepository.saveProfile(match {
                    it.uid == "uid789" && it.username.startsWith("ocupado") && it.username != "ocupado"
                })
            }
        }
    }

    @Nested
    inner class DeleteAccountTests {
        @Test
        fun `borra amistades, perfil de Firestore y cuenta de Firebase Auth`() = runTest {
            coEvery { friendshipRepository.deleteFriendshipsForUser("uid1") } returns Result.success(Unit)
            coEvery { userProfileRepository.deleteProfile("uid1", "user1") } returns Result.success(Unit)
            coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

            val useCase = DeleteAccountUseCase(authRepository, userProfileRepository, friendshipRepository)
            val result = useCase.execute("uid1", "user1")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { friendshipRepository.deleteFriendshipsForUser("uid1") }
            coVerify(exactly = 1) { userProfileRepository.deleteProfile("uid1", "user1") }
            coVerify(exactly = 1) { authRepository.deleteAccount() }
        }

        @Test
        fun `propaga error si falla el borrado de perfil en Firestore`() = runTest {
            coEvery { friendshipRepository.deleteFriendshipsForUser("uid1") } returns Result.success(Unit)
            coEvery { userProfileRepository.deleteProfile("uid1", "user1") } returns Result.failure(Exception("Error Firestore"))

            val useCase = DeleteAccountUseCase(authRepository, userProfileRepository, friendshipRepository)
            val result = useCase.execute("uid1", "user1")

            assertTrue(result.isFailure)
            assertEquals("Error Firestore", result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { authRepository.deleteAccount() }
        }
    }
}
