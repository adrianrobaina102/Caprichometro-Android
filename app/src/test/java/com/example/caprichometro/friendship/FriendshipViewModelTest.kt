package com.example.caprichometro.friendship

import android.util.Log
import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetIncomingFriendRequestsUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.RespondToFriendRequestUseCase
import com.example.caprichometro.application.usecase.friendship.SearchUserByUsernameUseCase
import com.example.caprichometro.application.usecase.friendship.SendFriendRequestUseCase
import com.example.caprichometro.domain.model.IncomingFriendRequest
import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.infrastructure.ui.friendship.FriendshipViewModel
import java.util.Date
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendshipViewModelTest {

    private val searchUserUseCase: SearchUserByUsernameUseCase = mockk()
    private val sendFriendRequestUseCase: SendFriendRequestUseCase = mockk()
    private val getIncomingRequestsUseCase: GetIncomingFriendRequestsUseCase = mockk()
    private val respondToRequestUseCase: RespondToFriendRequestUseCase = mockk()
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase = mockk()
    private val removeFriendUseCase: RemoveFriendUseCase = mockk()
    private val blockUserUseCase: BlockUserUseCase = mockk()
    private val userProfileRepository: CacheableUserProfileRepository = mockk(relaxed = true)

    private interface CacheableUserProfileRepository : UserProfileRepository, ProfileCacheControl

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: FriendshipViewModel

    private val me = UserProfile(uid = "me", username = "adrian")
    private val ana = UserProfile(uid = "u_ana", username = "ana")
    private val carlos = UserProfile(uid = "u_carlos", username = "carlos")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        viewModel = FriendshipViewModel(
            searchUserByUsernameUseCase = searchUserUseCase,
            sendFriendRequestUseCase = sendFriendRequestUseCase,
            getIncomingFriendRequestsUseCase = getIncomingRequestsUseCase,
            respondToFriendRequestUseCase = respondToRequestUseCase,
            getAcceptedFriendsUseCase = getAcceptedFriendsUseCase,
            removeFriendUseCase = removeFriendUseCase,
            blockUserUseCase = blockUserUseCase,
            userProfileRepository = userProfileRepository
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `listenToIncomingRequests ignora userId vacio y no duplica suscripcion activa`() = runTest {
        viewModel.listenToIncomingRequests("  ")
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 0) { getIncomingRequestsUseCase.execute(any()) }

        val requestsFlow = MutableStateFlow(
            listOf(
                IncomingFriendRequest(
                    friendshipId = "f1",
                    sender = ana,
                    createdAt = Date(1000L)
                )
            )
        )
        val friendsFlow = MutableStateFlow(listOf(carlos))
        coEvery { userProfileRepository.getUserById(me.uid) } returns Result.success(me)
        every { getIncomingRequestsUseCase.execute(me.uid) } returns requestsFlow
        every { getAcceptedFriendsUseCase.execute(me.uid) } returns friendsFlow

        viewModel.listenToIncomingRequests(me.uid)
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.currentUsername).isEqualTo("adrian")
        assertThat(viewModel.state.value.incomingRequests).hasSize(1)
        assertThat(viewModel.state.value.friends).containsExactly(carlos)
        assertThat(viewModel.state.value.hasLoadedFriends).isTrue()

        viewModel.listenToIncomingRequests(me.uid)
        verify(exactly = 1) { getIncomingRequestsUseCase.execute(me.uid) }

        viewModel.stopListening()
        assertThat(viewModel.state.value.friends).isEmpty()
        assertThat(viewModel.state.value.hasLoadedFriends).isFalse()
        assertThat(viewModel.state.value.currentUsername).isNull()
    }

    @Test
    fun `listenToIncomingRequests captura errores en los flujos de solicitudes y amigos`() = runTest {
        coEvery { userProfileRepository.getUserById(me.uid) } returns Result.success(me)
        every { getIncomingRequestsUseCase.execute(me.uid) } returns flow {
            throw IllegalStateException("fallo red")
        }
        every { getAcceptedFriendsUseCase.execute(me.uid) } returns flow {
            throw IllegalStateException("fallo amigos")
        }

        viewModel.listenToIncomingRequests(me.uid)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage).isNotNull()
        viewModel.clearError()
        assertThat(viewModel.state.value.errorMessage).isNull()
    }

    @Test
    fun `refreshProfiles invalida cache y actualiza propio perfil, amigos y solicitantes`() = runTest {
        val requestsFlow = MutableStateFlow(
            listOf(
                IncomingFriendRequest(
                    friendshipId = "f1",
                    sender = ana,
                    createdAt = Date(1000L)
                )
            )
        )
        val friendsFlow = MutableStateFlow(listOf(carlos))
        coEvery { userProfileRepository.getUserById(me.uid) } returns Result.success(me)
        every { getIncomingRequestsUseCase.execute(me.uid) } returns requestsFlow
        every { getAcceptedFriendsUseCase.execute(me.uid) } returns friendsFlow

        viewModel.listenToIncomingRequests(me.uid)
        testDispatcher.scheduler.runCurrent()

        val meUpdated = me.copy(username = "adrian_new")
        val carlosUpdated = carlos.copy(username = "carlos_new")
        val anaUpdated = ana.copy(username = "ana_new")
        coEvery { userProfileRepository.getUserById(me.uid) } returns Result.success(meUpdated)
        coEvery { userProfileRepository.getUserById(carlos.uid) } returns Result.success(carlosUpdated)
        coEvery { userProfileRepository.getUserById(ana.uid) } returns Result.success(anaUpdated)

        viewModel.refreshProfiles(me.uid)
        testDispatcher.scheduler.runCurrent()

        verify { userProfileRepository.invalidateUser(me.uid) }
        verify { userProfileRepository.invalidateUser(carlos.uid) }
        verify { userProfileRepository.invalidateUser(ana.uid) }
        assertThat(viewModel.state.value.currentUsername).isEqualTo("adrian_new")
        assertThat(viewModel.state.value.friends.first().username).isEqualTo("carlos_new")
        assertThat(viewModel.state.value.incomingRequests.first().sender.username).isEqualTo("ana_new")
        viewModel.stopListening()
    }

    @Test
    fun `updateSearchQuery con enlace de invitacion y openInviteForUsername lanzan busqueda`() = runTest {
        coEvery { searchUserUseCase.execute("ana") } returns Result.success(ana)
        coEvery { searchUserUseCase.execute("carlos") } returns Result.success(null)

        viewModel.updateSearchQuery("https://caprichometro.app/invite/ana")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.searchQuery).isEqualTo("ana")
        assertThat(viewModel.state.value.userFound).isEqualTo(ana)

        viewModel.openInviteForUsername("@Carlos")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.searchQuery).isEqualTo("carlos")
        assertThat(viewModel.state.value.userFound).isNull()
        assertThat(viewModel.state.value.errorMessage).isEqualTo("No se ha encontrado ningún usuario con ese nombre.")
    }

    @Test
    fun `searchUser maneja fallo y query en blanco`() = runTest {
        viewModel.searchUser()
        coVerify(exactly = 0) { searchUserUseCase.execute(any()) }

        coEvery { searchUserUseCase.execute("pedro") } returns Result.failure(RuntimeException("Sin conexión"))
        viewModel.updateSearchQuery("pedro")
        viewModel.searchUser()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.errorMessage).isEqualTo("Sin conexión")
    }

    @Test
    fun `sendRequest gestiona exito, error de bloqueo y evita autoenvio`() = runTest {
        coEvery { userProfileRepository.getUserById(me.uid) } returns Result.success(me)
        every { getIncomingRequestsUseCase.execute(me.uid) } returns MutableStateFlow(emptyList())
        every { getAcceptedFriendsUseCase.execute(me.uid) } returns MutableStateFlow(emptyList())
        viewModel.listenToIncomingRequests(me.uid)
        testDispatcher.scheduler.runCurrent()

        coEvery { searchUserUseCase.execute("adrian") } returns Result.success(me)
        viewModel.updateSearchQuery("adrian")
        viewModel.searchUser()
        testDispatcher.scheduler.runCurrent()
        viewModel.sendRequest(me.uid)
        coVerify(exactly = 0) { sendFriendRequestUseCase.execute(any(), any()) }

        coEvery { searchUserUseCase.execute("ana") } returns Result.success(ana)
        coEvery { sendFriendRequestUseCase.execute(me.uid, ana.uid) } returns Result.failure(IllegalStateException("Usuario bloqueado"))
        viewModel.updateSearchQuery("ana")
        viewModel.searchUser()
        testDispatcher.scheduler.runCurrent()
        viewModel.sendRequest(me.uid)
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.isFriendRequestBlocked).isTrue()
        assertThat(viewModel.state.value.errorMessage).isEqualTo("Usuario bloqueado")

        viewModel.updateSearchQuery("ana")
        viewModel.searchUser()
        testDispatcher.scheduler.runCurrent()
        coEvery { sendFriendRequestUseCase.execute(me.uid, ana.uid) } returns Result.success(Unit)
        viewModel.sendRequest(me.uid)
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.requestSentSuccess).isTrue()
        assertThat(viewModel.state.value.userFound).isNull()
        assertThat(viewModel.state.value.searchQuery).isEmpty()
        viewModel.clearRequestSentSuccess()
        assertThat(viewModel.state.value.requestSentSuccess).isFalse()
        viewModel.stopListening()
    }

    @Test
    fun `acceptRequest y declineRequest delegan en RespondToFriendRequestUseCase y limpian estado`() = runTest {
        coEvery { respondToRequestUseCase.execute("f1", true) } returns Result.success(Unit)
        coEvery { respondToRequestUseCase.execute("f2", false) } returns Result.failure(RuntimeException("Error al responder"))

        viewModel.acceptRequest("f1")
        viewModel.declineRequest("f2")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { respondToRequestUseCase.execute("f1", true) }
        coVerify(exactly = 1) { respondToRequestUseCase.execute("f2", false) }
        assertThat(viewModel.state.value.respondingRequestIds).isEmpty()
        assertThat(viewModel.state.value.errorMessage).isEqualTo("Error al responder")
    }

    @Test
    fun `removeFriend y blockUser actualizan listas y estado de bloqueo`() = runTest {
        val requestsFlow = MutableStateFlow(
            listOf(
                IncomingFriendRequest(
                    friendshipId = "f1",
                    sender = ana,
                    createdAt = Date(1000L)
                )
            )
        )
        val friendsFlow = MutableStateFlow(listOf(carlos, ana))
        coEvery { userProfileRepository.getUserById(me.uid) } returns Result.success(me)
        every { getIncomingRequestsUseCase.execute(me.uid) } returns requestsFlow
        every { getAcceptedFriendsUseCase.execute(me.uid) } returns friendsFlow
        viewModel.listenToIncomingRequests(me.uid)
        testDispatcher.scheduler.runCurrent()

        coEvery { searchUserUseCase.execute("ana") } returns Result.success(ana)
        viewModel.updateSearchQuery("ana")
        viewModel.searchUser()
        testDispatcher.scheduler.runCurrent()

        coEvery { removeFriendUseCase.execute(me.uid, carlos.uid) } returns Result.success(Unit)
        viewModel.removeFriend(me.uid, carlos.uid)
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.state.value.friends).containsExactly(ana)

        coEvery { blockUserUseCase.execute(me.uid, ana.uid) } returns Result.success(Unit)
        viewModel.blockUser(me.uid, ana.uid)
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.state.value.friends).isEmpty()
        assertThat(viewModel.state.value.incomingRequests).isEmpty()
        assertThat(viewModel.state.value.isFriendRequestBlocked).isTrue()

        coEvery { removeFriendUseCase.execute(me.uid, carlos.uid) } returns Result.failure(RuntimeException("Fallo al borrar"))
        viewModel.removeFriend(me.uid, carlos.uid)
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.state.value.errorMessage).isEqualTo("Fallo al borrar")
        viewModel.stopListening()
    }

    @Test
    fun `updateSearchQuery con 3+ letras carga sugerencias tras debounce y selectSuggestedUsername busca`() = runTest {
        coEvery { searchUserUseCase.searchSuggestions("ana") } returns Result.success(listOf("ana", "anabel"))
        coEvery { searchUserUseCase.execute("anabel") } returns Result.success(ana.copy(username = "anabel"))

        viewModel.updateSearchQuery("ana")
        testDispatcher.scheduler.advanceTimeBy(350)
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.usernameSuggestions).containsExactly("ana", "anabel")

        viewModel.selectSuggestedUsername("anabel")
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.usernameSuggestions).isEmpty()
        assertThat(viewModel.state.value.searchQuery).isEqualTo("anabel")
        assertThat(viewModel.state.value.userFound?.username).isEqualTo("anabel")
    }

    @Test
    fun `sendFriendRequestToUser gestiona exito y error directo desde General`() = runTest {
        coEvery { sendFriendRequestUseCase.execute(me.uid, ana.uid) } returns Result.failure(RuntimeException("Ya enviada"))
        viewModel.sendFriendRequestToUser(me.uid, ana.uid)
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.directRequestError).isEqualTo("Ya enviada")
        viewModel.clearDirectRequestError()
        assertThat(viewModel.state.value.directRequestError).isNull()

        coEvery { sendFriendRequestUseCase.execute(me.uid, ana.uid) } returns Result.success(Unit)
        viewModel.sendFriendRequestToUser(me.uid, ana.uid)
        testDispatcher.scheduler.runCurrent()

        assertThat(viewModel.state.value.directRequestSentUids).contains(ana.uid)
    }
}
