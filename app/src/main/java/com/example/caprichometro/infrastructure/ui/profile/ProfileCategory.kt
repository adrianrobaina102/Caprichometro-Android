package com.example.caprichometro.infrastructure.ui.profile

import androidx.annotation.StringRes
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.Caprice

/**
 * Categorías temáticas del perfil. Fuente única de clasificación para las tarjetas de
 * Estadísticas y para la hoja «Ver todas», de modo que los recuentos nunca difieran.
 */
internal enum class ProfileCategory(
    val label: String,
    @param:StringRes val labelRes: Int,
    val emoji: String
) {
    COMPRAS("Compras", R.string.category_compras, "\uD83D\uDECD\uFE0F"),
    PLANES("Planes", R.string.category_planes, "\uD83C\uDF89"),
    ROPA("Ropa", R.string.category_ropa, "\uD83D\uDC55"),
    OTROS("Otros", R.string.category_otros, "\u2728");

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
