package com.example.cricarena.ui.fantasyteam

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.data.repository.FirebaseRepository
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

data class SavedFantasySelection(
    val captainId: String,
    val viceCaptainId: String,
    val selectedPlayerIds: Set<String>
)

class FantasyTeamViewModel : ViewModel() {
    private val repository = FirebaseRepository()
    private var playersListener: ListenerRegistration? = null

    private val _players = MutableLiveData<List<FantasyPlayer>>(emptyList())
    val players: LiveData<List<FantasyPlayer>> = _players

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _loadError = MutableLiveData<String?>(null)
    val loadError: LiveData<String?> = _loadError

    private val _savedTeam = MutableLiveData<SavedFantasySelection?>(null)
    val savedTeam: LiveData<SavedFantasySelection?> = _savedTeam

    private fun teamDocumentId(userId: String, matchId: String): String = "${userId}_$matchId"

    fun observePlayers(matchId: String) {
        if (matchId.isBlank()) {
            _loadError.value = "Match ID is missing."
            return
        }
        if (playersListener != null) return

        _loading.value = true
        _loadError.value = null
        Log.d(TAG, "observePlayers called, matchId: $matchId")
        playersListener = repository.getPlayers(
            matchId = matchId,
            onData = { list ->
                _players.value = list
                _loading.value = false
            },
            onError = { message ->
                _loadError.value = message
                _loading.value = false
            }
        )
    }

    /**
     * Loads the current user's saved fantasy XI for this match, if any.
     * Uses a stable doc id userId_matchId; falls back to querying older `.add()` saves.
     */
    fun fetchSavedTeam(
        matchId: String,
        onResult: (SavedFantasySelection?, String?) -> Unit
    ) {
        Log.d(TAG, "Fetching saved team for matchId: $matchId")
        val userId = FirebaseUtils.auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            onResult(null, null)
            return
        }
        val teams = FirebaseUtils.teamsCollection()
        val docId = teamDocumentId(userId, matchId)
        teams.document(docId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val parsed = parseSavedSelection(snap.data)
                    _savedTeam.value = parsed
                    onResult(parsed, null)
                    return@addOnSuccessListener
                }
                teams
                    .whereEqualTo("matchId", matchId)
                    .get()
                    .addOnSuccessListener { qs ->
                        val doc = qs.documents
                            .filter { it.getString("userId") == userId }
                            .maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }
                        if (doc == null) {
                            _savedTeam.value = null
                            onResult(null, null)
                        } else {
                            val parsed = parseSavedSelection(doc.data)
                            _savedTeam.value = parsed
                            onResult(parsed, null)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error loading saved team for matchId: $matchId", e)
                        onResult(null, e.localizedMessage)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading saved team doc for matchId: $matchId", e)
                onResult(null, e.localizedMessage)
            }
    }

    private fun parseSavedSelection(data: Map<String, Any>?): SavedFantasySelection? {
        if (data == null) return null
        val captainId = data["captainId"]?.toString().orEmpty()
        val viceCaptainId = data["viceCaptainId"]?.toString().orEmpty()
        val rawPlayers = data["players"] as? List<*> ?: return null
        val ids = rawPlayers.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            m["id"]?.toString()
        }.toSet()
        if (ids.isEmpty()) return null
        return SavedFantasySelection(
            captainId = captainId,
            viceCaptainId = viceCaptainId,
            selectedPlayerIds = ids
        )
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
        Log.d(TAG, "Saving team for matchId: $matchId, players=${selectedPlayers.size}")

        val docRef = FirebaseUtils.teamsCollection().document(teamDocumentId(userId, matchId))
        val payload = hashMapOf<String, Any>(
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
            "updatedAt" to FieldValue.serverTimestamp()
        )

        docRef.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    payload["createdAt"] = FieldValue.serverTimestamp()
                }
                docRef.set(payload, SetOptions.merge())
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed saving team for matchId: $matchId", e)
                        onResult(false, e.localizedMessage ?: "Failed to save team.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed loading team doc before save for matchId: $matchId", e)
                onResult(false, e.localizedMessage ?: "Failed to save team.")
            }
    }

    override fun onCleared() {
        playersListener?.remove()
        playersListener = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}
