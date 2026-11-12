package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.GetFriendsCapricesUseCase
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
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CapriceAudienceVisibilityTest {

    @Test
    fun `isVisibleTo respeta lista vacia por compatibilidad y restringe cuando hay destinatarios`() {
        val legacyCaprice = Caprice(
            id = "legacy",
            userId = "author_1",
            question = "¿Legacy?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = emptyList()
        )
        assertThat(legacyCaprice.isVisibleTo("friend_a")).isTrue()
        assertThat(legacyCaprice.isVisibleTo("")).isFalse()

        val targetedCaprice = Caprice(
            id = "private",
            userId = "author_1",
            question = "¿Solo para A?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = listOf("author_1", "friend_a")
        )
        assertThat(targetedCaprice.isVisibleTo("author_1")).isTrue()
        assertThat(targetedCaprice.isVisibleTo("friend_a")).isTrue()
        assertThat(targetedCaprice.isVisibleTo("friend_b")).isFalse()
    }

    @Test
    fun `GetFriendsCapricesUseCase filtra caprichos privados que no incluyen al usuario actual`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val capriceRepository = mockk<CapriceRepository>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val visibleCaprice = Caprice(
            id = "c1",
            userId = "friend_1",
            question = "¿Para mí?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 100L,
            visibleToUserIds = listOf("friend_1", "me")
        )
        val hiddenCaprice = Caprice(
            id = "c2",
            userId = "friend_1",
            question = "¿Solo para otro?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 200L,
            visibleToUserIds = listOf("friend_1", "other")
        )
        val legacyCaprice = Caprice(
            id = "c3",
            userId = "friend_1",
            question = "¿Antiguo?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 50L,
            visibleToUserIds = emptyList()
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", 20) } returns Result.success(
            listOf(hiddenCaprice, visibleCaprice, legacyCaprice)
        )

        val useCase = GetFriendsCapricesUseCase(getAcceptedFriendsUseCase, capriceRepository)
        val result = useCase.execute("me").getOrThrow()

        assertThat(result.map { it.caprice.id }).containsExactly("c1", "c3")
    }

    @Test
    fun `ObserveFriendCapricesUseCase ignora caprichos en directo no dirigidos al usuario`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val observationRepository = mockk<CapriceObservationRepository>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val hiddenLive = Caprice(
            id = "live_hidden",
            userId = "friend_1",
            question = "¿Oculto?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2000L,
            visibleToUserIds = listOf("friend_1", "other")
        )
        val visibleLive = Caprice(
            id = "live_visible",
            userId = "friend_1",
            question = "¿Visible?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2001L,
            visibleToUserIds = listOf("friend_1", "me")
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        every { observationRepository.observeFriendCaprices("friend_1", 1000L) } returns flowOf(hiddenLive, visibleLive)

        val useCase = ObserveFriendCapricesUseCase(getAcceptedFriendsUseCase, observationRepository)
        val emitted = useCase.execute("me", 1000L).toList()

        assertThat(emitted.map { it.caprice.id }).containsExactly("live_visible")
    }

    @Test
    fun `NotifyFriendCapriceUseCase omite capricho mas reciente oculto y notifica el ultimo visible`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val capriceRepository = mockk<CapriceRepository>()
        val historyRepository = mockk<CapriceNotificationHistoryRepository>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val newerHidden = Caprice(
            id = "c_hidden",
            userId = "friend_1",
            question = "¿Secreto?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 3000L,
            visibleToUserIds = listOf("friend_1", "other")
        )
        val olderVisible = Caprice(
            id = "c_visible",
            userId = "friend_1",
            question = "¿Para ti?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = 2000L,
            visibleToUserIds = listOf("friend_1", "me")
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", 20) } returns Result.success(
            listOf(newerHidden, olderVisible)
        )
        every { historyRepository.getLastNotifiedCapriceId("me", "friend_1") } returns null
        every { historyRepository.getLastNotifiedTimestamp("me", "friend_1") } returns 0L
        every { historyRepository.setLastNotified("me", "friend_1", "c_visible", 2000L) } just runs

        val notified = mutableListOf<String>()
        val useCase = NotifyFriendCapriceUseCase(getAcceptedFriendsUseCase, capriceRepository, historyRepository)
        val items = useCase.checkAndNotifyUnnotifiedFriendCaprices("me") { _, caprice ->
            notified.add(caprice.id)
        }

        assertThat(notified).containsExactly("c_visible")
        assertThat(items.map { it.caprice.id }).containsExactly("c_visible")
        verify(exactly = 1) { historyRepository.setLastNotified("me", "friend_1", "c_visible", 2000L) }
    }

    @Test
    fun `GetNotificationCapriceUseCase deniega acceso si el receptor no esta en visibleToUserIds`() = runTest {
        val capriceDetailsRepository = mockk<CapriceDetailsRepository>()
        val userProfileRepository = mockk<UserProfileRepository>()
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val friend = UserProfile(uid = "friend_1", username = "amigo1")

        val restrictedCaprice = Caprice(
            id = "cap_restricted",
            userId = "friend_1",
            question = "¿Solo para otro?",
            answer1 = "Sí",
            answer2 = "No",
            visibleToUserIds = listOf("friend_1", "other")
        )

        coEvery { capriceDetailsRepository.getCapriceById("cap_restricted") } returns Result.success(restrictedCaprice)
        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))

        val useCase = GetNotificationCapriceUseCase(
            capriceDetailsRepository,
            userProfileRepository,
            getAcceptedFriendsUseCase
        )
        val result = useCase.execute("cap_restricted", "me")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }
}
