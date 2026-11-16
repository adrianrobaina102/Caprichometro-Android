package com.example.caprichometro.infrastructure.ui.profile

import com.example.caprichometro.domain.model.Caprice

/**
 * Categorías temáticas del perfil. Fuente única de clasificación para las tarjetas de
 * Estadísticas y para la hoja «Ver todas», de modo que los recuentos nunca difieran.
 */
internal enum class ProfileCategory(val label: String, val emoji: String) {
    COMPRAS("Compras", "\uD83D\uDECD\uFE0F"),
    PLANES("Planes", "\uD83C\uDF89"),
    ROPA("Ropa", "\uD83D\uDC55"),
    OTROS("Otros", "\u2728");

    companion object {
        fun of(type: String): ProfileCategory = when {
            type.contains("compra", ignoreCase = true) -> COMPRAS
            type.contains("plan", ignoreCase = true) -> PLANES
            type.contains("ropa", ignoreCase = true) -> ROPA
            else -> OTROS
        }

        fun countsOf(caprices: List<Caprice>): Map<ProfileCategory, Int> =
            entries.associateWith { category -> caprices.count { of(it.type) == category } }
    }
}
