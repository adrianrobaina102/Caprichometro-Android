package com.example.caprichometro.friendship

import com.example.caprichometro.application.usecase.friendship.BlockUserUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetIncomingFriendRequestsUseCase
import com.example.caprichometro.application.usecase.friendship.RemoveFriendUseCase
import com.example.caprichometro.application.usecase.friendship.RespondToFriendRequestUseCase
import com.example.caprichometro.application.usecase.friendship.SearchUserByUsernameUseCase
import com.example.caprichometro.application.usecase.friendship.SendFriendRequestUseCase
import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date

class FriendshipUseCasesTest {

    private val friendshipRepository: FriendshipRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()

    @Nested
    inner class SearchUserTests {
        private val useCase = SearchUserByUsernameUseCase(friendshipRepository)

        @Test
        fun `normaliza el username quitando arroba y pasando a minusculas antes de buscar`() = runTest {
            val expectedProfile = UserProfile(uid = "uid_1", username = "adrian_dev")
            coEvery { friendshipRepository.searchUserByUsername("adrian_dev") } returns Result.success(expectedProfile)

            val result = useCase.execute("  @Adrian_Dev  ")

            assertTrue(result.isSuccess)
            assertEquals("adrian_dev", result.getOrNull()?.username)
            coVerify(exactly = 1) { friendshipRepository.searchUserByUsername("adrian_dev") }
        }

        @Test
        fun `falla si el username queda vacio tras normalizar`() = runTest {
            val result = useCase.execute("   @   ")

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { friendshipRepository.searchUserByUsername(any()) }
        }
    }

    @Nested
    inner class SendFriendRequestTests {
        private val useCase = SendFriendRequestUseCase(friendshipRepository)

        @Test
        fun `impide enviarse una solicitud de amistad a uno mismo`() = runTest {
            val result = useCase.execute(currentUserId = "user_1", targetUserId = "user_1")

            assertTrue(result.isFailure)
            assertEquals("No puedes enviarte una solicitud a ti mismo", result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { friendshipRepository.sendFriendRequest(any()) }
        }

        @Test
        fun `crea la amistad con ID ordenado alfabeticamente minUid_maxUid y estado PENDING`() = runTest {
            coEvery { friendshipRepository.sendFriendRequest(any()) } returns Result.success(Unit)

            val result = useCase.execute(currentUserId = "user_z", targetUserId = "user_a")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                friendshipRepository.sendFriendRequest(match {
                    it.id == "user_a_user_z" &&
                        it.requestedBy == "user_z" &&
                        it.status == FriendshipStatus.PENDING &&
                        it.memberIDs.containsAll(listOf("user_z", "user_a"))
                })
            }
        }
    }

