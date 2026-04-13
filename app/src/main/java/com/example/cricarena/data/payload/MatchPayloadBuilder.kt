package com.example.cricarena.data.payload

import com.example.cricarena.data.model.MatchPlayer

/**
 * Single place for stable player IDs and Firestore payloads used when saving a match.
 */
object MatchPayloadBuilder {

    fun stablePlayerId(teamName: String, indexOneBased: Int, playerName: String): String =
        "${teamName.lowercase()}_${indexOneBased}_${playerName.lowercase().replace(" ", "_")}"

    data class BuiltPayload(
        val playersPayload: List<Map<String, Any>>,
        val playerStatsTemplate: List<Map<String, Any>>,
        val teamAPlayers: List<Map<String, String>>,
        val teamBPlayers: List<Map<String, String>>
    )

    fun build(teamA: String, teamB: String, players: List<MatchPlayer>): BuiltPayload {
        val playersPayload = players.mapIndexed { index, player ->
            mapOf(
                "id" to stablePlayerId(player.teamName, index + 1, player.name),
                "name" to player.name,
                "role" to player.role,
                "teamName" to player.teamName
            )
        }
        val playerStatsTemplate = playersPayload.map { p ->
            mapOf(
                "playerId" to p["id"].toString(),
                "playerName" to p["name"].toString(),
                "teamName" to p["teamName"].toString(),
                "role" to p["role"].toString(),
                "runs" to 0,
                "wickets" to 0,
                "catches" to 0,
                "balls" to 0,
                "points" to 0.0
            )
        }
        val teamAPlayers = players.filter { it.teamName == teamA }
            .map { mapOf("name" to it.name, "role" to it.role, "teamName" to it.teamName) }
        val teamBPlayers = players.filter { it.teamName == teamB }
            .map { mapOf("name" to it.name, "role" to it.role, "teamName" to it.teamName) }
        return BuiltPayload(playersPayload, playerStatsTemplate, teamAPlayers, teamBPlayers)
    }

    fun emptyLiveScoreMap(): Map<String, Int> = mapOf(
        "runs" to 0,
        "wickets" to 0,
        "balls" to 0
    )
}
