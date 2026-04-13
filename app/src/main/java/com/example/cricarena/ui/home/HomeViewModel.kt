package com.example.cricarena.ui.home

import androidx.lifecycle.ViewModel
import com.example.cricarena.data.repository.FirebaseRepository

class HomeViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel()
