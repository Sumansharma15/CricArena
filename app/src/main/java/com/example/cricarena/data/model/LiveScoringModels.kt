package com.example.cricarena.data.model

data class LivePlayer(
    val id: String,
    val name: String,
    val role: String,
    val teamName: String,
    val runs: Int = 0,
    val balls: Int = 0,
    val catches: Int = 0,
    val isOut: Boolean = false
)

data class LiveTeam(
    val name: String,
    val totalRuns: Int = 0,
    val wickets: Int = 0,
    val ballsDelivered: Int = 0
)

enum class BallEventType {
    RUN,
    WIDE,
    NO_BALL,
    BYE,
    WICKET,
    CATCH
}

data class BallEvent(
    val type: BallEventType,
    val playerId: String,
    val teamName: String,
    val runs: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
