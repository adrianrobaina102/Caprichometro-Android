package com.example.caprichometro.domain.model

data class ProgressiveFeedBatch(
    val items: List<CapriceFeedItem>,
    val isFromCache: Boolean,
    val isComplete: Boolean
)
