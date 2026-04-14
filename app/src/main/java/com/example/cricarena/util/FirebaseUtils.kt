package com.example.cricarena.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseUtils {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** Root collection for match documents (subcollections: `players`, `ballEvents`, `playerStats`, …). */
    const val COLLECTION_MATCHES = "Matches"

    fun usersCollection(): CollectionReference = firestore.collection("Users")
    fun matchesCollection(): CollectionReference = firestore.collection(COLLECTION_MATCHES)
    fun teamsCollection(): CollectionReference = firestore.collection("Teams")

    fun userDocument(userId: String): DocumentReference = usersCollection().document(userId)
    fun matchDocument(matchId: String): DocumentReference = matchesCollection().document(matchId)
}
