package com.example.cricarena.ui.creatematch

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.BallEvent
import com.example.cricarena.data.model.BallEventType
import com.example.cricarena.data.model.LivePlayer
import com.example.cricarena.data.model.LiveTeam
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.data.payload.MatchPayloadBuilder
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue

class LiveScoringViewModel : ViewModel() {

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

    private val events = mutableListOf<BallEvent>()

    private var firestoreMatchId: String? = null
    private var firestoreTeamA: String = ""
    private var firestoreTeamB: String = ""
    private var markedLiveOnFirestore = false

    fun initialize(matchTitle: String, teamA: String, teamB: String, matchPlayers: List<MatchPlayer>) {
        val mappedPlayers = matchPlayers.mapIndexed { index, player ->
            LivePlayer(
                id = MatchPayloadBuilder.stablePlayerId(player.teamName, index + 1, player.name),
                name = player.name,
                role = player.role,
                teamName = player.teamName
            )
        }
        _players.value = mappedPlayers
        _teams.value = mapOf(
            teamA to LiveTeam(teamA),
            teamB to LiveTeam(teamB)
        )
        _matchTitle.value = matchTitle
        _selectedTeam.value = teamA
        firestoreTeamA = teamA
        firestoreTeamB = teamB
        refreshUi()
    }

    /**
     * Call after the match is saved to Firestore so each ball updates totals incrementally on the server.
     */
    fun attachFirestoreMatch(matchId: String, teamA: String, teamB: String) {
        firestoreMatchId = matchId
        firestoreTeamA = teamA
        firestoreTeamB = teamB
        markedLiveOnFirestore = false
    }

    fun clearFirestoreAttachment() {
        firestoreMatchId = null
        markedLiveOnFirestore = false
    }

    fun getAttachedMatchId(): String? = firestoreMatchId

    fun isInitialized(): Boolean = !_selectedTeam.value.isNullOrBlank() && !_players.value.isNullOrEmpty()

    fun setSelectedTeam(teamName: String) {
        _selectedTeam.value = teamName
        refreshUi()
    }

    fun getMatchTitle(): String = _matchTitle.value.orEmpty()

    fun applyRun(playerId: String, runs: Int) {
        updatePlayer(playerId) { p ->
            p.copy(
                runs = p.runs + runs,
                balls = p.balls + 1
            )
        }
        updateTeamForPlayer(playerId, addRuns = runs, addBalls = 1)
        events.add(BallEvent(BallEventType.RUN, playerId, getPlayerTeam(playerId), runs))
        refreshUi()
        syncTeamDelta(playerId, addRuns = runs, addBalls = 1, addWicket = 0)
        syncPlayerStats(playerId, runsDelta = runs.toLong(), ballsDelta = 1, wicketsDelta = 0, catchesDelta = 0)
        maybeMarkLive()
    }

    fun applyWide(playerId: String) {
        updateTeamForPlayer(playerId, addRuns = 1, addBalls = 0)
        events.add(BallEvent(BallEventType.WIDE, playerId, getPlayerTeam(playerId), 1))
        refreshUi()
        syncTeamDelta(playerId, addRuns = 1, addBalls = 0, addWicket = 0)
        maybeMarkLive()
    }

    fun applyNoBall(playerId: String) {
        updateTeamForPlayer(playerId, addRuns = 1, addBalls = 0)
        events.add(BallEvent(BallEventType.NO_BALL, playerId, getPlayerTeam(playerId), 1))
        refreshUi()
        syncTeamDelta(playerId, addRuns = 1, addBalls = 0, addWicket = 0)
        maybeMarkLive()
    }

    fun applyBye(playerId: String) {
        updateTeamForPlayer(playerId, addRuns = 1, addBalls = 1)
        events.add(BallEvent(BallEventType.BYE, playerId, getPlayerTeam(playerId), 1))
        refreshUi()
        syncTeamDelta(playerId, addRuns = 1, addBalls = 1, addWicket = 0)
        maybeMarkLive()
    }

