package com.example.cricarena.ui.creatematch

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.data.payload.MatchPayloadBuilder
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue

class CreateMatchViewModel : ViewModel() {

    /**
     * @param onResult (success, matchIdOrMessage) — on success the second value is the Firestore match document id; on failure it is an error message.
     */
    fun saveMatch(
        title: String,
        teamA: String,
        teamB: String,
        matchType: String,
        startTimeMillis: Long,
        players: List<MatchPlayer>,
        status: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = FirebaseUtils.auth.currentUser?.uid.orEmpty()
        val built = MatchPayloadBuilder.build(teamA, teamB, players)
        val payload = hashMapOf<String, Any>(
            "title" to title,
            "teamA" to teamA,
            "teamB" to teamB,
            "teamAName" to teamA,
            "teamBName" to teamB,
            "matchType" to matchType,
            "startTimeMillis" to startTimeMillis,
            "players" to built.playersPayload,
            "teamAPlayers" to built.teamAPlayers,
            "teamBPlayers" to built.teamBPlayers,
            "playerStatsTemplate" to built.playerStatsTemplate,
            "liveScoreA" to MatchPayloadBuilder.emptyLiveScoreMap(),
            "liveScoreB" to MatchPayloadBuilder.emptyLiveScoreMap(),
            "status" to status,
            "source" to "MANUAL",
            "scoringRules" to mapOf(
                "runs" to 1.0,
                "wickets" to 25.0,
                "catch" to 8.0
            ),
            "createdBy" to userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastUpdated" to FieldValue.serverTimestamp()
        )

        val matchRef = FirebaseUtils.matchesCollection().document()
        Log.d(TAG, "Saving match to path: Matches/${matchRef.id}")
        matchRef
            .set(payload)
            .addOnSuccessListener {
                val batch = FirebaseUtils.firestore.batch()
                built.playersPayload.forEach { player ->
                    val playerId = player["id"]?.toString().orEmpty()
                    if (playerId.isBlank()) return@forEach
                    val playerDoc = mapOf(
                        "id" to playerId,
                        "name" to player["name"].toString(),
                        "team" to player["teamName"].toString(),
                        "role" to player["role"].toString()
                    )
                    val playerRef = matchRef.collection("players").document(playerId)
                    batch.set(playerRef, playerDoc)
                }
                built.playerStatsTemplate.forEach { stat ->
                    val playerId = stat["playerId"]?.toString().orEmpty()
                    if (playerId.isBlank()) return@forEach
                    val statRef = matchRef.collection("playerStats").document(playerId)
                    batch.set(statRef, stat)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Match saved successfully: ${matchRef.id}, players=${players.size}")
                        onResult(true, matchRef.id)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save subcollections for matchId: ${matchRef.id}", e)
                        onResult(false, e.localizedMessage ?: "Failed to create player records.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save match document", e)
                onResult(false, e.localizedMessage)
            }
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}
