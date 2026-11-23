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
    val finalDecisionAt: Long? = null,
    val nearbyCell: String? = null,
    val locationCell: String? = null,
    val price: Double? = null,
    val hasImage: Boolean = imageUrl.isNotBlank()
) {
    /**
     * Comprueba si [viewerUserId] tiene permiso para ver este capricho.
     * Un `nearbyCell` no vacío lo hace público para cualquier usuario autenticado en General.
     * `locationCell` solo aporta ubicación aproximada en Amigos y nunca abre visibilidad pública.
     * Un `visibleToUserIds` vacío mantiene compatibilidad con documentos antiguos o de iOS.
     */
    fun isVisibleTo(viewerUserId: String): Boolean {
        if (viewerUserId.isBlank()) return false
        if (userId == viewerUserId) return true
        if (!nearbyCell.isNullOrBlank()) return true
        return visibleToUserIds.isEmpty() || viewerUserId in visibleToUserIds
    }

    /**
     * Comprueba si [viewerUserId] tiene permiso para ver este capricho en Amigos y notificaciones
     * de amistad, respetando estrictamente `visibleToUserIds` incluso si también se publicó en General.
     */
    fun isVisibleInFriendsTo(viewerUserId: String): Boolean {
        if (viewerUserId.isBlank()) return false
        if (userId == viewerUserId) return true
        return visibleToUserIds.isEmpty() || viewerUserId in visibleToUserIds
    }

    /**
     * Celda aproximada usada para calcular distancia tanto en Amigos como en General.
     */
    fun distanceCell(): String? =
        locationCell?.trim()?.takeIf { it.isNotEmpty() }
            ?: nearbyCell?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Celda efectiva para mostrar distancia en Amigos, usando como respaldo opcional la celda
     * aproximada del autor cuando el capricho antiguo/iOS aún no incluye `locationCell`.
     */
    fun effectiveDistanceCell(authorLocationCell: String? = null): String? =
        distanceCell() ?: authorLocationCell?.trim()?.takeIf { it.isNotEmpty() }

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
        private var nearbyCell: String? = null
        private var locationCell: String? = null
        private var price: Double? = null
        private var hasImage: Boolean = false

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
        fun setNearbyCell(nearbyCell: String?) = apply {
            this.nearbyCell = nearbyCell?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun setLocationCell(locationCell: String?) = apply {
            this.locationCell = locationCell?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun setPrice(price: Double?) = apply {
            this.price = price?.takeIf { it.isFinite() && it >= 0.0 }
        }
        fun setHasImage(hasImage: Boolean) = apply { this.hasImage = hasImage }

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
                finalDecisionAt = finalDecisionAt,
                nearbyCell = nearbyCell,
                locationCell = locationCell,
                price = price,
                hasImage = hasImage || imageUrl.isNotBlank()
            )
        }
    }
}
