package com.example.cricarena.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchCategory
import com.example.cricarena.data.model.MatchStatus
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel : ViewModel() {

    private val _matches = MutableLiveData<List<Match>>(emptyList())
    val matches: LiveData<List<Match>> = _matches

    private val _loadError = MutableLiveData<String?>(null)
    val loadError: LiveData<String?> = _loadError

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun loadMatches() {
        _loading.value = true
        FirebaseUtils.matchesCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)
            .get()
            .addOnSuccessListener { snap ->
                _matches.value = snap.documents.mapNotNull { it.toUiMatch() }
                _loadError.value = null
                _loading.value = false
            }
            .addOnFailureListener { e ->
                _loadError.value = e.localizedMessage
                _loading.value = false
            }
    }
}

private val listTimeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

private fun formatOvers(balls: Int): String {
    val o = balls / 6
    val b = balls % 6
    return "$o.$b"
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

private fun DocumentSnapshot.toUiMatch(): Match? {
    val statusStr = getString("status") ?: "DRAFT"
    if (statusStr == "DRAFT") return null

    val teamA = getString("teamA") ?: return null
    val teamB = getString("teamB") ?: return null
    val matchType = getString("matchType") ?: "T20"

    val status = when (statusStr) {
        "LIVE" -> MatchStatus.LIVE
        "COMPLETED" -> MatchStatus.COMPLETED
        else -> MatchStatus.UPCOMING
    }

    val startMillis = getLong("startTimeMillis") ?: 0L
    val startTime = if (startMillis > 0L) {
        listTimeFormat.format(Date(startMillis))
    } else {
        "—"
    }

    val scoreSummary = buildScoreSummary(this)

    return Match(
        id = id,
        teamA = teamA,
        teamB = teamB,
        matchType = matchType,
        category = MatchCategory.MANUAL,
        status = status,
        startTime = startTime,
        scoreSummary = scoreSummary
    )
}
