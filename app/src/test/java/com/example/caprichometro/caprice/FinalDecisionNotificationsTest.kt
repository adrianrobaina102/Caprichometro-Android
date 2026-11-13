package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.NotifyFriendCapriceUseCase
import com.example.caprichometro.application.usecase.ObserveFriendCapricesUseCase
import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.UserProfile
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

class FinalDecisionNotificationsTest {

    @Test
    fun `checkAndNotifyFriendFinalDecisions notifica solo decisiones visibles y nuevas`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val capriceRepository = mockk<CapriceRepository>()
        val historyRepository = mockk<CapriceNotificationHistoryRepository>()
        val friend = UserProfile(uid = "friend_1", username = "carlos")

        val hiddenDecided = Caprice(
            id = "c_hidden",
            userId = "friend_1",
            question = "¿Privado?",
            answer1 = "A",
            answer2 = "B",
            timestamp = 1000L,
            visibleToUserIds = listOf("friend_1", "other"),
            finalDecision = 1,
            finalDecisionAt = 5000L
        )
        val visibleDecided = Caprice(
            id = "c_visible",
            userId = "friend_1",
            question = "¿Público?",
            answer1 = "A",
            answer2 = "B",
            timestamp = 900L,
            visibleToUserIds = listOf("friend_1", "me"),
            finalDecision = 2,
            finalDecisionAt = 4000L
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        coEvery { capriceRepository.getCaprices("friend_1", 20) } returns Result.success(
            listOf(hiddenDecided, visibleDecided)
        )
        every { historyRepository.getLastNotifiedDecisionCapriceId("me", "friend_1") } returns null
        every { historyRepository.getLastNotifiedDecisionTimestamp("me", "friend_1") } returns 0L
        every { historyRepository.setLastNotifiedDecision("me", "friend_1", "c_visible", 4000L) } just runs

        val notifiedIds = mutableListOf<String>()
        val useCase = NotifyFriendCapriceUseCase(getAcceptedFriendsUseCase, capriceRepository, historyRepository)
        val result = useCase.checkAndNotifyFriendFinalDecisions("me") { _, caprice ->
            notifiedIds.add(caprice.id)
        }

        assertThat(notifiedIds).containsExactly("c_visible")
        assertThat(result.map { it.caprice.id }).containsExactly("c_visible")
        verify(exactly = 1) { historyRepository.setLastNotifiedDecision("me", "friend_1", "c_visible", 4000L) }
    }

    @Test
    fun `observeFinalDecisions filtra decisiones por audiencia visible`() = runTest {
        val getAcceptedFriendsUseCase = mockk<GetAcceptedFriendsUseCase>()
        val observationRepository = mockk<CapriceObservationRepository>()
        val friend = UserProfile(uid = "friend_1", username = "carlos")

        val hidden = Caprice(
            id = "d_hidden",
            userId = "friend_1",
            question = "P1",
            answer1 = "A",
            answer2 = "B",
            visibleToUserIds = listOf("friend_1", "other"),
            finalDecision = 1,
            finalDecisionAt = 3000L
        )
        val visible = Caprice(
            id = "d_visible",
            userId = "friend_1",
            question = "P2",
            answer1 = "A",
            answer2 = "B",
            visibleToUserIds = listOf("friend_1", "me"),
            finalDecision = 2,
            finalDecisionAt = 3500L
        )

        every { getAcceptedFriendsUseCase.execute("me") } returns flowOf(listOf(friend))
        every { observationRepository.observeFriendFinalDecisions("friend_1", 2000L) } returns flowOf(hidden, visible)

        val useCase = ObserveFriendCapricesUseCase(getAcceptedFriendsUseCase, observationRepository)
        val emitted = useCase.observeFinalDecisions("me", 2000L).toList()

        assertThat(emitted.map { it.caprice.id }).containsExactly("d_visible")
    }
}
