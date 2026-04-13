package com.example.cricarena.data.scoring

import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.round

class FantasyScoringManager(
    private val firestore: FirebaseFirestore = FirebaseUtils.firestore
) {

    fun calculateAndStoreResults(
        matchId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (matchId.isBlank()) {
            onResult(false, "Match id is required.")
            return
        }

        FirebaseUtils.matchesCollection().document(matchId).get()
            .addOnSuccessListener { matchDoc ->
                val scoringRules = matchDoc.get("scoringRules") as? Map<*, *>
                val runPoint = (scoringRules?.get("runs") as? Number)?.toDouble() ?: 1.0
                val wicketPoint = (scoringRules?.get("wickets") as? Number)?.toDouble() ?: 25.0
                val catchPoint = (scoringRules?.get("catch") as? Number)?.toDouble() ?: 8.0

                fetchPlayerStats(matchId) { statsMap, statsError ->
                    if (statsError != null) {
                        onResult(false, statsError)
                        return@fetchPlayerStats
                    }

                    FirebaseUtils.teamsCollection()
                        .whereEqualTo("matchId", matchId)
                        .get()
                        .addOnSuccessListener { teamSnapshot ->
                            val teamDocs = teamSnapshot.documents
                            if (teamDocs.isEmpty()) {
                                onResult(false, "No teams found for this match.")
                                return@addOnSuccessListener
                            }

                            val calculated = teamDocs.map { teamDoc ->
                                val players = teamDoc.get("players") as? List<*> ?: emptyList<Any>()
                                val captainId = teamDoc.getString("captainId").orEmpty()
                                val viceCaptainId = teamDoc.getString("viceCaptainId").orEmpty()

                                var teamTotal = 0.0
                                players.forEach { playerRaw ->
                                    val playerMap = playerRaw as? Map<*, *> ?: return@forEach
                                    val playerId = playerMap["id"]?.toString().orEmpty()
                                    val playerName = playerMap["name"]?.toString().orEmpty()
                                    val key = if (playerId.isNotBlank()) {
                                        "id:$playerId"
                                    } else {
                                        "name:${playerName.lowercase()}"
                                    }
                                    val stats = statsMap[key] ?: PlayerStats()
                                    val base = (stats.runs * runPoint) +
                                        (stats.wickets * wicketPoint) +
                                        (stats.catches * catchPoint)
                                    val multiplier = when {
                                        captainId.isNotBlank() && captainId == playerId -> 2.0
                                        viceCaptainId.isNotBlank() && viceCaptainId == playerId -> 1.5
                                        (playerMap["isCaptain"] as? Boolean) == true -> 2.0
                                        (playerMap["isViceCaptain"] as? Boolean) == true -> 1.5
                                        else -> 1.0
                                    }
                                    teamTotal += (base * multiplier)
                                }

                                TeamScore(
                                    teamDocId = teamDoc.id,
                                    totalPoints = round(teamTotal * 10.0) / 10.0
                                )
                            }.sortedByDescending { it.totalPoints }

                            val ranked = calculated.mapIndexed { index, team ->
                                team.copy(rank = index + 1)
                            }

                            val batch = firestore.batch()
                            ranked.forEach { item ->
                                val teamRef = FirebaseUtils.teamsCollection().document(item.teamDocId)
                                batch.update(
                                    teamRef,
                                    mapOf(
                                        "totalPoints" to item.totalPoints,
                                        "rank" to item.rank,
                                        "status" to "COMPLETED",
                                        "evaluatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }

                            // Store global player points breakdown for match details screen.
                            statsMap.forEach { (key, stats) ->
                                val basePoints = (stats.runs * runPoint) +
                                    (stats.wickets * wicketPoint) +
                                    (stats.catches * catchPoint)
                                val playerName = key.removePrefix("name:").removePrefix("id:")
                                val docRef = FirebaseUtils.matchesCollection()
                                    .document(matchId)
                                    .collection("playerPoints")
                                    .document(safeDocId(playerName))
                                batch.set(
                                    docRef,
                                    mapOf(
                                        "playerName" to playerName,
                                        "runs" to stats.runs,
                                        "wickets" to stats.wickets,
                                        "catches" to stats.catches,
                                        "points" to (round(basePoints * 10.0) / 10.0),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }

                            FirebaseUtils.matchesCollection().document(matchId).let { matchRef ->
                                batch.update(
                                    matchRef,
                                    mapOf(
                                        "status" to "COMPLETED",
                                        "lastScoredAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }

                            batch.commit()
                                .addOnSuccessListener { onResult(true, null) }
                                .addOnFailureListener { e ->
                                    onResult(false, e.localizedMessage ?: "Failed to store match scores.")
                                }
                        }
                        .addOnFailureListener { e ->
                            onResult(false, e.localizedMessage ?: "Failed to load teams.")
                        }
                }
            }
            .addOnFailureListener { e ->
                onResult(false, e.localizedMessage ?: "Failed to load match scoring rules.")
            }
    }

    private fun fetchPlayerStats(
        matchId: String,
        onResult: (Map<String, PlayerStats>, String?) -> Unit
    ) {
        FirebaseUtils.matchesCollection()
            .document(matchId)
            .collection("playerStats")
            .get()
            .addOnSuccessListener { statsSnapshot ->
                val statsByKey = mutableMapOf<String, PlayerStats>()
                statsSnapshot.documents.forEach { doc ->
                    val playerId = doc.getString("playerId").orEmpty()
                    val playerName = doc.getString("playerName").orEmpty()
                    val stats = PlayerStats(
                        runs = (doc.getLong("runs") ?: 0L).toInt(),
                        wickets = (doc.getLong("wickets") ?: 0L).toInt(),
                        catches = (doc.getLong("catches") ?: 0L).toInt()
                    )
                    if (playerId.isNotBlank()) {
                        statsByKey["id:$playerId"] = stats
                    }
                    if (playerName.isNotBlank()) {
                        statsByKey["name:${playerName.lowercase()}"] = stats
                    }
                }

                if (statsByKey.isNotEmpty()) {
                    onResult(statsByKey, null)
                } else {
                    // Fallback: read stats if embedded in match players array.
                    FirebaseUtils.matchesCollection().document(matchId).get()
                        .addOnSuccessListener { matchDoc ->
                            val players = matchDoc.get("players") as? List<*> ?: emptyList<Any>()
                            val fallbackMap = mutableMapOf<String, PlayerStats>()
                            players.forEach { playerRaw ->
                                val map = playerRaw as? Map<*, *> ?: return@forEach
                                val id = map["id"]?.toString().orEmpty()
                                val name = map["name"]?.toString().orEmpty()
                                val stats = PlayerStats(
                                    runs = (map["runs"] as? Number)?.toInt() ?: 0,
                                    wickets = (map["wickets"] as? Number)?.toInt() ?: 0,
                                    catches = (map["catches"] as? Number)?.toInt() ?: 0
                                )
                                if (id.isNotBlank()) fallbackMap["id:$id"] = stats
                                if (name.isNotBlank()) fallbackMap["name:${name.lowercase()}"] = stats
                            }
                            if (fallbackMap.isEmpty()) {
                                onResult(emptyMap(), "No player stats found for scoring.")
                            } else {
                                onResult(fallbackMap, null)
                            }
                        }
                        .addOnFailureListener { e ->
                            onResult(emptyMap(), e.localizedMessage ?: "Failed to load player stats.")
                        }
                }
            }
            .addOnFailureListener { e ->
                onResult(emptyMap(), e.localizedMessage ?: "Failed to load player stats.")
            }
    }

    private fun safeDocId(value: String): String {
        return value.lowercase()
            .replace(" ", "_")
            .replace("/", "_")
            .replace(".", "_")
            .ifBlank { "player" }
    }
}

private data class PlayerStats(
    val runs: Int = 0,
    val wickets: Int = 0,
    val catches: Int = 0
)

private data class TeamScore(
    val teamDocId: String,
    val totalPoints: Double,
    val rank: Int = 0
)
