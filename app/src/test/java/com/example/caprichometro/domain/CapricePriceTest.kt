package com.example.caprichometro.domain

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapricePrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class CapricePriceTest {

    @Test
    fun `parseOptional admite vacio coma o punto decimal y rechaza negativos o texto`() {
        assertThat(CapricePrice.parseOptional("").getOrNull()).isNull()
        assertThat(CapricePrice.parseOptional("   ").getOrNull()).isNull()
        assertThat(CapricePrice.parseOptional("24,99").getOrNull()).isEqualTo(24.99)
        assertThat(CapricePrice.parseOptional("24.99").getOrNull()).isEqualTo(24.99)
        assertThat(CapricePrice.parseOptional("0").getOrNull()).isEqualTo(0.0)
        assertThat(CapricePrice.parseOptional("15 €").getOrNull()).isEqualTo(15.0)

        assertThat(CapricePrice.parseOptional("-1.50").isFailure).isTrue()
        assertThat(CapricePrice.parseOptional("abc").isFailure).isTrue()
    }

    @Test
    fun `formatEuros formatea en euros y Builder descarta precios invalidos`() {
        val formattedDecimal = CapricePrice.formatEuros(24.99, Locale.forLanguageTag("es-ES"))
        assertThat(formattedDecimal).contains("24,99").contains("€")

        val capriceWithNegative = Caprice.Builder()
            .setUserId("u1")
            .setQuestion("¿Lo compro?")
            .setOptions("Sí", "No")
            .setPrice(-10.0)
            .build()
        assertThat(capriceWithNegative.price).isNull()

        val capriceWithValid = Caprice.Builder()
            .setUserId("u1")
            .setQuestion("¿Lo compro?")
            .setOptions("Sí", "No")
            .setPrice(39.95)
            .build()
        assertThat(capriceWithValid.price).isEqualTo(39.95)
    }
}
