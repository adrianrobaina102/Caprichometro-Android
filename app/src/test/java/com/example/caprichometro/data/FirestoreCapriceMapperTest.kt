package com.example.caprichometro.data

import android.util.Log
import com.example.caprichometro.infrastructure.data.toCapriceOrNull
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Date

class FirestoreCapriceMapperTest {

    @Test
    fun `toCapriceOrNull mapea campos validos normaliza arrays y respeta decision final`() {
        val snap = mockk<DocumentSnapshot>()
        every { snap.id } returns "cap_100"
        every { snap.get("timestamp") } returns 1_700_000_000_000L
        every { snap.getString("question") } returns "  ¿Me pillo estas zapas?  "
        every { snap.getString("answer1") } returns " Sí, de cabeza "
        every { snap.getString("answer2") } returns " Mejor ahorra "
        every { snap.get("visibleToUserIds") } returns listOf(" u2 ", "", "u3", "u2", 99)
        every { snap.get("finalDecision") } returns 2L
        every { snap.get("finalDecisionAt") } returns 1_700_000_400_000L
        every { snap.getString("nearbyCell") } returns " v1_2608_3525 "
        every { snap.getString("locationCell") } returns "   "
        every { snap.getString("userId") } returns "author_1"
        every { snap.getString("type") } returns "Compra"
        every { snap.getString("imageUrl") } returns "data:image/webp;base64,AAA="
        every { snap.get("price") } returns 29.99

        val caprice = snap.toCapriceOrNull("TestTag")

        assertThat(caprice).isNotNull
        assertThat(caprice!!.id).isEqualTo("cap_100")
        assertThat(caprice.userId).isEqualTo("author_1")
        assertThat(caprice.question).isEqualTo("¿Me pillo estas zapas?")
        assertThat(caprice.answer1).isEqualTo("Sí, de cabeza")
        assertThat(caprice.answer2).isEqualTo("Mejor ahorra")
        assertThat(caprice.timestamp).isEqualTo(1_700_000_000_000L)
        assertThat(caprice.visibleToUserIds).containsExactly("u2", "u3")
        assertThat(caprice.finalDecision).isEqualTo(2)
        assertThat(caprice.finalDecisionAt).isEqualTo(1_700_000_400_000L)
        assertThat(caprice.nearbyCell).isEqualTo("v1_2608_3525")
        assertThat(caprice.locationCell).isNull()
        assertThat(caprice.price).isEqualTo(29.99)
    }

    @Test
    fun `toCapriceOrNull convierte Firebase Timestamp e ignora finalDecision fuera de rango`() {
        val snap = mockk<DocumentSnapshot>()
        val ts = Timestamp(Date(1_710_000_123_000L))
        val decisionTs = Timestamp(Date(1_710_000_500_000L))
        every { snap.id } returns "cap_ts"
        every { snap.get("timestamp") } returns ts
        every { snap.getString("question") } returns "¿Plan de cena?"
        every { snap.getString("answer1") } returns "Pizza"
        every { snap.getString("answer2") } returns "Sushi"
        every { snap.get("visibleToUserIds") } returns null
        every { snap.get("finalDecision") } returns 5
        every { snap.get("finalDecisionAt") } returns decisionTs
        every { snap.getString("nearbyCell") } returns null
        every { snap.getString("locationCell") } returns "v1_10_20"
        every { snap.getString("userId") } returns null
        every { snap.getString("type") } returns null
        every { snap.getString("imageUrl") } returns null
        every { snap.get("price") } returns null

        val caprice = snap.toCapriceOrNull("TestTag")

        assertThat(caprice).isNotNull
        assertThat(caprice!!.timestamp).isEqualTo(1_710_000_123_000L)
        assertThat(caprice.finalDecision).isNull()
        assertThat(caprice.finalDecisionAt).isEqualTo(1_710_000_500_000L)
        assertThat(caprice.type).isEqualTo("Compra")
        assertThat(caprice.locationCell).isEqualTo("v1_10_20")
        assertThat(caprice.price).isNull()
    }

    @Test
    fun `toCapriceOrNull devuelve null si falta pregunta u opciones o si lanza excepcion`() {
        val incomplete = mockk<DocumentSnapshot>()
        every { incomplete.id } returns "cap_bad"
        every { incomplete.get("timestamp") } returns 1_700_000_000_000L
        every { incomplete.getString("question") } returns "   "
        every { incomplete.getString("answer1") } returns "A"
        every { incomplete.getString("answer2") } returns "B"

        assertThat(incomplete.toCapriceOrNull("TestTag")).isNull()

        val corrupt = mockk<DocumentSnapshot>()
        every { corrupt.id } returns "cap_err"
        every { corrupt.get("timestamp") } throws IllegalStateException("corrupt snapshot")

        mockkStatic(Log::class)
        try {
            every { Log.e(any(), any(), any()) } returns 0
            assertThat(corrupt.toCapriceOrNull("TestTag")).isNull()
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
