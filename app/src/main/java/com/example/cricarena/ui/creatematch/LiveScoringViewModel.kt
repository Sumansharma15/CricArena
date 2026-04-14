package com.example.cricarena.ui.creatematch

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.BallEvent
import com.example.cricarena.data.model.BallEventType
import com.example.cricarena.data.model.LivePlayer
import com.example.cricarena.data.model.LiveTeam
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.data.payload.MatchPayloadBuilder
import com.example.cricarena.data.scoring.ScoringManager

class LiveScoringViewModel : ViewModel() {
    private val scoringManager = ScoringManager()

    private val _players = MutableLiveData<List<LivePlayer>>(emptyList())
    val players: LiveData<List<LivePlayer>> = _players

    private val _selectedTeam = MutableLiveData<String>("")
    val selectedTeam: LiveData<String> = _selectedTeam

    private val _teams = MutableLiveData<Map<String, LiveTeam>>(emptyMap())
    private val _matchTitle = MutableLiveData("")
    private val _scoreLine = MutableLiveData("0/0 (0.0 overs)")
    val scoreLine: LiveData<String> = _scoreLine

    private val _currentBatsman = MutableLiveData("-")
    val currentBatsman: LiveData<String> = _currentBatsman

    private val _currentBowler = MutableLiveData("-")
    val currentBowler: LiveData<String> = _currentBowler

    private val _filteredPlayers = MutableLiveData<List<LivePlayer>>(emptyList())
    val filteredPlayers: LiveData<List<LivePlayer>> = _filteredPlayers

    private val ballEvents = MediatorLiveData<List<BallEvent>>(emptyList())
    private var ballEventsSource: LiveData<List<BallEvent>>? = null
    private var localBallEvents = mutableListOf<BallEvent>()
    private var basePlayers: List<LivePlayer> = emptyList()

    private var firestoreMatchId: String? = null
    private var firestoreTeamA: String = ""
    private var firestoreTeamB: String = ""

    /**
     * Builds live players from the squad. Reuses existing [LivePlayer] stats when the stable id matches
     * so runs/balls accumulate across balls (opening the scoring screen must not reset totals).
     */
    fun initialize(matchTitle: String, teamA: String, teamB: String, matchPlayers: List<MatchPlayer>) {
        val mappedPlayers = matchPlayers.mapIndexed { index, player ->
            val id = MatchPayloadBuilder.stablePlayerId(player.teamName, index + 1, player.name)
            LivePlayer(
                id = id,
                name = player.name,
                role = player.role,
                teamName = player.teamName
            )
        }
        basePlayers = mappedPlayers

        _teams.value = mapOf(
            teamA to LiveTeam(teamA),
            teamB to LiveTeam(teamB)
        )
        _matchTitle.value = matchTitle
        _selectedTeam.value = teamA
        firestoreTeamA = teamA
        firestoreTeamB = teamB
        applyRecalculatedState(ballEvents.value.orEmpty())
    }

    fun getPlayerState(playerId: String): LivePlayer? =
        _players.value.orEmpty().firstOrNull { it.id == playerId }

    /**
     * Call after the match is saved to Firestore so each ball updates totals incrementally on the server.
     */
    fun attachFirestoreMatch(matchId: String, teamA: String, teamB: String) {
        firestoreMatchId = matchId
        firestoreTeamA = teamA
        firestoreTeamB = teamB
        observeBallEvents(matchId)
    }

    fun clearFirestoreAttachment() {
        ballEventsSource?.let { ballEvents.removeSource(it) }
        ballEventsSource = null
        firestoreMatchId = null
        localBallEvents.clear()
        ballEvents.value = emptyList()
    }

    fun getAttachedMatchId(): String? = firestoreMatchId

    fun isInitialized(): Boolean = !_selectedTeam.value.isNullOrBlank() && !_players.value.isNullOrEmpty()

    fun setSelectedTeam(teamName: String) {
        _selectedTeam.value = teamName
        refreshUi()
    }