    fun applyWicket(playerId: String) {
        updatePlayer(playerId) { it.copy(isOut = true, balls = it.balls + 1) }
        updateTeamForPlayer(playerId, addRuns = 0, addBalls = 1, addWicket = 1)
        events.add(BallEvent(BallEventType.WICKET, playerId, getPlayerTeam(playerId)))
        refreshUi()
        syncTeamDelta(playerId, addRuns = 0, addBalls = 1, addWicket = 1)
        syncPlayerStats(playerId, runsDelta = 0, ballsDelta = 1, wicketsDelta = 1, catchesDelta = 0)
        maybeMarkLive()
    }

    fun applyCatch(playerId: String) {
        updatePlayer(playerId) { it.copy(catches = it.catches + 1, isOut = true, balls = it.balls + 1) }
        updateTeamForPlayer(playerId, addRuns = 0, addBalls = 1, addWicket = 1)
        events.add(BallEvent(BallEventType.CATCH, playerId, getPlayerTeam(playerId)))
        refreshUi()
        syncTeamDelta(playerId, addRuns = 0, addBalls = 1, addWicket = 1)
        syncPlayerStats(playerId, runsDelta = 0, ballsDelta = 1, wicketsDelta = 1, catchesDelta = 1)
        maybeMarkLive()
    }

    private fun updatePlayer(playerId: String, transform: (LivePlayer) -> LivePlayer) {
        val current = _players.value.orEmpty().toMutableList()
        val index = current.indexOfFirst { it.id == playerId }
        if (index >= 0) {
            current[index] = transform(current[index])
            _players.value = current
        }
    }

    private fun updateTeamForPlayer(
        playerId: String,
        addRuns: Int,
        addBalls: Int,
        addWicket: Int = 0
    ) {
        val teamName = getPlayerTeam(playerId)
        val current = _teams.value.orEmpty().toMutableMap()
        val team = current[teamName] ?: return
        current[teamName] = team.copy(
            totalRuns = team.totalRuns + addRuns,
            wickets = team.wickets + addWicket,
            ballsDelivered = team.ballsDelivered + addBalls
        )
        _teams.value = current
    }

    private fun getPlayerTeam(playerId: String): String {
        return _players.value.orEmpty().firstOrNull { it.id == playerId }?.teamName.orEmpty()
    }

    private fun liveScoreKeyForTeam(teamName: String): String? = when (teamName) {
        firestoreTeamA -> "liveScoreA"
        firestoreTeamB -> "liveScoreB"
        else -> null
    }

    private fun syncTeamDelta(
        playerId: String,
        addRuns: Int,
        addBalls: Int,
        addWicket: Int
    ) {
        val mid = firestoreMatchId ?: return
        val teamName = getPlayerTeam(playerId)
        val key = liveScoreKeyForTeam(teamName) ?: return
        val ref = FirebaseUtils.matchesCollection().document(mid)
        val updates = mutableMapOf<String, Any>()
        if (addRuns != 0) {
            updates["$key.runs"] = FieldValue.increment(addRuns.toLong())
        }
        if (addBalls != 0) {
            updates["$key.balls"] = FieldValue.increment(addBalls.toLong())
        }
        if (addWicket != 0) {
            updates["$key.wickets"] = FieldValue.increment(addWicket.toLong())
        }
        if (updates.isEmpty()) return
        ref.update(updates)
    }

    private fun syncPlayerStats(
        playerId: String,
        runsDelta: Long,
        ballsDelta: Long,
        wicketsDelta: Long,
        catchesDelta: Long
    ) {
        val mid = firestoreMatchId ?: return
        if (runsDelta == 0L && ballsDelta == 0L && wicketsDelta == 0L && catchesDelta == 0L) return
        val updates = mutableMapOf<String, Any>()
        if (runsDelta != 0L) updates["runs"] = FieldValue.increment(runsDelta)
        if (ballsDelta != 0L) updates["balls"] = FieldValue.increment(ballsDelta)
        if (wicketsDelta != 0L) updates["wickets"] = FieldValue.increment(wicketsDelta)
        if (catchesDelta != 0L) updates["catches"] = FieldValue.increment(catchesDelta)
        FirebaseUtils.matchesCollection().document(mid)
            .collection("playerStats")
            .document(playerId)
            .update(updates)
    }

    private fun maybeMarkLive() {
        val mid = firestoreMatchId ?: return
        if (markedLiveOnFirestore) return
        markedLiveOnFirestore = true
        FirebaseUtils.matchesCollection().document(mid)
            .update("status", "LIVE")
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
}
