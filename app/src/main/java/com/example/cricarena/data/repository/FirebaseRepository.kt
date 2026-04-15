package com.example.cricarena.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.data.model.BallEvent
import com.example.cricarena.data.model.BallEventType
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchCategory
import com.example.cricarena.data.model.MatchStatus
import com.example.cricarena.data.model.UserTeamMatch
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseRepository(
    val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun getMatches(): LiveData<List<Match>> {
        return object : FirestoreLiveData<List<Match>>() {
            override fun startListening() {
                Log.d(TAG, "Fetching matches")
                Log.d(
                    TAG,
                    "Path: ${FirebaseUtils.COLLECTION_MATCHES} (full collection; client sort — no orderBy so docs without createdAt are included)"
                )
                registration = FirebaseUtils.matchesCollection()
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(ERROR_TAG, error.message ?: "Unknown matches fetch error", error)
                            // Keep last emitted list during transient watch/network failures.
                            return@addSnapshotListener
                        }
                        val mapped = snapshot?.documents.orEmpty().mapNotNull { it.toUiMatch() }
                        Log.d(TAG, "Matches fetched count: ${mapped.size}")
                        postValue(mapped)
                    }
            }
        }
    }

    fun getPlayers(matchId: String): LiveData<List<FantasyPlayer>> {
        return object : FirestoreLiveData<List<FantasyPlayer>>() {
            override fun startListening() {
                if (matchId.isBlank()) {
                    Log.e(ERROR_TAG, "matchId is empty while fetching players")
                    postValue(emptyList())
                    return
                }
                val path = "${FirebaseUtils.COLLECTION_MATCHES}/$matchId/players"
                Log.d(TAG, "Fetching players for matchId: $matchId")
                Log.d(TAG, "Path: $path")
                registration = FirebaseUtils.matchesCollection()
                    .document(matchId)
                    .collection("players")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(ERROR_TAG, error.message ?: "Unknown players fetch error", error)
                            // Keep last emitted players during transient watch/network failures.
                            return@addSnapshotListener
                        }
                        val mapped = snapshot?.documents.orEmpty().mapIndexedNotNull { index, document ->
                            val name = document.getString("name").orEmpty()
                            val role = document.getString("role").orEmpty()
                            val team = document.getString("team").orEmpty()
                            if (name.isBlank() || role.isBlank() || team.isBlank()) {
                                return@mapIndexedNotNull null
                            }
                            val stableId = document.getString("id")
                                ?.takeIf { it.isNotBlank() }
                                ?: document.id.takeIf { it.isNotBlank() }
                                ?: "$matchId-$index-${name.lowercase()}"
                            FantasyPlayer(
                                id = stableId,
                                name = name,
                                role = role,
                                team = team,
                                runs = (document.getLong("runs") ?: 0L).toInt(),
                                balls = (document.getLong("balls") ?: 0L).toInt(),
                                wickets = (document.getLong("wickets") ?: 0L).toInt(),
                                catches = (document.getLong("catches") ?: 0L).toInt(),
                                fantasyPoints = document.getDouble("fantasyPoints") ?: 0.0
                            )
                        }
                        Log.d(TAG, "Players fetched count for $matchId: ${mapped.size}")
                        postValue(mapped)
                    }
            }
        }
    }

    fun getBallEvents(matchId: String): LiveData<List<BallEvent>> {
        return object : FirestoreLiveData<List<BallEvent>>() {
            override fun startListening() {
                if (matchId.isBlank()) {
                    Log.e(ERROR_TAG, "matchId is empty while fetching ball events")
                    postValue(emptyList())
                    return
                }
                val path = "${FirebaseUtils.COLLECTION_MATCHES}/$matchId/ballEvents"
                Log.d(TAG, "Fetching ballEvents for matchId: $matchId")
                Log.d(TAG, "Path: $path")
                registration = FirebaseUtils.matchesCollection()
                    .document(matchId)
                    .collection("ballEvents")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(ERROR_TAG, error.message ?: "Unknown ballEvents fetch error", error)
                            postValue(emptyList())
                            return@addSnapshotListener
                        }
                        val mapped = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            doc.toBallEventOrNull()
                        }
                        Log.d(TAG, "Ball events fetched count for $matchId: ${mapped.size}")
                        postValue(mapped)
                    }
            }
        }
    }

    fun getUserTeams(userId: String): LiveData<List<UserTeamMatch>> {
        return object : FirestoreLiveData<List<UserTeamMatch>>() {
            override fun startListening() {
                if (userId.isBlank()) {
                    Log.e(ERROR_TAG, "userId is empty while fetching user teams")
                    postValue(emptyList())
                    return
                }
                Log.d(TAG, "Fetching teams for userId: $userId")
                Log.d(TAG, "Path: Teams where userId == $userId")
                registration = firestore.collection("Teams")
                    .whereEqualTo("userId", userId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(ERROR_TAG, error.message ?: "Unknown teams fetch error", error)
                            postValue(emptyList())
                            return@addSnapshotListener
                        }
                        val mapped = snapshot?.documents.orEmpty().mapNotNull { doc ->
                            val matchId = doc.getString("matchId").orEmpty()
                            if (matchId.isBlank()) return@mapNotNull null
                            UserTeamMatch(
                                matchId = matchId,
                                matchName = doc.getString("matchName").orEmpty(),
                                status = doc.getString("status").orEmpty(),
                                totalPoints = doc.getDouble("totalPoints") ?: 0.0,
                                rank = (doc.getLong("rank") ?: 0L).toInt()
                            )
                        }
                        postValue(mapped)
                    }
            }
        }
    }

    fun appendBallEvent(
        matchId: String,
        event: BallEvent,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (matchId.isBlank()) {
            onResult(false, "Match id is required.")
            return
        }
        val payload = mapOf(
            "type" to event.type.name,
            "runs" to event.runs,
            "playerId" to event.playerId,
            "team" to event.teamName,
            "timestamp" to event.timestamp
        )
        val path = "${FirebaseUtils.COLLECTION_MATCHES}/$matchId/ballEvents"
        Log.d(TAG, "Saving ball event to path: $path")
        FirebaseUtils.matchesCollection()
            .document(matchId)
            .collection("ballEvents")
            .add(payload)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                Log.e(ERROR_TAG, e.message ?: "Failed to save ball event", e)
                onResult(false, e.localizedMessage ?: "Failed to save ball event.")
            }
    }

    fun deleteMatch(
        matchId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (matchId.isBlank()) {
            onResult(false, "Match id is required.")
            return
        }
        FirebaseUtils.matchDocument(matchId)
            .delete()
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e ->
                Log.e(ERROR_TAG, e.message ?: "Failed to delete match", e)
                onResult(false, e.localizedMessage ?: "Failed to delete match.")
            }
    }

    private fun DocumentSnapshot.toUiMatch(): Match? {
        // Only exclude explicit drafts; missing/legacy status is shown (not treated as DRAFT).
        val statusStr = getString("status").orEmpty()
        if (statusStr.equals("DRAFT", ignoreCase = true)) return null

        val teamA = getString("teamAName")
            ?: getString("teamA")
            ?: return null
        val teamB = getString("teamBName")
            ?: getString("teamB")
            ?: return null
        val title = getString("title").orEmpty().ifBlank { "$teamA vs $teamB" }
        val matchType = getString("matchType") ?: "T20"
        val source = getString("source").orEmpty()
        val category = when {
            source.equals("AUTO", ignoreCase = true) -> MatchCategory.AUTO
            else -> MatchCategory.MANUAL
        }
        val normalized = statusStr.uppercase(Locale.US)
        val status = when (normalized) {
            "LIVE" -> MatchStatus.LIVE
            "COMPLETED" -> MatchStatus.COMPLETED
            // PUBLISHED, SQUADS_SAVED, empty, unknown → upcoming for chips / fantasy eligibility
            else -> MatchStatus.UPCOMING
        }

        val startMillis = readStartTimeMillis()
        val startTime = if (startMillis > 0L) {
            LIST_TIME_FORMAT.format(Date(startMillis))
        } else {
            "-"
        }

        return Match(
            id = id,
            title = title,
            createdBy = getString("createdBy").orEmpty(),
            createdAt = getTimestamp("createdAt"),
            lastUpdated = getTimestamp("lastUpdated"),
            teamA = teamA,
            teamB = teamB,
            matchType = matchType,
            category = category,
            status = status,
            startTime = startTime,
            scoreSummary = buildScoreSummary(this)
        )
    }

    /**
     * Prefer `startTimeMillis` (Long) as written by create flow; accept Firestore `Timestamp`
     * in `startTime` for older or imported docs.
     */
    private fun DocumentSnapshot.readStartTimeMillis(): Long {
        getLong("startTimeMillis")?.takeIf { it > 0L }?.let { return it }
        getTimestamp("startTime")?.toDate()?.time?.takeIf { it > 0L }?.let { return it }
        return 0L
    }

    private fun buildScoreSummary(doc: DocumentSnapshot): String? {
        val teamAName = doc.getString("teamAName") ?: doc.getString("teamA") ?: "A"
        val teamBName = doc.getString("teamBName") ?: doc.getString("teamB") ?: "B"
        val a = doc.get("liveScoreA") as? Map<*, *> ?: return null
        val b = doc.get("liveScoreB") as? Map<*, *> ?: return null
        val rA = (a["runs"] as? Number)?.toInt() ?: 0
        val wA = (a["wickets"] as? Number)?.toInt() ?: 0
        val ballsA = (a["balls"] as? Number)?.toInt() ?: 0
        val rB = (b["runs"] as? Number)?.toInt() ?: 0
        val wB = (b["wickets"] as? Number)?.toInt() ?: 0
        val ballsB = (b["balls"] as? Number)?.toInt() ?: 0
        if (rA == 0 && wA == 0 && ballsA == 0 && rB == 0 && wB == 0 && ballsB == 0) return null
        val oA = formatOvers(ballsA)
        val oB = formatOvers(ballsB)
        return "$teamAName $rA/$wA ($oA) vs $teamBName $rB/$wB ($oB)"
    }

    private fun formatOvers(balls: Int): String {
        val overs = balls / 6
        val remBalls = balls % 6
        return "$overs.$remBalls"
    }

    private fun DocumentSnapshot.toBallEventOrNull(): BallEvent? {
        val typeName = getString("type").orEmpty()
        val type = runCatching { BallEventType.valueOf(typeName) }.getOrNull() ?: return null
        val playerId = getString("playerId").orEmpty()
        if (playerId.isBlank()) return null
        val teamName = getString("team").orEmpty()
        if (teamName.isBlank()) return null
        val runs = (getLong("runs") ?: 0L).toInt()
        val timestamp = getLong("timestamp") ?: 0L
        return BallEvent(
            id = id,
            type = type,
            playerId = playerId,
            teamName = teamName,
            runs = runs,
            timestamp = timestamp
        )
    }

    private abstract class FirestoreLiveData<T> : LiveData<T>() {
        protected var registration: com.google.firebase.firestore.ListenerRegistration? = null

        abstract fun startListening()

        override fun onActive() {
            super.onActive()
            startListening()
        }

        override fun onInactive() {
            registration?.remove()
            registration = null
            super.onInactive()
        }
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
        private const val ERROR_TAG = "FIREBASE_ERROR"
        private val LIST_TIME_FORMAT = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    }
}
