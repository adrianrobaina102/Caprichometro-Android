package com.example.caprichometro.caprice

import com.example.caprichometro.infrastructure.ui.formatPollStatus
import com.example.caprichometro.infrastructure.ui.formatRelativeTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeedTimeFormattingTest {

    @Test
    fun `formatPollStatus devuelve minutos restantes cuando la duda sigue activa`() {
        val baseTimestamp = 1_000_000_000L // Duda creada
        // 5 minutos = 300_000 ms. El cierre es en baseTimestamp + 300_000L

        // Quedan 4 minutos y 30 segundos (270.000 ms) -> Redondea hacia arriba a 5 min
        val now4m30s = baseTimestamp + 30_000L
        assertThat(formatPollStatus(baseTimestamp, now4m30s)).isEqualTo("Quedan 5 min")

        // Quedan 2 minutos exactos (120.000 ms)
        val now2m = baseTimestamp + 180_000L
        assertThat(formatPollStatus(baseTimestamp, now2m)).isEqualTo("Quedan 2 min")

        // Quedan 40 segundos -> Muestra "Queda 1 min"
        val now40s = baseTimestamp + 260_000L
        assertThat(formatPollStatus(baseTimestamp, now40s)).isEqualTo("Queda 1 min")
    }

    @Test
    fun `formatPollStatus devuelve cerro ahora o cerro hace X cuando ya pasaron los 5 minutos`() {
        val baseTimestamp = 1_000_000_000L
        val closeTimestamp = baseTimestamp + 300_000L

        // Cerró hace 20 segundos
        val nowJustClosed = closeTimestamp + 20_000L
        assertThat(formatPollStatus(baseTimestamp, nowJustClosed)).isEqualTo("Cerró ahora")

        // Cerró hace 3 minutos
        val nowClosed3m = closeTimestamp + (3 * 60_000L)
        assertThat(formatPollStatus(baseTimestamp, nowClosed3m)).isEqualTo("Cerró hace 3 min")

        // Cerró hace 2 horas
        val nowClosed2h = closeTimestamp + (2 * 3600_000L)
        assertThat(formatPollStatus(baseTimestamp, nowClosed2h)).isEqualTo("Cerró hace 2h")

        // Cerró hace 3 días
        val nowClosed3d = closeTimestamp + (3 * 86400_000L)
        assertThat(formatPollStatus(baseTimestamp, nowClosed3d)).isEqualTo("Cerró hace 3d")
    }

    @Test
    fun `formatRelativeTime devuelve antiguedad respecto a la creacion`() {
        val created = 1_000_000_000L

        assertThat(formatRelativeTime(created, created + 10_000L)).isEqualTo("Ahora")
        assertThat(formatRelativeTime(created, created + (5 * 60_000L))).isEqualTo("5m")
        assertThat(formatRelativeTime(created, created + (2 * 3600_000L))).isEqualTo("2h")
        assertThat(formatRelativeTime(created, created + (4 * 86400_000L))).isEqualTo("4d")
    }
}
