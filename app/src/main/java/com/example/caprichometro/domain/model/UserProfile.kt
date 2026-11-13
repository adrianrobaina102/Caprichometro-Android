package com.example.caprichometro.domain.model

import java.util.Date

data class UserProfile(
    val uid: String,
    val username: String,
    val createdAt: Date = Date(),
    val bio: String = "",
    val photoUrl: String = ""
)
