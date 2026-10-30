package com.example.caprichometro.friendship

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.application.usecase.friendship.GetIncomingFriendRequestsUseCase
import com.example.caprichometro.application.usecase.friendship.RespondToFriendRequestUseCase
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
            coEvery { userProfileRepository.getUserById(senderWithoutProfile) } returns Result.success(null)
            coEvery { userProfileRepository.getUserById(senderWithProfile) } returns Result.success(validProfile)

            val result = useCase.execute(myId).first()

            assertEquals(1, result.size)
            assertEquals("amigo_valido", result[0].sender.username)
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
            // El usuario borrado no devuelve perfil
            coEvery { userProfileRepository.getUserById(deletedFriendId) } returns Result.success(null)
            // El usuario activo sí
            coEvery { userProfileRepository.getUserById(activeFriendId) } returns Result.success(activeProfile)

            val friends = useCase.execute(myId).first()

            // Debe mostrar solo el activo y omitir el borrado
            assertEquals(1, friends.size)
            assertEquals("amigo_activo", friends[0].username)
        }
    }
}
