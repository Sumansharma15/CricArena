package com.example.cricarena.data.model

data class FantasyPlayer(
    val id: String,
    val name: String,
    val role: String,
    val team: String = "",
    val runs: Int = 0,
    val balls: Int = 0,
    val wickets: Int = 0,
    val catches: Int = 0,
    val fantasyPoints: Double = 0.0
)
