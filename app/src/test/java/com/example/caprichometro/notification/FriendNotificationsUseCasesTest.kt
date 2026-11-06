package com.example.caprichometro.notification

import com.example.caprichometro.application.usecase.GetNotificationCapriceUseCase
import com.example.caprichometro.application.usecase.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.ObserveFriendCapricesUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.repository.CapriceDetailsRepository
import com.example.caprichometro.domain.repository.CapriceNotificationHistoryRepository
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FriendNotificationsUseCasesTest {

    private val capriceDetailsRepository = mockk<CapriceDetailsRepository>()
    private val userProfileRepository = mockk<UserProfileRepository>()
    private val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
    private val capriceRepository = mockk<CapriceRepository>()
    private val historyRepository = mockk<CapriceNotificationHistoryRepository>()
    private val observationRepository = mockk<CapriceObservationRepository>()

    private val getNotificationCapriceUseCase = GetNotificationCapriceUseCase(
        capriceDetailsRepository,
        userProfileRepository,
        getAcceptedFriendsUseCase
    )

    private val notifyFriendCapriceUseCase = NotifyFriendCapriceUseCase(
        getAcceptedFriendsUseCase,
        capriceRepository,
        historyRepository
    )

    private val observeFriendCapricesUseCase = ObserveFriendCapricesUseCase(
        getAcceptedFriendsUseCase,
        observationRepository
    )

    @Test
    fun `GetNotificationCapriceUseCase devuelve capricho propio con perfil del usuario`() = runTest {
        val ownCaprice = Caprice(
            id = "cap_1",
            userId = "user_me",
            type = "Compra",
            question = "¿Me compro esta chaqueta?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 1000L
        )

        coEvery { capriceDetailsRepository.getCapriceById("cap_1") } returns Result.success(ownCaprice)
        coEvery { userProfileRepository.getUserById("user_me") } returns Result.success(
            UserProfile(uid = "user_me", username = "mi_usuario")
        )

        val result = getNotificationCapriceUseCase.execute("cap_1", "user_me")

        assertThat(result.isSuccess).isTrue()
        val item = result.getOrNull()
        assertThat(item).isNotNull
        assertThat(item?.caprice?.id).isEqualTo("cap_1")
        assertThat(item?.author?.username).isEqualTo("mi_usuario")
    }

    @Test
    fun `GetNotificationCapriceUseCase devuelve capricho de amigo si es amigo aceptado`() = runTest {
        val friendCaprice = Caprice(
            id = "cap_friend",
            userId = "friend_123",
            type = "Plan",
            question = "¿Salimos hoy?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2000L
        )
        val friendProfile = UserProfile(uid = "friend_123", username = "dani")

        coEvery { capriceDetailsRepository.getCapriceById("cap_friend") } returns Result.success(friendCaprice)
        coEvery { getAcceptedFriendsUseCase.execute("user_me") } returns flowOf(listOf(friendProfile))

        val result = getNotificationCapriceUseCase.execute("cap_friend", "user_me")

        assertThat(result.isSuccess).isTrue()
        val item = result.getOrNull()
        assertThat(item).isNotNull
        assertThat(item?.author?.username).isEqualTo("dani")
        assertThat(item?.caprice?.question).isEqualTo("¿Salimos hoy?")
    }

    @Test
    fun `GetNotificationCapriceUseCase devuelve null si el autor no es amigo aceptado o esta bloqueado`() = runTest {
        val strangerCaprice = Caprice(
            id = "cap_stranger",
            userId = "stranger_999",
            type = "Plan",
            question = "Pregunta privada",
            answer1 = "A",
            answer2 = "B",
            timestamp = 2000L
        )

        coEvery { capriceDetailsRepository.getCapriceById("cap_stranger") } returns Result.success(strangerCaprice)
        coEvery { getAcceptedFriendsUseCase.execute("user_me") } returns flowOf(emptyList())

        val result = getNotificationCapriceUseCase.execute("cap_stranger", "user_me")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun `NotifyFriendCapriceUseCase notifica si el ultimo capricho del amigo no estaba en el historial`() = runTest {
        val friend = UserProfile(uid = "friend_1", username = "maria")
        val caprice = Caprice(
            id = "cap_new",
            userId = "friend_1",
            type = "Compra",
            question = "¿Me compro las botas?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 5000L
        )

        coEvery { getAcceptedFriendsUseCase.execute("user_me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", limit = 1) } returns Result.success(listOf(caprice))
        every { historyRepository.getLastNotifiedCapriceId("user_me", "friend_1") } returns null
        every { historyRepository.getLastNotifiedTimestamp("user_me", "friend_1") } returns 0L
        every { historyRepository.setLastNotified("user_me", "friend_1", "cap_new", 5000L) } returns Unit

        var notifiedCount = 0
        val result = notifyFriendCapriceUseCase.checkAndNotifyUnnotifiedFriendCaprices("user_me") { author, c ->
            notifiedCount++
            assertThat(author.username).isEqualTo("maria")
            assertThat(c.id).isEqualTo("cap_new")
        }

        assertThat(notifiedCount).isEqualTo(1)
        assertThat(result).hasSize(1)
        verify(exactly = 1) { historyRepository.setLastNotified("user_me", "friend_1", "cap_new", 5000L) }
    }

    @Test
    fun `NotifyFriendCapriceUseCase no repite aviso si el capricho ya fue notificado previamente`() = runTest {
        val friend = UserProfile(uid = "friend_1", username = "maria")
        val caprice = Caprice(
            id = "cap_old",
            userId = "friend_1",
            type = "Compra",
            question = "¿Me compro las botas?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 5000L
        )

        coEvery { getAcceptedFriendsUseCase.execute("user_me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", limit = 1) } returns Result.success(listOf(caprice))
        // Ya figura en el historial local
        every { historyRepository.getLastNotifiedCapriceId("user_me", "friend_1") } returns "cap_old"
        every { historyRepository.getLastNotifiedTimestamp("user_me", "friend_1") } returns 5000L

        var notifiedCount = 0
        val result = notifyFriendCapriceUseCase.checkAndNotifyUnnotifiedFriendCaprices("user_me") { _, _ ->
            notifiedCount++
        }

        assertThat(notifiedCount).isEqualTo(0)
        assertThat(result).isEmpty()
        verify(exactly = 0) { historyRepository.setLastNotified(any(), any(), any(), any()) }
    }

    @Test
    fun `ObserveFriendCapricesUseCase emite caprichos en directo para amigos aceptados`() = runTest {
        val friend = UserProfile(uid = "friend_1", username = "carlos")
        val liveCaprice = Caprice(
            id = "cap_live",
            userId = "friend_1",
            type = "Compra",
            question = "¿Qué cenamos?",
            answer1 = "Pizza",
            answer2 = "Sushi",
            timestamp = 10000L
        )

        coEvery { getAcceptedFriendsUseCase.execute("user_me") } returns flowOf(listOf(friend))
        every { observationRepository.observeFriendCaprices("friend_1", 9000L) } returns flowOf(liveCaprice)

        val feedItem = observeFriendCapricesUseCase.execute("user_me", 9000L).first()

        assertThat(feedItem.caprice.id).isEqualTo("cap_live")
        assertThat(feedItem.author.username).isEqualTo("carlos")
    }
}
