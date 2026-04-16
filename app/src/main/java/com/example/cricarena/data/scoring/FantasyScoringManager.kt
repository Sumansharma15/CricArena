package com.example.cricarena.data.scoring

import android.util.Log
import com.example.cricarena.data.model.BallEvent
import com.example.cricarena.data.model.BallEventType
import com.example.cricarena.data.model.LivePlayer
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.round

class FantasyScoringManager(
    private val firestore: FirebaseFirestore = FirebaseUtils.firestore
) {
    private val scoringManager = ScoringManager()

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
                val teamAName = matchDoc.getString("teamAName") ?: matchDoc.getString("teamA").orEmpty()
                val teamBName = matchDoc.getString("teamBName") ?: matchDoc.getString("teamB").orEmpty()
                val scoringRules = matchDoc.get("scoringRules") as? Map<*, *>
                val runPoint = (scoringRules?.get("runs") as? Number)?.toDouble() ?: 1.0
                val wicketPoint = (scoringRules?.get("wickets") as? Number)?.toDouble() ?: 25.0
                val catchPoint = (scoringRules?.get("catch") as? Number)?.toDouble() ?: 8.0

                loadPlayersAndEvents(matchId) { players, events, loadError ->
                    if (loadError != null) {
                        onResult(false, loadError)
                        return@loadPlayersAndEvents
                    }
                    if (players.isEmpty()) {
                        onResult(false, "No players found for scoring.")
                        return@loadPlayersAndEvents
                    }
                    val derived = scoringManager.recalculateMatchStats(events, players, teamAName, teamBName)
                    val statsMap = derived.players.associateBy { "id:${it.id}" }

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
                                    val stats = statsMap["id:$playerId"] ?: LivePlayer(
                                        id = playerId,
                                        name = playerMap["name"]?.toString().orEmpty(),
                                        role = playerMap["role"]?.toString().orEmpty(),
                                        teamName = playerMap["team"]?.toString().orEmpty()
                                    )
                                    val wicketCount = stats.wicketsTaken
                                    val base = (stats.runs * runPoint) +
                                        (wicketCount * wicketPoint) +
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
                                val wicketCount = stats.wicketsTaken
                                val basePoints = (stats.runs * runPoint) +
                                    (wicketCount * wicketPoint) +
                                    (stats.catches * catchPoint)
                                val playerName = stats.name.ifBlank { key.removePrefix("id:") }
                                val docRef = FirebaseUtils.matchesCollection()
                                    .document(matchId)
                                    .collection("playerPoints")
                                    .document(safeDocId(playerName))
                                batch.set(
                                    docRef,
                                    mapOf(
                                        "playerName" to playerName,
                                        "runs" to stats.runs,
                                        "wickets" to wicketCount,
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
                                .addOnSuccessListener {
                                    Log.d(TAG, "Leaderboard updated for matchId=$matchId")
                                    onResult(true, null)
                                }
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

    private fun loadPlayersAndEvents(
        matchId: String,
        onResult: (List<LivePlayer>, List<BallEvent>, String?) -> Unit
    ) {
        FirebaseUtils.matchesCollection()
            .document(matchId)
            .collection("players")
            .get()
            .addOnSuccessListener { playerSnapshot ->
                val players = playerSnapshot.documents.mapNotNull { doc ->
                    val id = doc.getString("id").orEmpty().ifBlank { doc.id }
                    val name = doc.getString("name").orEmpty().ifBlank { doc.getString("playerName").orEmpty() }
                    val role = doc.getString("role").orEmpty()
                    val team = doc.getString("team").orEmpty().ifBlank { doc.getString("teamName").orEmpty() }
                    if (id.isBlank() || name.isBlank() || role.isBlank() || team.isBlank()) {
                        null
                    } else {
                        LivePlayer(
                            id = id,
                            name = name,
                            role = role,
                            teamName = team,
                            wicketsTaken = (doc.getLong("wickets") ?: 0L).toInt(),
                            catches = (doc.getLong("catches") ?: 0L).toInt(),
                            runs = (doc.getLong("runs") ?: 0L).toInt(),
                            balls = (doc.getLong("balls") ?: 0L).toInt(),
                            isOut = doc.getBoolean("isOut") ?: false
                        )
                    }
                }
                FirebaseUtils.matchesCollection()
                    .document(matchId)
                    .collection("ballEvents")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener { eventSnapshot ->
                        val events = eventSnapshot.documents.mapNotNull { doc ->
                            val typeName = doc.getString("type").orEmpty()
                            val type = runCatching { BallEventType.valueOf(typeName) }.getOrNull() ?: return@mapNotNull null
                            val playerId = doc.getString("playerId").orEmpty()
                            val team = doc.getString("team").orEmpty()
                            if (playerId.isBlank() || team.isBlank()) return@mapNotNull null
                            BallEvent(
                                id = doc.id,
                                type = type,
                                playerId = playerId,
                                teamName = team,
                                runs = (doc.getLong("runs") ?: 0L).toInt(),
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        }
                        onResult(players, events, null)
                    }
                    .addOnFailureListener { e ->
                        onResult(players, emptyList(), e.localizedMessage ?: "Failed to load ball events.")
                    }
            }
            .addOnFailureListener { e ->
                onResult(emptyList(), emptyList(), e.localizedMessage ?: "Failed to load players.")
            }
    }

    private fun safeDocId(value: String): String {
        return value.lowercase()
            .replace(" ", "_")
            .replace("/", "_")
            .replace(".", "_")
            .ifBlank { "player" }
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}

private data class TeamScore(
    val teamDocId: String,
    val totalPoints: Double,
    val rank: Int = 0
)