    @Nested
    inner class IncomingRequestsTests {
        private val useCase = GetIncomingFriendRequestsUseCase(friendshipRepository, userProfileRepository)

        @Test
        fun `filtra y emite solo solicitudes pendientes donde el usuario es el receptor`() = runTest {
            val myId = "user_me"
            val senderId = "user_sender"
            val friendship = Friendship(
                id = "${senderId}_${myId}",
                memberIDs = listOf(senderId, myId),
                requestedBy = senderId,
                status = FriendshipStatus.PENDING,
                createdAt = Date(),
                updatedAt = Date()
            )

            val senderProfile = UserProfile(uid = senderId, username = "amigo_sender")
            coEvery { friendshipRepository.getFriendships(myId) } returns flowOf(listOf(friendship))
            coEvery { friendshipRepository.isBlocked(myId, senderId) } returns Result.success(false)
            coEvery { userProfileRepository.getUserById(senderId) } returns Result.success(senderProfile)

            val result = useCase.execute(myId).first()
            assertEquals(1, result.size)
            assertEquals("amigo_sender", result[0].sender.username)
        }

        @Test
        fun `omite la solicitud si el perfil del remitente no existe segun la norma de common_md`() = runTest {
            val myId = "user_me"
            val senderWithoutProfile = "user_deleted"
            val senderWithProfile = "user_valid"

            val friendship1 = Friendship(
                id = "${senderWithoutProfile}_${myId}",
                memberIDs = listOf(senderWithoutProfile, myId),
                requestedBy = senderWithoutProfile,
                status = FriendshipStatus.PENDING,
                createdAt = Date(1000L),
                updatedAt = Date(1000L)
            )

            val friendship2 = Friendship(
                id = "${senderWithProfile}_${myId}",
                memberIDs = listOf(senderWithProfile, myId),
                requestedBy = senderWithProfile,
                status = FriendshipStatus.PENDING,
                createdAt = Date(2000L),
                updatedAt = Date(2000L)
            )

            val validProfile = UserProfile(uid = senderWithProfile, username = "amigo_valido")

            coEvery { friendshipRepository.getFriendships(myId) } returns flowOf(listOf(friendship1, friendship2))
            coEvery { friendshipRepository.isBlocked(myId, any()) } returns Result.success(false)
            coEvery { userProfileRepository.getUserById(senderWithoutProfile) } returns Result.success(null)
            coEvery { userProfileRepository.getUserById(senderWithProfile) } returns Result.success(validProfile)

            val result = useCase.execute(myId).first()

            assertEquals(1, result.size)
            assertEquals("amigo_valido", result[0].sender.username)
        }

        @Test
        fun `omite solicitudes entrantes si existe un bloqueo entre los usuarios`() = runTest {
            val myId = "user_me"
            val blockedSenderId = "user_blocked"
            val friendship = Friendship(
                id = "${blockedSenderId}_${myId}",
                memberIDs = listOf(blockedSenderId, myId),
                requestedBy = blockedSenderId,
                status = FriendshipStatus.PENDING,
                createdAt = Date(),
                updatedAt = Date()
            )

            coEvery { friendshipRepository.getFriendships(myId) } returns flowOf(listOf(friendship))
            coEvery { friendshipRepository.isBlocked(myId, blockedSenderId) } returns Result.success(true)

            val result = useCase.execute(myId).first()

            assertTrue(result.isEmpty())
            coVerify(exactly = 0) { userProfileRepository.getUserById(blockedSenderId) }
        }
    }

    @Nested
    inner class RespondToRequestTests {
        private val useCase = RespondToFriendRequestUseCase(friendshipRepository)

        @Test
        fun `aceptar solicitud actualiza el estado a ACCEPTED`() = runTest {
            coEvery { friendshipRepository.updateFriendshipStatus("friendship_123", FriendshipStatus.ACCEPTED) } returns Result.success(Unit)

            val result = useCase.execute("friendship_123", accept = true)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { friendshipRepository.updateFriendshipStatus("friendship_123", FriendshipStatus.ACCEPTED) }
        }

        @Test
        fun `rechazar solicitud actualiza el estado a DECLINED`() = runTest {
            coEvery { friendshipRepository.updateFriendshipStatus("friendship_123", FriendshipStatus.DECLINED) } returns Result.success(Unit)

            val result = useCase.execute("friendship_123", accept = false)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { friendshipRepository.updateFriendshipStatus("friendship_123", FriendshipStatus.DECLINED) }
        }
    }

