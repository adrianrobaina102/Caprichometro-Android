package com.example.caprichometro.profile

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.infrastructure.ui.profile.ProfileCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProfileCategoryTest {

    private fun caprice(id: String, type: String) = Caprice(
        id = id,
        userId = "u1",
        type = type,
        question = "¿$id?",
        answer1 = "Sí",
        answer2 = "No",
        timestamp = 1L
    )

    @Test
    fun `classifies types ignoring case and falls back to Otros`() {
        assertThat(ProfileCategory.of("Compra")).isEqualTo(ProfileCategory.COMPRAS)
        assertThat(ProfileCategory.of("COMPRAS")).isEqualTo(ProfileCategory.COMPRAS)
        assertThat(ProfileCategory.of("Plan")).isEqualTo(ProfileCategory.PLANES)
        assertThat(ProfileCategory.of("ropa")).isEqualTo(ProfileCategory.ROPA)
        assertThat(ProfileCategory.of("")).isEqualTo(ProfileCategory.OTROS)
        assertThat(ProfileCategory.of("Comida")).isEqualTo(ProfileCategory.OTROS)
    }

    @Test
    fun `countsOf adds up to the total and includes empty categories`() {
        val caprices = listOf(
            caprice("a", "Compra"),
            caprice("b", "Compra"),
            caprice("c", "Plan"),
            caprice("d", "Viaje")
        )

        val counts = ProfileCategory.countsOf(caprices)

        assertThat(counts[ProfileCategory.COMPRAS]).isEqualTo(2)
        assertThat(counts[ProfileCategory.PLANES]).isEqualTo(1)
        assertThat(counts[ProfileCategory.ROPA]).isEqualTo(0)
        assertThat(counts[ProfileCategory.OTROS]).isEqualTo(1)
        assertThat(counts.values.sum()).isEqualTo(caprices.size)
    }
}
