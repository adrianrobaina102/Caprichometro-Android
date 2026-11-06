package com.example.caprichometro.domain.model

data class Vote(
    val capriceId: String,
    val voterId: String,
    val option: Int, // 1 para answer1, 2 para answer2
    val timestamp: Long = System.currentTimeMillis()
)
