package com.example.cricarena.data.repository

import android.util.Log
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchCategory
import com.example.cricarena.data.model.MatchStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseRepository(
    val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun getMatches(
        onData: (List<Match>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        Log.d(TAG, "Listening matches from path: Matches orderBy createdAt DESC")
        return firestore.collection("Matches")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching matches", error)
                    onError(error.localizedMessage ?: "Failed to fetch matches.")
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    onData(emptyList())
                    return@addSnapshotListener
                }
                val mapped = snapshot.documents.mapNotNull { it.toUiMatch() }
                Log.d(TAG, "Matches fetched: ${mapped.size}")
                onData(mapped)
            }
    }

    fun getPlayers(
        matchId: String,
        onData: (List<FantasyPlayer>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        val path = "Matches/$matchId/players"
        Log.d(TAG, "Fetching players for matchId: $matchId, path: $path")
        return firestore.collection("Matches")
            .document(matchId)
            .collection("players")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching players for matchId: $matchId", error)
                    onError(error.localizedMessage ?: "Failed to fetch players.")
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    onData(emptyList())
                    return@addSnapshotListener
                }
                val mapped = snapshot.documents.mapIndexedNotNull { index, document ->
                    val name = document.getString("name").orEmpty()
                    val role = document.getString("role").orEmpty()
                    if (name.isBlank() || role.isBlank()) return@mapIndexedNotNull null
                    val stableId = document.getString("id")
                        ?.takeIf { it.isNotBlank() }
                        ?: document.id.takeIf { it.isNotBlank() }
                        ?: "$matchId-$index-${name.lowercase()}"
                    FantasyPlayer(
                        id = stableId,
                        name = name,
                        role = role
                    )
                }
                Log.d(TAG, "Players fetched for $matchId: ${mapped.size}")
                onData(mapped)
            }
    }

    private fun DocumentSnapshot.toUiMatch(): Match? {
        val statusStr = getString("status") ?: "DRAFT"
        if (statusStr == "DRAFT") return null

        val teamA = getString("teamAName")
            ?: getString("teamA")
            ?: return null
        val teamB = getString("teamBName")
            ?: getString("teamB")
            ?: return null
        val title = getString("title").orEmpty().ifBlank { "$teamA vs $teamB" }
        val matchType = getString("matchType") ?: "T20"
        val status = when (statusStr) {
            "LIVE" -> MatchStatus.LIVE
            "COMPLETED" -> MatchStatus.COMPLETED
            else -> MatchStatus.UPCOMING
        }

        val startMillis = getLong("startTimeMillis") ?: 0L
        val startTime = if (startMillis > 0L) {
            LIST_TIME_FORMAT.format(Date(startMillis))
        } else {
            "-"
        }

        return Match(
            id = id,
            title = title,
            teamA = teamA,
            teamB = teamB,
            matchType = matchType,
            category = MatchCategory.MANUAL,
            status = status,
            startTime = startTime,
            scoreSummary = buildScoreSummary(this)
        )
    }

    private fun buildScoreSummary(doc: DocumentSnapshot): String? {
        val a = doc.get("liveScoreA") as? Map<*, *> ?: return null
        val b = doc.get("liveScoreB") as? Map<*, *> ?: return null
        val rA = (a["runs"] as? Number)?.toInt() ?: 0
        val wA = (a["wickets"] as? Number)?.toInt() ?: 0
        val ballsA = (a["balls"] as? Number)?.toInt() ?: 0
        val rB = (b["runs"] as? Number)?.toInt() ?: 0
        val wB = (b["wickets"] as? Number)?.toInt() ?: 0
        val ballsB = (b["balls"] as? Number)?.toInt() ?: 0
        if (rA == 0 && wA == 0 && ballsA == 0 && rB == 0 && wB == 0 && ballsB == 0) return null
        return "$rA/$wA (${formatOvers(ballsA)}) · $rB/$wB (${formatOvers(ballsB)})"
    }

    private fun formatOvers(balls: Int): String {
        val overs = balls / 6
        val remBalls = balls % 6
        return "$overs.$remBalls"
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
        private val LIST_TIME_FORMAT = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }
}
