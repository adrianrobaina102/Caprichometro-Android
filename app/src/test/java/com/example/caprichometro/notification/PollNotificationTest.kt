package com.example.caprichometro.notification

import com.example.caprichometro.infrastructure.notification.PollNotificationHelper
import com.example.caprichometro.infrastructure.notification.PollNotificationScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PollNotificationTest {

    @Test
    fun `la duracion de la encuesta debe ser de exactamente 5 minutos`() {
        // 5 minutos * 60 segundos * 1000 milisegundos = 300_000 ms
        val expectedMillis = 5 * 60 * 1000L
        assertThat(PollNotificationScheduler.POLL_DURATION_MILLIS).isEqualTo(expectedMillis)
    }

    @Test
    fun `calculateTriggerTime debe sumar exactamente 5 minutos al timestamp original`() {
        val initialTimestamp = 1_700_000_000_000L
        val triggerTime = PollNotificationScheduler.calculateTriggerTime(initialTimestamp)

        assertThat(triggerTime).isEqualTo(initialTimestamp + 300_000L)
    }

    @Test
    fun `isPollExpired debe devolver false si aun quedan minutos por transcurrir`() {
        val initialTimestamp = 1_700_000_000_000L
        // Simula 2 minutos transcurridos (quedan 3)
        val currentTime = initialTimestamp + (2 * 60 * 1000L)

        val expired = PollNotificationScheduler.isPollExpired(initialTimestamp, currentTime)
        assertThat(expired).isFalse()
    }

    @Test
    fun `isPollExpired debe devolver true cuando se alcanzan o superan los 5 minutos`() {
        val initialTimestamp = 1_700_000_000_000L
        // Simula exactamente 5 minutos transcurridos
        val exactTime = initialTimestamp + (5 * 60 * 1000L)
        assertThat(PollNotificationScheduler.isPollExpired(initialTimestamp, exactTime)).isTrue()

        // Simula 6 minutos transcurridos
        val laterTime = initialTimestamp + (6 * 60 * 1000L)
        assertThat(PollNotificationScheduler.isPollExpired(initialTimestamp, laterTime)).isTrue()
    }

    @Test
    fun `constantes del canal de notificaciones y extras son consistentes`() {
        assertThat(PollNotificationHelper.CHANNEL_ID).isEqualTo("caprice_poll_channel")
        assertThat(PollNotificationHelper.CHANNEL_NAME).isEqualTo("Votaciones de Caprichos")
        assertThat(PollNotificationHelper.NOTIFICATION_ID).isEqualTo(1001)
        assertThat(PollNotificationHelper.CREATED_NOTIFICATION_ID).isEqualTo(1002)
        assertThat(PollNotificationHelper.EXTRA_NAVIGATE_TO).isEqualTo("EXTRA_NAVIGATE_TO")
        assertThat(PollNotificationHelper.EXTRA_CAPRICE_ID).isEqualTo("EXTRA_CAPRICE_ID")
        assertThat(PollNotificationHelper.EXTRA_CAPRICE_QUESTION).isEqualTo("EXTRA_CAPRICE_QUESTION")
    }
}
