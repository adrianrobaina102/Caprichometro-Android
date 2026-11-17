package com.example.caprichometro.domain.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utilidad pura para validar, parsear y formatear el precio opcional en euros de un [Caprice].
 */
object CapricePrice {

    /**
     * Interpreta una cadena introducida por el usuario (admite coma o punto decimal).
     * Una cadena vacía o en blanco devuelve `Result.success(null)`.
     * Cualquier valor no numérico o negativo devuelve `Result.failure(...)`.
     */
    fun parseOptional(raw: String): Result<Double?> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.success(null)

        val normalized = trimmed
            .replace("€", "")
            .replace(" ", "")
            .replace(',', '.')

        val parsed = normalized.toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed < 0.0) {
            return Result.failure(IllegalArgumentException("Precio inválido"))
        }
        return Result.success(parsed)
    }

    /**
     * Formatea un precio en euros según la configuración regional actual.
     */
    fun formatEuros(price: Double, locale: Locale = Locale.getDefault()): String {
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("EUR")
            val hasFraction = (price % 1.0) != 0.0
            minimumFractionDigits = if (hasFraction) 2 else 0
            maximumFractionDigits = 2
        }
        return formatter.format(price)
    }
}
