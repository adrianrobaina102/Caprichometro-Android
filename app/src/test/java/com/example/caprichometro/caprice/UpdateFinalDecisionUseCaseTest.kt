package com.example.caprichometro.caprice

import com.example.caprichometro.application.usecase.UpdateFinalDecisionUseCase
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UpdateFinalDecisionUseCaseTest {

    private val capriceRepository = mockk<CapriceRepository>()

    @Test
    fun `autor confirma decision tras cierre y actualiza modelo y repositorio`() = runTest {
        val createdAt = 1_000_000L
        val now = createdAt + PollNotificationScheduler.POLL_DURATION_MILLIS + 10_000L
        val caprice = Caprice(
            id = "cap_1",
            userId = "author_1",
            question = "¿Me compro las zapatillas?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = createdAt
        )
        coEvery { capriceRepository.updateFinalDecision("cap_1", 1, now) } returns Result.success(Unit)

        val useCase = UpdateFinalDecisionUseCase(capriceRepository) { now }
        val result = useCase.execute(caprice, "author_1", 1)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.finalDecision).isEqualTo(1)
        assertThat(result.getOrNull()?.finalDecisionAt).isEqualTo(now)
        coVerify(exactly = 1) { capriceRepository.updateFinalDecision("cap_1", 1, now) }
    }

    @Test
    fun `rechaza si no es el autor o si la votacion sigue activa`() = runTest {
        val createdAt = 1_000_000L
        val activeNow = createdAt + 60_000L
        val caprice = Caprice(
            id = "cap_1",
            userId = "author_1",
            question = "¿Me compro las zapatillas?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = createdAt
        )

        val activeUseCase = UpdateFinalDecisionUseCase(capriceRepository) { activeNow }
        assertThat(activeUseCase.execute(caprice, "author_1", 1).isFailure).isTrue()
        assertThat(activeUseCase.execute(caprice, "friend_1", 1).isFailure).isTrue()
        coVerify(exactly = 0) { capriceRepository.updateFinalDecision(any(), any(), any()) }
    }

    @Test
    fun `rechaza si ya tenia decision final confirmada`() = runTest {
        val createdAt = 1_000_000L
        val now = createdAt + PollNotificationScheduler.POLL_DURATION_MILLIS + 10_000L
        val decidedCaprice = Caprice(
            id = "cap_1",
            userId = "author_1",
            question = "¿Me compro las zapatillas?",
            answer1 = "Sí",
            answer2 = "No",
            timestamp = createdAt,
            finalDecision = 2,
            finalDecisionAt = now - 1000L
        )

        val useCase = UpdateFinalDecisionUseCase(capriceRepository) { now }
        assertThat(useCase.execute(decidedCaprice, "author_1", 1).isFailure).isTrue()
        coVerify(exactly = 0) { capriceRepository.updateFinalDecision(any(), any(), any()) }
    }
}
