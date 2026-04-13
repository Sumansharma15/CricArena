package com.example.cricarena.ui.fantasyteam

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.repository.FirebaseRepository

class FantasyTeamViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel()