    fun getMatchTitle(): String = _matchTitle.value.orEmpty()

    fun applyRun(playerId: String, runs: Int) {
        recordEvent(playerId = playerId, type = BallEventType.RUN, runs = runs)
    }

    fun applyWide(playerId: String) {
        recordEvent(playerId = playerId, type = BallEventType.WIDE, runs = 1)
    }

    fun applyNoBall(playerId: String) {
        recordEvent(playerId = playerId, type = BallEventType.NO_BALL, runs = 1)
    }

    fun applyBye(playerId: String) {
        recordEvent(playerId = playerId, type = BallEventType.BYE, runs = 1)
    }

    fun applyWicket(playerId: String) {
        recordEvent(playerId = playerId, type = BallEventType.WICKET, runs = 0)
    }

    fun applyCatch(playerId: String) {
        recordEvent(playerId = playerId, type = BallEventType.CATCH, runs = 0)
    }

    private fun observeBallEvents(matchId: String) {
        ballEventsSource?.let { ballEvents.removeSource(it) }
        val source = scoringManager.getBallEvents(matchId)
        ballEventsSource = source
        ballEvents.addSource(source) { events ->
            ballEvents.value = events
            applyRecalculatedState(events)
        }
    }

    private fun recordEvent(playerId: String, type: BallEventType, runs: Int) {
        val teamName = getPlayerTeam(playerId)
        if (playerId.isBlank() || teamName.isBlank()) return
        val event = BallEvent(
            type = type,
            playerId = playerId,
            teamName = teamName,
            runs = runs
        )
        val matchId = firestoreMatchId
        if (matchId.isNullOrBlank()) {
            localBallEvents.add(event)
            applyRecalculatedState(localBallEvents)
            return
        }
        scoringManager.addBallEvent(matchId, event) { success, error ->
            if (!success) {
                Log.e(ERROR_TAG, error ?: "Failed to append ball event")
            }
        }
    }

    private fun getPlayerTeam(playerId: String): String {
        return basePlayers.firstOrNull { it.id == playerId }?.teamName.orEmpty()
    }

    private fun applyRecalculatedState(events: List<BallEvent>) {
        val result = scoringManager.recalculateMatchStats(events, basePlayers, firestoreTeamA, firestoreTeamB)
        _players.value = result.players
        _teams.value = result.teams
        refreshUi()
        val matchId = firestoreMatchId
        if (!matchId.isNullOrBlank()) {
            scoringManager.persistDerivedStats(
                matchId = matchId,
                teamA = firestoreTeamA,
                teamB = firestoreTeamB,
                result = result
            )
        }
    }

    private fun refreshUi() {
        val selectedTeamName = _selectedTeam.value.orEmpty()
        val selectedTeam = _teams.value?.get(selectedTeamName)
        if (selectedTeam != null) {
            _scoreLine.value =
                "${selectedTeam.totalRuns}/${selectedTeam.wickets} (${toOvers(selectedTeam.ballsDelivered)} overs)"
        }
        val filtered = _players.value.orEmpty().filter { it.teamName == selectedTeamName }
        _filteredPlayers.value = filtered

        val striker = filtered.firstOrNull { !it.isOut }?.name ?: "-"
        _currentBatsman.value = striker

        val oppositionPlayers = _players.value.orEmpty().filter { it.teamName != selectedTeamName }
        val bowler = oppositionPlayers.firstOrNull { it.role.equals("Bowler", true) }?.name
            ?: oppositionPlayers.firstOrNull()?.name
            ?: "-"
        _currentBowler.value = bowler
    }

    private fun toOvers(totalBalls: Int): String {
        val overs = totalBalls / 6
        val balls = totalBalls % 6
        return "$overs.$balls"
    }

    override fun onCleared() {
        ballEventsSource?.let { ballEvents.removeSource(it) }
        ballEventsSource = null
        super.onCleared()
    }

    companion object {
        private const val ERROR_TAG = "FIREBASE_ERROR"
    }
}
