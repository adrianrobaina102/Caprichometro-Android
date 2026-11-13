package com.example.caprichometro.domain.model

import java.util.UUID

data class Caprice(
    val id: String = "",
    val userId: String = "",
    val type: String = "Compra",
    val question: String = "",
    val answer1: String = "",
    val answer2: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val visibleToUserIds: List<String> = emptyList(),
    val finalDecision: Int? = null,
    val finalDecisionAt: Long? = null
) {
    /**
     * Comprueba si [viewerUserId] tiene permiso para ver este capricho.
     * Un `visibleToUserIds` vacío mantiene compatibilidad con documentos antiguos o de iOS.
     */
    fun isVisibleTo(viewerUserId: String): Boolean {
        if (viewerUserId.isBlank()) return false
        if (userId == viewerUserId) return true
        return visibleToUserIds.isEmpty() || viewerUserId in visibleToUserIds
    }

    class Builder {
        private var id: String = UUID.randomUUID().toString()
        private var userId: String = ""
        private var type: String = "Compra"
        private var question: String = ""
        private var answer1: String = ""
        private var answer2: String = ""
        private var imageUrl: String = ""
        private var timestamp: Long = System.currentTimeMillis()
        private var visibleToUserIds: List<String> = emptyList()
        private var finalDecision: Int? = null
        private var finalDecisionAt: Long? = null

        fun setId(id: String) = apply { this.id = id }
        fun setUserId(userId: String) = apply { this.userId = userId }
        fun setType(type: String) = apply { this.type = type }
        fun setQuestion(question: String) = apply { this.question = question }
        fun setOptions(ans1: String, ans2: String) = apply {
            this.answer1 = ans1
            this.answer2 = ans2
        }
        fun setImageUrl(imageUrl: String) = apply { this.imageUrl = imageUrl }
        fun setTimestamp(timestamp: Long) = apply { this.timestamp = timestamp }
        fun setVisibleToUserIds(userIds: List<String>) = apply {
            this.visibleToUserIds = userIds.filter { it.isNotBlank() }.distinct()
        }
        fun setFinalDecision(decision: Int?, decidedAt: Long? = null) = apply {
            this.finalDecision = decision?.takeIf { it == 1 || it == 2 }
            this.finalDecisionAt = decidedAt
        }

        fun build(): Caprice {
            check(question.isNotBlank()) { "La pregunta es obligatoria" }
            check(answer1.isNotBlank() && answer2.isNotBlank()) { "Ambas opciones son obligatorias" }
            return Caprice(
                id = id,
                userId = userId,
                type = type,
                question = question,
                answer1 = answer1,
                answer2 = answer2,
                imageUrl = imageUrl,
                timestamp = timestamp,
                visibleToUserIds = visibleToUserIds,
                finalDecision = finalDecision,
                finalDecisionAt = finalDecisionAt
            )
        }
    }
}
