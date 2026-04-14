package com.example.cricarena.data.model

data class UserTeamMatch(
    val matchId: String,
    val matchName: String,
    val status: String,
    val totalPoints: Double,
    val rank: Int
)
