package com.example.cricarena.data.model

import com.google.firebase.Timestamp

data class User(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val createdAt: Timestamp? = null
)
