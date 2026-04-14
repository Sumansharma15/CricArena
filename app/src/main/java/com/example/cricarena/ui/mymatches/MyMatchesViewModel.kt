package com.example.cricarena.ui.mymatches

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MyMatchItem
import com.example.cricarena.data.model.UserTeamMatch
import com.example.cricarena.data.repository.FirebaseRepository

class MyMatchesViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {
    private val _items = MediatorLiveData<List<MyMatchItem>>(emptyList())
    val items: LiveData<List<MyMatchItem>> = _items

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var selectedTab: MyMatchesTab = MyMatchesTab.JOINED
    private var matchesSource: LiveData<List<Match>>? = null
    private var teamsSource: LiveData<List<UserTeamMatch>>? = null
    private var allMatches: List<Match> = emptyList()
    private var userTeams: List<UserTeamMatch> = emptyList()

    fun setTab(tab: MyMatchesTab) {
        selectedTab = tab
        recompute()
    }

    fun observeMatches() {
        val userId = repository.auth.currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            _error.value = "Please login to view matches."
            _items.value = emptyList()
            _loading.value = false
            return
        }
        if (matchesSource != null || teamsSource != null) return
        _loading.value = true
        _error.value = null

        matchesSource = repository.getMatches().also { src ->
            _items.addSource(src) { matches ->
                allMatches = matches
                recompute()
            }
        }
        teamsSource = repository.getUserTeams(userId).also { src ->
            _items.addSource(src) { teams ->
                userTeams = teams
                recompute()
            }
        }
    }

    private fun recompute() {
        val userId = repository.auth.currentUser?.uid.orEmpty()
        val matchesById = allMatches.associateBy { it.id }
        val joined = userTeams.mapNotNull { team ->
            val match = matchesById[team.matchId]
            val name = team.matchName.ifBlank { match?.title.orEmpty().ifBlank { "Match ${team.matchId}" } }
            MyMatchItem(
                matchId = team.matchId,
                matchName = name,
                status = team.status.ifBlank { matchStatusText(match) },
                pointsEarned = team.totalPoints,
                rank = team.rank
            )
        }
        val created = allMatches
            .filter { match -> userId.isNotBlank() && isCreatedByUser(match, userId) }
            .map { match ->
                MyMatchItem(
                    matchId = match.id,
                    matchName = match.title.ifBlank { "${match.teamA} vs ${match.teamB}" },
                    status = match.status.name,
                    pointsEarned = 0.0,
                    rank = 0
                )
            }
        val tabItems = when (selectedTab) {
            MyMatchesTab.JOINED -> joined
            MyMatchesTab.CREATED -> created
            MyMatchesTab.COMPLETED -> joined.filter { item ->
                val m = matchesById[item.matchId]
                item.status.equals("COMPLETED", ignoreCase = true) ||
                    m?.status?.name?.equals("COMPLETED", ignoreCase = true) == true
            }
        }
        _items.value = tabItems.distinctBy { it.matchId }
        _loading.value = false
        Log.d(TAG, "MyMatches updated for tab=${selectedTab.name}, size=${tabItems.size}")
    }

    private fun matchStatusText(match: Match?): String {
        if (match == null) return "JOINED"
        return match.status.name
    }

    private fun isCreatedByUser(match: Match, userId: String): Boolean {
        return match.createdBy == userId
    }

    override fun onCleared() {
        matchesSource?.let { _items.removeSource(it) }
        teamsSource?.let { _items.removeSource(it) }
        matchesSource = null
        teamsSource = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}
