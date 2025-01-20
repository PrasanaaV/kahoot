package com.example.kahoot.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot

class FirebaseRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // Get current user ID
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Sign in anonymously
    fun signInAnonymously(onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception ?: Exception("Unknown error"))
                }
            }
    }

    // Generate a unique PIN
    fun generateUniquePin(onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val pin = (100000..999999).random().toString()
        db.collection("quizzes").whereEqualTo("pincode", pin).get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onSuccess(pin)
                } else {
                    generateUniquePin(onSuccess, onFailure) // Retry if PIN exists
                }
            }
            .addOnFailureListener(onFailure)
    }

    // Generate a unique quiz ID
    fun generateQuizId(): String = db.collection("quizzes").document().id

    // Create a new quiz
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

    // Get a quiz by PIN
    fun getQuizByPin(
        pin: String,
        onSuccess: (QuerySnapshot) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("quizzes").whereEqualTo("pincode", pin).limit(1).get()
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure)
    }

    // Update quiz status
    fun updateQuizStatus(
        quizId: String,
        status: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("quizzes").document(quizId)
            .update("status", status)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    // Add a question to a quiz
    fun addQuestionToQuiz(
        quizId: String,
        question: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("quizzes").document(quizId)
            .update("questions", FieldValue.arrayUnion(question))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    // Get a quiz by ID
    fun getQuiz(
        quizId: String,
        onSuccess: (DocumentSnapshot) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("quizzes").document(quizId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onSuccess(doc)
                } else {
                    onFailure(Exception("Quiz not found"))
                }
            }
            .addOnFailureListener(onFailure)
    }

    // Submit an answer
    fun submitAnswer(
        quizId: String,
        questionIndex: Int,
        userId: String,
        optionIndex: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val responseData = mapOf(
            "optionChosen" to optionIndex,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("quizzes").document(quizId)
            .collection("responses")
            .document(questionIndex.toString())
            .collection("answers")
            .document(userId)
            .set(responseData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}
