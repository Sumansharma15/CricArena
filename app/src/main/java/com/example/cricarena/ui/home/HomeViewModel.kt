package com.example.cricarena.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.repository.FirebaseRepository
import com.google.firebase.firestore.ListenerRegistration

class HomeViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _matches = MutableLiveData<List<Match>>(emptyList())
    val matches: LiveData<List<Match>> = _matches

    private val _loadError = MutableLiveData<String?>(null)
    val loadError: LiveData<String?> = _loadError

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private var matchesListener: ListenerRegistration? = null

    fun loadMatches() {
        if (matchesListener != null) return
        _loading.value = true
        Log.d(TAG, "Starting matches listener")
        matchesListener = repository.getMatches(
            onData = { matchList ->
                _matches.value = matchList
                _loadError.value = null
                _loading.value = false
            },
            onError = { message ->
                _loadError.value = message
                _loading.value = false
            }
        )
    }

    override fun onCleared() {
        matchesListener?.remove()
        matchesListener = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "FIREBASE_DEBUG"
    }
}
