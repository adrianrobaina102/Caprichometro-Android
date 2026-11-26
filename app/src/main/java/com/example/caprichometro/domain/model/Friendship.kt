package com.example.caprichometro.domain.model

import java.util.Date

enum class FriendshipStatus (val value: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined")
}
data class Friendship (
    val id: String,
    val memberIDs: List<String>,
    val requestedBy: String,
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)