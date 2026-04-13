package com.example.cricarena.ui.creatematch

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.repository.FirebaseRepository

class CreateMatchViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel()
