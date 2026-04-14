package com.example.cricarena.ui.fantasyteam

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.repository.FirebaseRepository
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

data class SavedFantasySelection(
    val captainId: String,
    val viceCaptainId: String,
    val selectedPlayerIds: Set<String>
)

class FantasyTeamViewModel : ViewModel() {
    private val repository = FirebaseRepository()
    private var playersSource: LiveData<List<FantasyPlayer>>? = null
    private var matchesSource: LiveData<List<Match>>? = null
    private var hasEligibleMatchesLoadedOnce: Boolean = false

    private val _players = MediatorLiveData<List<FantasyPlayer>>(emptyList())
    val players: LiveData<List<FantasyPlayer>> = _players

    private val _eligibleMatches = MediatorLiveData<List<Match>>(emptyList())
    val eligibleMatches: LiveData<List<Match>> = _eligibleMatches

    /** True until the first Firestore snapshot for the match list is applied (same race as Home). */
    private val _eligibleMatchesLoading = MutableLiveData(true)
    val eligibleMatchesLoading: LiveData<Boolean> = _eligibleMatchesLoading

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _loadError = MutableLiveData<String?>(null)
    val loadError: LiveData<String?> = _loadError

    private val _savedTeam = MutableLiveData<SavedFantasySelection?>(null)
    val savedTeam: LiveData<SavedFantasySelection?> = _savedTeam

    private fun teamDocumentId(userId: String, matchId: String): String = "${userId}_$matchId"

    /**
     * Matches available for fantasy (all non-draft matches from Home feed).
     */
    fun observeEligibleMatches() {
        if (matchesSource != null) {
            _eligibleMatchesLoading.value = !hasEligibleMatchesLoadedOnce
            return
        }
        val source = repository.getMatches()
        matchesSource = source
        _eligibleMatches.addSource(source) { list ->
            _eligibleMatches.value = list
                .filter { it.id.isNotBlank() }
                .sortedByDescending {
                    it.lastUpdated?.toDate()?.time
                        ?: it.createdAt?.toDate()?.time
                        ?: 0L
                }
            hasEligibleMatchesLoadedOnce = true
            _eligibleMatchesLoading.value = false
        }
    }

    /**
     * Stops listening to players and clears selection-related state in VM when switching matches.
     */
    fun resetPlayerStreams() {
        playersSource?.let { _players.removeSource(it) }
        playersSource = null
        _players.value = emptyList()
        _savedTeam.value = null
        _loadError.value = null
    }

    fun loadMatchForFantasy(matchId: String) {
        if (matchId.isBlank()) {
            _loadError.value = "Match ID is missing."
            return
        }
        resetPlayerStreams()
        _loading.value = true
        _loadError.value = null
        Log.d(TAG, "loadMatchForFantasy: $matchId")
        val source = repository.getPlayers(matchId)
        playersSource = source
        _players.addSource(source) { list ->
            _players.value = list
            _loading.value = false
        }
    }

    /**
     * Loads the current user's saved fantasy XI for this match, if any.
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
        var docListener: com.google.firebase.firestore.ListenerRegistration? = null
        docListener = teams.document(docId).addSnapshotListener { snap, docErr ->
            if (docErr != null) {
                Log.e(TAG, "Error loading saved team doc for matchId: $matchId", docErr)
                onResult(null, docErr.localizedMessage)
                docListener?.remove()
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                val parsed = parseSavedSelection(snap.data)
                _savedTeam.value = parsed
                onResult(parsed, null)
                docListener?.remove()
                return@addSnapshotListener
            }
            var queryListener: com.google.firebase.firestore.ListenerRegistration? = null
            queryListener = teams
                .whereEqualTo("matchId", matchId)
                .addSnapshotListener { qs, qsErr ->
                    if (qsErr != null) {
                        Log.e(TAG, "Error loading saved team for matchId: $matchId", qsErr)
                        onResult(null, qsErr.localizedMessage)
                        queryListener?.remove()
                        docListener?.remove()
                        return@addSnapshotListener
                    }
                    val doc = qs?.documents
                        .orEmpty()
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
                    queryListener?.remove()
                    docListener?.remove()
                }
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
        matchDisplayName: String,
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
        docRef.get()
            .addOnSuccessListener { snap ->
                val payload = hashMapOf<String, Any>(
                    "userId" to userId,
                    "matchId" to matchId,
                    "matchName" to matchDisplayName.ifBlank { "Match $matchId" },
                    "captainId" to captainId,
                    "viceCaptainId" to viceCaptainId,
                    "players" to selectedPlayers.map { player ->
                        mapOf(
                            "id" to player.id,
                            "name" to player.name,
                            "role" to player.role,
                            "team" to player.team,
                            "isCaptain" to (player.id == captainId),
                            "isViceCaptain" to (player.id == viceCaptainId)
                        )
                    },
                    "totalPoints" to 0.0,
                    "rank" to 0,
                    "status" to "ACTIVE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
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
                Log.e(TAG, "Failed reading team doc before save", e)
                onResult(false, e.localizedMessage ?: "Failed to save team.")
            }
    }

    override fun onCleared() {
        playersSource?.let { _players.removeSource(it) }
        playersSource = null
        matchesSource?.let { _eligibleMatches.removeSource(it) }
        matchesSource = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}
