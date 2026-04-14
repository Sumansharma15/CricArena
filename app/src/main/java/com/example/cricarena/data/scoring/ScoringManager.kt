package com.example.cricarena.data.scoring

import android.util.Log
import androidx.lifecycle.LiveData
import com.example.cricarena.data.model.BallEvent
import com.example.cricarena.data.model.BallEventType
import com.example.cricarena.data.model.LivePlayer
import com.example.cricarena.data.model.LiveTeam
import com.example.cricarena.data.repository.FirebaseRepository
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

data class MatchStatsResult(
    val players: List<LivePlayer>,
    val teams: Map<String, LiveTeam>
)

class ScoringManager(
    private val repository: FirebaseRepository = FirebaseRepository()
) {
    fun getBallEvents(matchId: String): LiveData<List<BallEvent>> = repository.getBallEvents(matchId)

    fun addBallEvent(
        matchId: String,
        event: BallEvent,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (matchId.isBlank()) {
            onResult(false, "Match id is required.")
            return
        }
        val matchRef = FirebaseUtils.matchDocument(matchId)
        matchRef.get()
            .addOnSuccessListener { matchDoc ->
                val status = matchDoc.getString("status").orEmpty()
                if (status.isNotBlank() &&
                    status != "LIVE" &&
                    status != "SQUADS_SAVED" &&
                    status != "PUBLISHED"
                ) {
                    onResult(false, "Scoring allowed only when match is published or live.")
                    return@addOnSuccessListener
                }
                repository.appendBallEvent(matchId, event) { success, error ->
                    if (success) {
                        Log.d(TAG, "BallEvent added: matchId=$matchId, type=${event.type}, playerId=${event.playerId}")
                        matchRef.set(mapOf("status" to "LIVE"), SetOptions.merge())
                    }
                    onResult(success, error)
                }
            }
            .addOnFailureListener { e ->
                Log.e(ERROR_TAG, e.message ?: "Failed to validate match status before scoring", e)
                onResult(false, e.localizedMessage ?: "Failed to validate match status.")
            }
    }

    fun undoLastEvent(
        matchId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (matchId.isBlank()) {
            onResult(false, "Match id is required.")
            return
        }
        FirebaseUtils.matchDocument(matchId)
            .collection("ballEvents")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc == null) {
                    onResult(false, "No ball events to undo.")
                    return@addOnSuccessListener
                }
                doc.reference.delete()
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e ->
                        Log.e(ERROR_TAG, e.message ?: "Failed to undo event", e)
                        onResult(false, e.localizedMessage ?: "Failed to undo event.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(ERROR_TAG, e.message ?: "Failed to load last ball event", e)
                onResult(false, e.localizedMessage ?: "Failed to load last event.")
            }
    }

    fun recalculateMatchStats(
        ballEvents: List<BallEvent>,
        basePlayers: List<LivePlayer>,
        teamA: String,
        teamB: String
    ): MatchStatsResult {
        Log.d(TAG, "Recalculating stats for events=${ballEvents.size}")
        val playersMap = basePlayers.associateBy { it.id }.mapValues { it.value.copy() }.toMutableMap()
        val teamMap = mutableMapOf(
            teamA to LiveTeam(teamA),
            teamB to LiveTeam(teamB)
        )

        ballEvents.forEach { event ->
            val player = playersMap[event.playerId] ?: return@forEach
            val team = teamMap[event.teamName] ?: LiveTeam(event.teamName)
            when (event.type) {
                BallEventType.RUN -> {
                    playersMap[event.playerId] = player.copy(runs = player.runs + event.runs, balls = player.balls + 1)
                    teamMap[event.teamName] = team.copy(totalRuns = team.totalRuns + event.runs, ballsDelivered = team.ballsDelivered + 1)
                }
                BallEventType.WIDE,
                BallEventType.NO_BALL -> {
                    teamMap[event.teamName] = team.copy(totalRuns = team.totalRuns + event.runs)
                }
                BallEventType.BYE -> {
                    teamMap[event.teamName] = team.copy(totalRuns = team.totalRuns + event.runs, ballsDelivered = team.ballsDelivered + 1)
                }
                BallEventType.WICKET -> {
                    playersMap[event.playerId] = player.copy(isOut = true, balls = player.balls + 1)
                    teamMap[event.teamName] = team.copy(wickets = team.wickets + 1, ballsDelivered = team.ballsDelivered + 1)
                }
                BallEventType.CATCH -> {
                    playersMap[event.playerId] = player.copy(catches = player.catches + 1, isOut = true, balls = player.balls + 1)
                    teamMap[event.teamName] = team.copy(wickets = team.wickets + 1, ballsDelivered = team.ballsDelivered + 1)
                }
            }
        }
        return MatchStatsResult(
            players = playersMap.values.toList(),
            teams = teamMap
        )
    }

    fun persistDerivedStats(
        matchId: String,
        teamA: String,
        teamB: String,
        result: MatchStatsResult
    ) {
        if (matchId.isBlank()) return
        val teamAState = result.teams[teamA] ?: LiveTeam(teamA)
        val teamBState = result.teams[teamB] ?: LiveTeam(teamB)
        val matchRef = FirebaseUtils.matchDocument(matchId)
        val batch = repository.firestore.batch()
        val oversA = toOvers(teamAState.ballsDelivered)
        val oversB = toOvers(teamBState.ballsDelivered)

        batch.set(
            matchRef,
            mapOf(
                "liveScoreA" to mapOf(
                    "runs" to teamAState.totalRuns,
                    "wickets" to teamAState.wickets,
                    "balls" to teamAState.ballsDelivered
                ),
                "liveScoreB" to mapOf(
                    "runs" to teamBState.totalRuns,
                    "wickets" to teamBState.wickets,
                    "balls" to teamBState.ballsDelivered
                ),
                "wicketsA" to teamAState.wickets,
                "wicketsB" to teamBState.wickets,
                "oversA" to oversA,
                "oversB" to oversB,
                "lastUpdated" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        Log.d("SCORE_DEBUG", "Updated match score: A=${teamAState.totalRuns}/${teamAState.wickets}, B=${teamBState.totalRuns}/${teamBState.wickets}")

        result.players.forEach { player ->
            val wickets = if (player.isOut) 1 else 0
            val fantasyPoints = (player.runs * 1.0) + (wickets * 25.0) + (player.catches * 8.0)
            val playerPayload = mapOf(
                "playerId" to player.id,
                "id" to player.id,
                "playerName" to player.name,
                "name" to player.name,
                "teamName" to player.teamName,
                "team" to player.teamName,
                "role" to player.role,
                "runs" to player.runs,
                "wickets" to wickets,
                "catches" to player.catches,
                "balls" to player.balls,
                "fantasyPoints" to fantasyPoints
            )
            val playerStatRef = matchRef.collection("playerStats").document(player.id)
            batch.set(
                playerStatRef,
                playerPayload,
                SetOptions.merge()
            )
            val playerRef = matchRef.collection("players").document(player.id)
            batch.set(playerRef, playerPayload, SetOptions.merge())
            Log.d("SCORE_DEBUG", "Player stats updated: playerId=${player.id}")
        }
        batch.commit().addOnFailureListener { e ->
            Log.e(ERROR_TAG, e.message ?: "Failed to persist derived stats", e)
        }
    }

    private fun toOvers(totalBalls: Int): String {
        val overs = totalBalls / 6
        val balls = totalBalls % 6
        return "$overs.$balls"
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
        private const val ERROR_TAG = "FIREBASE_ERROR"
    }
}
