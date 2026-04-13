package com.example.cricarena.ui.creatematch

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.repository.FirebaseRepository
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue

class CreateMatchViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    fun saveMatch(
        title: String,
        teamA: String,
        teamB: String,
        matchType: String,
        startTimeMillis: Long,
        players: List<Map<String, String>>,
        scoringRules: Map<String, Int>,
        status: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = FirebaseUtils.auth.currentUser?.uid.orEmpty()
        val payload = hashMapOf(
            "title" to title,
            "teamA" to teamA,
            "teamB" to teamB,
            "matchType" to matchType,
            "startTimeMillis" to startTimeMillis,
            "players" to players,
            "scoringRules" to scoringRules,
            "status" to status,
            "createdBy" to userId,
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseUtils.matchesCollection()
            .add(payload)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage) }
    }
}
