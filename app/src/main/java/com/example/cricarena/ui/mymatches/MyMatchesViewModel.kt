package com.example.cricarena.ui.mymatches

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.repository.FirebaseRepository

class MyMatchesViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel()
