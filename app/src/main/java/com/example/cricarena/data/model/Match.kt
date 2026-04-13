package com.example.cricarena.data.model

data class Match(
    val id: String,
    val teamA: String,
    val teamB: String,
    val matchType: String,
    val category: MatchCategory,
    val status: MatchStatus,
    val startTime: String
)

enum class MatchCategory {
    MANUAL,
    AUTO
}

enum class MatchStatus {
    LIVE,
    UPCOMING,
    COMPLETED
}