    @Nested
    inner class AcceptedFriendsTests {
        private val useCase = GetAcceptedFriendsUseCase(friendshipRepository, userProfileRepository)

        @Test
        fun `emite la lista de amigos aceptados con sus perfiles de usuario`() = runTest {
            val myId = "user_me"
            val friendId = "user_friend"
            val friendship = Friendship(
                id = "${myId}_${friendId}",
                memberIDs = listOf(myId, friendId),
                requestedBy = myId,
                status = FriendshipStatus.ACCEPTED,
                createdAt = Date(),
                updatedAt = Date()
            )
            val friendProfile = UserProfile(uid = friendId, username = "buen_amigo")

            coEvery { friendshipRepository.getFriendships(myId) } returns flowOf(listOf(friendship))
            coEvery { friendshipRepository.isBlocked(myId, friendId) } returns Result.success(false)
            coEvery { userProfileRepository.getUserById(friendId) } returns Result.success(friendProfile)

            val friends = useCase.execute(myId).first()
            assertEquals(1, friends.size)
            assertEquals("buen_amigo", friends[0].username)
        }

        @Test
        fun `omite amigos cuya cuenta haya sido borrada y no tengan perfil`() = runTest {
            val myId = "user_me"
            val deletedFriendId = "user_deleted"
            val activeFriendId = "user_active"

            val friendshipDeleted = Friendship(
                id = "${myId}_${deletedFriendId}",
                memberIDs = listOf(myId, deletedFriendId),
                requestedBy = myId,
                status = FriendshipStatus.ACCEPTED,
                createdAt = Date(1000L),
                updatedAt = Date(1000L)
            )

            val friendshipActive = Friendship(
                id = "${myId}_${activeFriendId}",
                memberIDs = listOf(myId, activeFriendId),
                requestedBy = myId,
                status = FriendshipStatus.ACCEPTED,
                createdAt = Date(2000L),
                updatedAt = Date(2000L)
            )

            val activeProfile = UserProfile(uid = activeFriendId, username = "amigo_activo")

            coEvery { friendshipRepository.getFriendships(myId) } returns flowOf(listOf(friendshipDeleted, friendshipActive))
            coEvery { friendshipRepository.isBlocked(myId, any()) } returns Result.success(false)
            coEvery { userProfileRepository.getUserById(deletedFriendId) } returns Result.success(null)
            coEvery { userProfileRepository.getUserById(activeFriendId) } returns Result.success(activeProfile)

            val friends = useCase.execute(myId).first()

            assertEquals(1, friends.size)
            assertEquals("amigo_activo", friends[0].username)
        }

        @Test
        fun `omite amigos aceptados si existe un bloqueo entre los usuarios`() = runTest {
            val myId = "user_me"
            val blockedFriendId = "user_blocked"
            val friendship = Friendship(
                id = "${myId}_${blockedFriendId}",
                memberIDs = listOf(myId, blockedFriendId),
                requestedBy = myId,
                status = FriendshipStatus.ACCEPTED,
                createdAt = Date(),
                updatedAt = Date()
            )

            coEvery { friendshipRepository.getFriendships(myId) } returns flowOf(listOf(friendship))
            coEvery { friendshipRepository.isBlocked(myId, blockedFriendId) } returns Result.success(true)

            val friends = useCase.execute(myId).first()

            assertTrue(friends.isEmpty())
            coVerify(exactly = 0) { userProfileRepository.getUserById(blockedFriendId) }
        }
    }

    @Nested
    inner class RemoveFriendTests {
        private val useCase = RemoveFriendUseCase(friendshipRepository)

        @Test
        fun `elimina la amistad aceptada llamando al repositorio`() = runTest {
            coEvery { friendshipRepository.removeFriendship("user_1", "user_2") } returns Result.success(Unit)

            val result = useCase.execute("user_1", "user_2")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { friendshipRepository.removeFriendship("user_1", "user_2") }
        }

        @Test
        fun `falla si el usuario intenta eliminarse a si mismo`() = runTest {
            val result = useCase.execute("user_1", "user_1")

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { friendshipRepository.removeFriendship(any(), any()) }
        }
    }

    @Nested
    inner class BlockUserTests {
        private val useCase = BlockUserUseCase(friendshipRepository)

        @Test
        fun `bloquea al usuario llamando al repositorio`() = runTest {
            coEvery { friendshipRepository.blockUser("user_1", "user_2") } returns Result.success(Unit)

            val result = useCase.execute("user_1", "user_2")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { friendshipRepository.blockUser("user_1", "user_2") }
        }

        @Test
        fun `impide bloquearse a uno mismo`() = runTest {
            val result = useCase.execute("user_1", "user_1")

            assertTrue(result.isFailure)
            assertEquals("No puedes bloquearte a ti mismo", result.exceptionOrNull()?.message)
            coVerify(exactly = 0) { friendshipRepository.blockUser(any(), any()) }
        }
    }
}
