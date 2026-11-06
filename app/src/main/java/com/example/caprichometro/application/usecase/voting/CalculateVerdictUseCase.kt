package com.example.caprichometro.application.usecase.voting

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.StandardVerdictStrategy
import com.example.caprichometro.domain.model.VerdictResult
import com.example.caprichometro.domain.model.VerdictStrategy
import com.example.caprichometro.domain.model.Vote

class CalculateVerdictUseCase(
    private val strategy: VerdictStrategy = StandardVerdictStrategy()
) {
    fun execute(caprice: Caprice, votes: List<Vote>): VerdictResult {
        val option1Votes = votes.count { it.option == 1 }
        val option2Votes = votes.count { it.option == 2 }
        return strategy.calculate(
            option1Votes = option1Votes,
            option2Votes = option2Votes,
            answer1Text = caprice.answer1,
            answer2Text = caprice.answer2
        )
    }
}
