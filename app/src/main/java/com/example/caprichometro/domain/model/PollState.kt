package com.example.caprichometro.domain.model

/**
 * Patrón Estado (State Pattern) para modelar el ciclo de vida de una encuesta activa/votada/expirada.
 */
sealed interface PollState {
    val remainingMillis: Long

    // Amigo viendo duda activa (< 5 min) sin haber votado aún
    data class Active(
        override val remainingMillis: Long
    ) : PollState

    // Amigo que ya votó por la opción 1 o 2 (ve porcentajes en tiempo real)
    data class Voted(
        val selectedOption: Int,
        override val remainingMillis: Long,
        val option1Percentage: Float,
        val option2Percentage: Float,
        val totalVotes: Int
    ) : PollState

    // El autor del capricho viendo los votos de sus amigos en tiempo real
    data class Author(
        override val remainingMillis: Long,
        val option1Percentage: Float,
        val option2Percentage: Float,
        val totalVotes: Int
    ) : PollState

    // Encuesta expirada (>= 5 min) con su veredicto calculado
    data class Expired(
        val verdictResult: VerdictResult,
        override val remainingMillis: Long = 0L
    ) : PollState
}
