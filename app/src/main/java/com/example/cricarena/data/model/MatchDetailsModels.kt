package com.example.cricarena.data.model

data class LeaderboardEntry(
    val userName: String,
    val points: Double,
    val rank: Int
)

data class PlayerPointsEntry(
    val playerName: String,
    val points: Double
)
