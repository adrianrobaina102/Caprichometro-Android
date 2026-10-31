package com.example.caprichometro.domain.model

object UsernameRules {

    fun normalize(username: String): String {
        return username.trim().removePrefix("@").lowercase()
    }

    fun isValid(username: String): Boolean {
        val regex = "^[a-zA-Z0-9_]{3,15}$".toRegex()
        return regex.matches(username)
    }
}
