package com.example.kahoot.models

data class Question(
    val questionText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val timeLimitSeconds: Int
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "questionText" to questionText,
            "options" to options,
            "correctOptionIndex" to correctOptionIndex,
            "timeLimitSeconds" to timeLimitSeconds
        )
    }
}   