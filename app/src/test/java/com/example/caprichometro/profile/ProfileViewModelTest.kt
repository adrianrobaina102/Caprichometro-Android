package com.example.caprichometro.profile

import com.example.caprichometro.application.usecase.GetCapricesUseCase
import com.example.caprichometro.application.usecase.auth.DeleteAccountUseCase
import com.example.caprichometro.application.usecase.auth.GetCurrentUserUseCase
import com.example.caprichometro.application.usecase.auth.SignOutUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetBlockedUsersUseCase
import com.example.caprichometro.application.usecase.friendship.UnblockUserUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.User
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.infrastructure.ui.profile.ProfileViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val getCurrentUserUseCase: GetCurrentUserUseCase = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase = mockk()
    private val unblockUserUseCase: UnblockUserUseCase = mockk()
    private val signOutUseCase: SignOutUseCase = mockk()
    private val deleteAccountUseCase: DeleteAccountUseCase = mockk()
    private val getCapricesUseCase: GetCapricesUseCase = mockk()
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase = mockk()

    private lateinit var viewModel: ProfileViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProfileViewModel(
            getCurrentUserUseCase,
            userProfileRepository,
            getBlockedUsersUseCase,
            unblockUserUseCase,
            signOutUseCase,
            deleteAccountUseCase,
            getCapricesUseCase,
            getAcceptedFriendsUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadProfile para perfil propio carga usuario, caprichos, amigos y bloqueados`() = runTest {
        val myUid = "user_me"
        val myUser = User(id = myUid, email = "me@test.com", name = "Adrian")
        val myProfile = UserProfile(uid = myUid, username = "adrian")
        val capricesList = listOf(
            Caprice(id = "c1", userId = myUid, question = "¿Zapatillas?", answer1 = "Sí", answer2 = "No")
        )
        val friendProfile = UserProfile(uid = "f1", username = "lucia")
        val blockedProfile = UserProfile(uid = "b1", username = "troll")

        every { getCurrentUserUseCase.execute() } returns myUser
        coEvery { userProfileRepository.getUserById(myUid) } returns Result.success(myProfile)
        coEvery { getCapricesUseCase.execute(myUid, 50) } returns Result.success(capricesList)
        coEvery { getBlockedUsersUseCase.execute(myUid) } returns Result.success(listOf(blockedProfile))
        coEvery { getAcceptedFriendsUseCase.execute(myUid) } returns flowOf(listOf(friendProfile))

        viewModel.loadProfile(targetUserId = myUid, currentUserId = myUid)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.isOwnProfile).isTrue()
        assertThat(state.email).isEqualTo("me@test.com")
        assertThat(state.profile?.username).isEqualTo("adrian")
        assertThat(state.caprices).hasSize(1)
        assertThat(state.friends).hasSize(1)
        assertThat(state.friends[0].username).isEqualTo("lucia")
        assertThat(state.blockedUsers).hasSize(1)
    }

    @Test
    fun `loadProfile para perfil de amigo oculta bloqueados y email por privacidad`() = runTest {
        val myUid = "user_me"
        val friendUid = "friend_123"
        val friendProfile = UserProfile(uid = friendUid, username = "marcos")
        val friendCaprices = listOf(
            Caprice(id = "c2", userId = friendUid, question = "¿Viaje?", answer1 = "Roma", answer2 = "París")
        )

        coEvery { userProfileRepository.getUserById(friendUid) } returns Result.success(friendProfile)
        coEvery { getCapricesUseCase.execute(friendUid, 50) } returns Result.success(friendCaprices)
        coEvery { getAcceptedFriendsUseCase.execute(friendUid) } returns flowOf(emptyList())

        viewModel.loadProfile(targetUserId = friendUid, currentUserId = myUid)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.isOwnProfile).isFalse()
        assertThat(state.email).isEmpty()
        assertThat(state.profile?.username).isEqualTo("marcos")
        assertThat(state.caprices).hasSize(1)
        assertThat(state.blockedUsers).isEmpty()
        coVerify(exactly = 0) { getBlockedUsersUseCase.execute(any()) }
    }

    @Test
    fun `unblockUser actualiza la lista de bloqueados en el estado al completarse`() = runTest {
        val myUid = "user_me"
        val blockedUid = "b1"
        val myUser = User(id = myUid, email = "me@test.com", name = "Adrian")
        val blockedUser = UserProfile(uid = blockedUid, username = "bloqueado")

        every { getCurrentUserUseCase.execute() } returns myUser
        coEvery { userProfileRepository.getUserById(myUid) } returns Result.success(UserProfile(uid = myUid, username = "me"))
        coEvery { getCapricesUseCase.execute(myUid, 50) } returns Result.success(emptyList())
        coEvery { getBlockedUsersUseCase.execute(myUid) } returns Result.success(listOf(blockedUser))
        coEvery { getAcceptedFriendsUseCase.execute(myUid) } returns flowOf(emptyList())
        coEvery { unblockUserUseCase.execute(myUid, blockedUid) } returns Result.success(Unit)

        viewModel.loadProfile(myUid, myUid)
        advanceUntilIdle()
        assertThat(viewModel.state.value.blockedUsers).hasSize(1)

        viewModel.unblockUser(myUid, blockedUid)
        advanceUntilIdle()

        assertThat(viewModel.state.value.blockedUsers).isEmpty()
        coVerify(exactly = 1) { unblockUserUseCase.execute(myUid, blockedUid) }
    }

    @Test
    fun `signOut ejecuta el caso de uso y actualiza isSignedOut`() = runTest {
        coEvery { signOutUseCase.execute() } returns Result.success(Unit)

        viewModel.signOut()
        advanceUntilIdle()

        assertThat(viewModel.state.value.isSignedOut).isTrue()
        coVerify(exactly = 1) { signOutUseCase.execute() }
    }
}
