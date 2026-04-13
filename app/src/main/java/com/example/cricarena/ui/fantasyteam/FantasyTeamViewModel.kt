package com.example.cricarena.ui.fantasyteam

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.data.repository.FirebaseRepository
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue

class FantasyTeamViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    fun fetchPlayersFromMatch(
        matchId: String,
        onResult: (List<FantasyPlayer>, String?) -> Unit
    ) {
        FirebaseUtils.matchesCollection().document(matchId).get()
            .addOnSuccessListener { document ->
                val playersRaw = document.get("players")
                if (playersRaw !is List<*>) {
                    onResult(emptyList(), "No players found for this match.")
                    return@addOnSuccessListener
                }

                val mapped = playersRaw.mapIndexedNotNull { index, item ->
                    val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
                    val name = map["name"]?.toString().orEmpty()
                    val role = map["role"]?.toString().orEmpty()
                    if (name.isBlank() || role.isBlank()) return@mapIndexedNotNull null
                    val stableId = map["id"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: "$matchId-$index-${name.lowercase()}"
                    FantasyPlayer(
                        id = stableId,
                        name = name,
                        role = role
                    )
                }
                onResult(mapped, null)
            }
            .addOnFailureListener { e ->
                onResult(emptyList(), e.localizedMessage ?: "Failed to fetch players.")
            }
    }

    fun saveTeam(
        matchId: String,
        selectedPlayers: List<FantasyPlayer>,
        captainId: String,
        viceCaptainId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = FirebaseUtils.auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            onResult(false, "Please login to save team.")
            return
        }

        val payload = hashMapOf(
            "userId" to userId,
            "matchId" to matchId,
            "captainId" to captainId,
            "viceCaptainId" to viceCaptainId,
            "players" to selectedPlayers.map { player ->
                mapOf(
                    "id" to player.id,
                    "name" to player.name,
                    "role" to player.role,
                    "isCaptain" to (player.id == captainId),
                    "isViceCaptain" to (player.id == viceCaptainId)
                )
            },
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseUtils.teamsCollection()
            .add(payload)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage ?: "Failed to save team.") }
    }
}
