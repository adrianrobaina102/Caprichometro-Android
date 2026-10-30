package com.example.caprichometro.domain.model

import java.util.UUID

data class Caprice(
    val id: String = "",
    val userId: String = "",
    val type: String = "Compra",
    val question: String = "",
    val answer1: String = "",
    val answer2: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    class Builder {
        private var id: String = UUID.randomUUID().toString()
        private var userId: String = ""
        private var type: String = "Compra"
        private var question: String = ""
        private var answer1: String = ""
        private var answer2: String = ""
        private var timestamp: Long = System.currentTimeMillis()

        fun setId(id: String) = apply { this.id = id }
        fun setUserId(userId: String) = apply { this.userId = userId }
        fun setType(type: String) = apply { this.type = type }
        fun setQuestion(question: String) = apply { this.question = question }
        fun setOptions(ans1: String, ans2: String) = apply {
            this.answer1 = ans1
            this.answer2 = ans2
        }
        fun setTimestamp(timestamp: Long) = apply { this.timestamp = timestamp }

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
                timestamp = timestamp
            )
        }
    }
}
