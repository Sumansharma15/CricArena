package com.example.cricarena.data.model

data class Match(
    val id: String,
    val title: String = "",
    val teamA: String,
    val teamB: String,
    val matchType: String,
    val category: MatchCategory,
    val status: MatchStatus,
    val startTime: String,
    /** Short line for list UI, e.g. live score summary */
    val scoreSummary: String? = null
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
