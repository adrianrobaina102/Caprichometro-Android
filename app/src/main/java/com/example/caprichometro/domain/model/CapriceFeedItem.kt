package com.example.caprichometro.domain.model

data class CapriceFeedItem(
    val caprice: Caprice,
    val author: UserProfile,
    val canOpenAuthorProfile: Boolean = true,
    val approximateDistanceMeters: Double? = null
)
