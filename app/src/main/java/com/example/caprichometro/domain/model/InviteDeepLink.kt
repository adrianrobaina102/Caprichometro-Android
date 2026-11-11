package com.example.caprichometro.domain.model

/**
 * Utilidad de dominio pura (sin dependencias del framework Android) para construir
 * y extraer invitaciones de amistad mediante Deep Links (`caprichometro://invite/{username}`),
 * enlaces web (`https://caprichometro.app/invite/{username}`) o texto compartido.
 */
object InviteDeepLink {

    const val SCHEME = "caprichometro"
    const val HOST_INVITE = "invite"
    const val WEB_HOST = "caprichometro.app"

    private val customUriRegex = Regex(
        pattern = """caprichometro://invite(?:/([a-zA-Z0-9_]{3,20})|\?(?:[^#\s]*&)?(?:username|user)=@?([a-zA-Z0-9_]{3,20}))""",
        option = RegexOption.IGNORE_CASE
    )

    private val webUrlRegex = Regex(
        pattern = """https?://(?:www\.)?caprichometro\.app/invite(?:/@?([a-zA-Z0-9_]{3,20})|\?(?:[^#\s]*&)?(?:username|user)=@?([a-zA-Z0-9_]{3,20}))""",
        option = RegexOption.IGNORE_CASE
    )

    private val mentionRegex = Regex(
        pattern = """(?:como\s+@|^@)([a-zA-Z0-9_]{3,20})\b""",
        option = RegexOption.IGNORE_CASE
    )

    fun buildCustomUri(username: String): String {
        val clean = UsernameRules.normalize(username)
        return "$SCHEME://$HOST_INVITE/$clean"
    }

    fun buildWebUrl(username: String): String {
        val clean = UsernameRules.normalize(username)
        return "https://$WEB_HOST/$HOST_INVITE/$clean"
    }

    fun buildShareMessage(username: String?): String {
        val clean = username?.let { UsernameRules.normalize(it) }?.takeIf { UsernameRules.isValid(it) }
        return if (clean != null) {
            "¡Únete a mi círculo en Caprichómetro y ayúdame a decidir mis caprichos en 5 minutos! ⏱️\n\n" +
                "Pulsa para agregarme directo:\n" +
                "${buildWebUrl(clean)}\n" +
                "(${buildCustomUri(clean)} o búscame como @$clean)"
        } else {
            "¡Únete a Caprichómetro y ayúdame a decidir mis caprichos en 5 minutos!"
        }
    }

    /**
     * Extrae y valida un `@username` desde un URI de Deep Link, un enlace HTTPS de invitación
     * o un mensaje completo pegado en el buscador. Devuelve `null` si no contiene una invitación válida.
     */
    fun extractUsername(rawInput: String?): String? {
        if (rawInput.isNullOrBlank()) return null
        val trimmed = rawInput.trim()

        customUriRegex.find(trimmed)?.let { match ->
            val candidate = (match.groupValues[1].ifEmpty { match.groupValues[2] })
            val normalized = UsernameRules.normalize(candidate)
            if (UsernameRules.isValid(normalized)) return normalized
        }

        webUrlRegex.find(trimmed)?.let { match ->
            val candidate = (match.groupValues[1].ifEmpty { match.groupValues[2] })
            val normalized = UsernameRules.normalize(candidate)
            if (UsernameRules.isValid(normalized)) return normalized
        }

        if (trimmed.contains("caprichómetro", ignoreCase = true) ||
            trimmed.contains("caprichometro", ignoreCase = true)
        ) {
            mentionRegex.find(trimmed)?.let { match ->
                val normalized = UsernameRules.normalize(match.groupValues[1])
                if (UsernameRules.isValid(normalized)) return normalized
            }
        }

        return null
    }
}
