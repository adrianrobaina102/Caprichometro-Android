package com.example.caprichometro.domain.model

object UsernameRules {

    fun normalize(username: String): String {
        return username.trim().removePrefix("@").lowercase()
    }

    fun isValid(username: String): Boolean {
        val normalized = normalize(username)
        val regex = "^[a-z0-9_]{3,20}$".toRegex()
        return regex.matches(normalized)
    }
}
