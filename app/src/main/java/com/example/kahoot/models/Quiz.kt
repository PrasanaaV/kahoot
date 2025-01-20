package com.example.kahoot.models

import com.google.firebase.firestore.FieldValue

data class Quiz(
    val hostId: String,
    val pincode: String,
    val questions: List<Question> = emptyList(),
    val participants: List<Map<String, Any>> = emptyList(),
    val status: String = "open_for_join",
    val createdAt: Any = FieldValue.serverTimestamp()
)
