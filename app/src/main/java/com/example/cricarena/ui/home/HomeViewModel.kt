package com.example.cricarena.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.repository.FirebaseRepository

class HomeViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _matches = MediatorLiveData<List<Match>>(emptyList())
    val matches: LiveData<List<Match>> = _matches

    private val _loadError = MutableLiveData<String?>(null)
    val loadError: LiveData<String?> = _loadError

    /** True until the first Firestore snapshot for [matches] arrives (avoids empty UI flash). */
    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private var matchesSource: LiveData<List<Match>>? = null
    private var hasLoadedAtLeastOnce: Boolean = false

    fun loadMatches() {
        if (matchesSource != null) {
            _loading.value = !hasLoadedAtLeastOnce
            return
        }
        _loading.value = true
        Log.d(TAG, "Fetching matches")
        val source = repository.getMatches()
        matchesSource = source
        _matches.addSource(source) { matchList ->
            // Apply list before clearing loading so HomeFragment observers never see
            // loading=false with a stale empty list (would flash empty / hide rows).
            _matches.value = matchList.sortedByDescending {
                it.lastUpdated?.toDate()?.time
                    ?: it.createdAt?.toDate()?.time
                    ?: 0L
            }
            hasLoadedAtLeastOnce = true
            _loadError.value = null
            _loading.value = false
        }
    }

    fun deleteMatch(
        matchId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        repository.deleteMatch(matchId) { success, error ->
            if (!success) {
                _loadError.value = error ?: "Failed to delete match."
            }
            onResult(success, error)
        }
    }

    override fun onCleared() {
        matchesSource?.let { _matches.removeSource(it) }
        matchesSource = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}
