package com.example.kahoot.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

class FirebaseRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // ...
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun signInAnonymously(onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        if (auth.currentUser != null) {
            // Already signed in
            onSuccess()
        } else {
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure(task.exception ?: Exception("Unknown error"))
                    }
                }
        }
    }

    fun generateUniquePin(onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val pin = (100000..999999).random().toString()
        db.collection("quizzes").whereEqualTo("pincode", pin).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onSuccess(pin)
                } else {
                    generateUniquePin(onSuccess, onFailure)
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun generateQuizId(): String {
        return db.collection("quizzes").document().id
    }

    fun createQuiz(
        quizId: String,
        quizData: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("quizzes").document(quizId).set(quizData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    // Other utility methods as needed...
}
