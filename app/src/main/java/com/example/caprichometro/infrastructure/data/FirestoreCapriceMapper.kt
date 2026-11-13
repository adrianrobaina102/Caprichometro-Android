package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Parsea un [DocumentSnapshot] de Firestore a [Caprice], devolviendo `null` si faltan campos obligatorios.
 */
internal fun DocumentSnapshot.toCapriceOrNull(logTag: String): Caprice? {
    return try {
        val rawTimestamp = get("timestamp")
        val parsedTimestamp = when (rawTimestamp) {
            is Number -> rawTimestamp.toLong()
            is Timestamp -> rawTimestamp.toDate().time
            is String -> rawTimestamp.toLongOrNull() ?: 0L
            else -> 0L
        }

        val question = getString("question")?.trim().orEmpty()
        val answer1 = getString("answer1")?.trim().orEmpty()
        val answer2 = getString("answer2")?.trim().orEmpty()
        if (question.isEmpty() || answer1.isEmpty() || answer2.isEmpty()) {
            return null
        }

        val visibleToUserIds = (get("visibleToUserIds") as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotBlank) }
            ?.distinct()
            .orEmpty()

        val finalDecision = (get("finalDecision") as? Number)?.toInt()?.takeIf { it == 1 || it == 2 }
        val finalDecisionAt = when (val rawDecisionAt = get("finalDecisionAt")) {
            is Number -> rawDecisionAt.toLong()
            is Timestamp -> rawDecisionAt.toDate().time
            else -> null
        }

        Caprice(
            id = id,
            userId = getString("userId") ?: "",
            type = getString("type") ?: "Compra",
            question = question,
            answer1 = answer1,
            answer2 = answer2,
            imageUrl = getString("imageUrl").orEmpty(),
            timestamp = parsedTimestamp,
            visibleToUserIds = visibleToUserIds,
            finalDecision = finalDecision,
            finalDecisionAt = finalDecisionAt
        )
    } catch (e: Exception) {
        Log.e(logTag, "Documento caprice inválido $id: ${e.message}", e)
        null
    }
}
