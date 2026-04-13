package com.example.cricarena.ui.mymatches

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.MyMatchItem
import com.example.cricarena.data.repository.FirebaseRepository
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.DocumentSnapshot

class MyMatchesViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    fun fetchMatches(tab: MyMatchesTab, onResult: (List<MyMatchItem>, String?) -> Unit) {
        val userId = FirebaseUtils.auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            onResult(emptyList(), "Please login to view matches.")
            return
        }

        when (tab) {
            MyMatchesTab.JOINED -> fetchJoinedMatches(userId, onResult)
            MyMatchesTab.CREATED -> fetchCreatedMatches(userId, onResult)
            MyMatchesTab.COMPLETED -> fetchCompletedMatches(userId, onResult)
        }
    }

    private fun fetchJoinedMatches(userId: String, onResult: (List<MyMatchItem>, String?) -> Unit) {
        FirebaseUtils.teamsCollection()
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { teams ->
                val items = teams.documents.map { doc ->
                    MyMatchItem(
                        matchId = doc.getString("matchId").orEmpty(),
                        matchName = doc.getString("matchName").orEmpty(),
                        status = doc.getString("status").orEmpty().ifBlank { "JOINED" },
                        pointsEarned = doc.getDouble("totalPoints") ?: 0.0,
                        rank = (doc.getLong("rank") ?: 0L).toInt()
                    )
                }.filter { it.matchId.isNotBlank() }
                resolveMissingMatchNames(items, onResult)
            }
            .addOnFailureListener { e ->
                onResult(emptyList(), e.localizedMessage ?: "Failed to fetch joined matches.")
            }
    }

    private fun fetchCreatedMatches(userId: String, onResult: (List<MyMatchItem>, String?) -> Unit) {
        FirebaseUtils.matchesCollection()
            .whereEqualTo("createdBy", userId)
            .get()
            .addOnSuccessListener { matches ->
                val result = matches.documents.map { doc ->
                    MyMatchItem(
                        matchId = doc.id,
                        matchName = doc.getString("title").orEmpty(),
                        status = doc.getString("status").orEmpty().ifBlank { "CREATED" },
                        pointsEarned = 0.0,
                        rank = 0
                    )
                }
                onResult(result, null)
            }
            .addOnFailureListener { e ->
                onResult(emptyList(), e.localizedMessage ?: "Failed to fetch created matches.")
            }
    }

    private fun fetchCompletedMatches(userId: String, onResult: (List<MyMatchItem>, String?) -> Unit) {
        FirebaseUtils.teamsCollection()
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { teams ->
                val items = teams.documents.map { doc ->
                    MyMatchItem(
                        matchId = doc.getString("matchId").orEmpty(),
                        matchName = doc.getString("matchName").orEmpty(),
                        status = "COMPLETED",
                        pointsEarned = doc.getDouble("totalPoints") ?: 0.0,
                        rank = (doc.getLong("rank") ?: 0L).toInt()
                    )
                }.filter { it.matchId.isNotBlank() }
                resolveMissingMatchNames(items, onResult)
            }
            .addOnFailureListener {
                // Fallback: if Teams documents don't carry status, derive from Matches status
                FirebaseUtils.teamsCollection()
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { teams ->
                        val rawItems = teams.documents.map { doc ->
                            MyMatchItem(
                                matchId = doc.getString("matchId").orEmpty(),
                                matchName = doc.getString("matchName").orEmpty(),
                                status = "COMPLETED",
                                pointsEarned = doc.getDouble("totalPoints") ?: 0.0,
                                rank = (doc.getLong("rank") ?: 0L).toInt()
                            )
                        }.filter { it.matchId.isNotBlank() }
                        filterByCompletedMatchStatus(rawItems, onResult)
                    }
                    .addOnFailureListener { e ->
                        onResult(emptyList(), e.localizedMessage ?: "Failed to fetch completed matches.")
                    }
            }
    }

    private fun resolveMissingMatchNames(
        items: List<MyMatchItem>,
        onResult: (List<MyMatchItem>, String?) -> Unit
    ) {
        if (items.isEmpty()) {
            onResult(emptyList(), null)
            return
        }
        val pendingIds = items.filter { it.matchName.isBlank() }.map { it.matchId }
        if (pendingIds.isEmpty()) {
            onResult(items, null)
            return
        }
        val resolved = items.toMutableList()
        var completed = 0
        pendingIds.forEach { matchId ->
            FirebaseUtils.matchesCollection().document(matchId).get()
                .addOnSuccessListener { doc ->
                    val idx = resolved.indexOfFirst { it.matchId == matchId }
                    if (idx >= 0) {
                        resolved[idx] = resolved[idx].copy(
                            matchName = doc.getString("title").orEmpty().ifBlank { "Match $matchId" },
                            status = resolved[idx].status.ifBlank {
                                doc.getString("status").orEmpty().ifBlank { "JOINED" }
                            }
                        )
                    }
                    completed += 1
                    if (completed == pendingIds.size) onResult(resolved, null)
                }
                .addOnFailureListener {
                    completed += 1
                    if (completed == pendingIds.size) onResult(resolved, null)
                }
        }
    }

    private fun filterByCompletedMatchStatus(
        items: List<MyMatchItem>,
        onResult: (List<MyMatchItem>, String?) -> Unit
    ) {
        if (items.isEmpty()) {
            onResult(emptyList(), null)
            return
        }
        val completedItems = mutableListOf<MyMatchItem>()
        var doneCount = 0
        items.forEach { item ->
            FirebaseUtils.matchesCollection().document(item.matchId).get()
                .addOnSuccessListener { matchDoc: DocumentSnapshot ->
                    val status = matchDoc.getString("status").orEmpty()
                    if (status.equals("COMPLETED", ignoreCase = true)) {
                        completedItems.add(
                            item.copy(
                                matchName = item.matchName.ifBlank {
                                    matchDoc.getString("title").orEmpty().ifBlank { "Match ${item.matchId}" }
                                }
                            )
                        )
                    }
                    doneCount += 1
                    if (doneCount == items.size) onResult(completedItems, null)
                }
                .addOnFailureListener {
                    doneCount += 1
                    if (doneCount == items.size) onResult(completedItems, null)
                }
        }
    }
}
