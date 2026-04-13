package com.example.cricarena.ui.creatematch

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
            "matchType" to matchType,
            "startTimeMillis" to startTimeMillis,
            "players" to built.playersPayload,
            "teamAPlayers" to built.teamAPlayers,
            "teamBPlayers" to built.teamBPlayers,
            "playerStatsTemplate" to built.playerStatsTemplate,
            "liveScoreA" to MatchPayloadBuilder.emptyLiveScoreMap(),
            "liveScoreB" to MatchPayloadBuilder.emptyLiveScoreMap(),
            "status" to status,
            "createdBy" to userId,
            "createdAt" to FieldValue.serverTimestamp()
        )

        val matchRef = FirebaseUtils.matchesCollection().document()
        matchRef
            .set(payload)
            .addOnSuccessListener {
                val batch = FirebaseUtils.firestore.batch()
                built.playerStatsTemplate.forEach { stat ->
                    val playerId = stat["playerId"]?.toString().orEmpty()
                    if (playerId.isBlank()) return@forEach
                    val statRef = matchRef.collection("playerStats").document(playerId)
                    batch.set(statRef, stat)
                }
                batch.commit()
                    .addOnSuccessListener { onResult(true, matchRef.id) }
                    .addOnFailureListener { e -> onResult(false, e.localizedMessage ?: "Failed to create player stats.") }
            }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage) }
    }
}
