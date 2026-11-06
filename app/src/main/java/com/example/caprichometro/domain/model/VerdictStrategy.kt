package com.example.caprichometro.domain.model

data class VerdictResult(
    val winningOption: Int?, // 1, 2 o null en caso de empate o sin votos
    val option1Percentage: Float,
    val option2Percentage: Float,
    val totalVotes: Int,
    val headline: String,
    val subtitle: String
)

/**
 * Patrón Estrategia (Strategy Pattern) para calcular el veredicto final de un capricho.
 */
interface VerdictStrategy {
    fun calculate(
        option1Votes: Int,
        option2Votes: Int,
        answer1Text: String,
        answer2Text: String
    ): VerdictResult
}

class StandardVerdictStrategy : VerdictStrategy {
    override fun calculate(
        option1Votes: Int,
        option2Votes: Int,
        answer1Text: String,
        answer2Text: String
    ): VerdictResult {
        val total = option1Votes + option2Votes
        if (total == 0) {
            return VerdictResult(
                winningOption = null,
                option1Percentage = 0f,
                option2Percentage = 0f,
                totalVotes = 0,
                headline = "Sin respuestas",
                subtitle = "Tus amigos te han dejado con la duda."
            )
        }

        val pct1 = (option1Votes.toFloat() / total) * 100f
        val pct2 = (option2Votes.toFloat() / total) * 100f

        return when {
            option1Votes > option2Votes -> VerdictResult(
                winningOption = 1,
                option1Percentage = pct1,
                option2Percentage = pct2,
                totalVotes = total,
                headline = "¡Tus amigos han hablado!",
                subtitle = "Ganó «$answer1Text» con un ${pct1.toInt()}% de los votos."
            )
            option2Votes > option1Votes -> VerdictResult(
                winningOption = 2,
                option1Percentage = pct1,
                option2Percentage = pct2,
                totalVotes = total,
                headline = "¡Tus amigos han hablado!",
                subtitle = "Ganó «$answer2Text» con un ${pct2.toInt()}% de los votos."
            )
            else -> VerdictResult(
                winningOption = null,
                option1Percentage = 50f,
                option2Percentage = 50f,
                totalVotes = total,
                headline = "¡Empate técnico (50/50)!",
                subtitle = "Tus amigos están divididos. ¡Sigue tu intuición!"
            )
        }
    }
}
