package com.example.kahoot.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*

object ScreenTimeManager {
    private val auth = FirebaseAuth.getInstance()
    private var sessionStartTime: Long = 0

    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
    }

    fun endSession() {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        if (sessionDuration > 0) {
            saveScreenTime(sessionDuration)
        }
    }

    private fun saveScreenTime(duration: Long) {
        val userId = auth.currentUser?.uid ?: return
        
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)

        val db = FirebaseFirestore.getInstance()
        val screenTimeRef = db.collection("screenTime")
            .document(userId)
            .collection("months")
            .document(month.toString())

        db.runTransaction { transaction ->
            val snapshot = transaction.get(screenTimeRef)
            val currentTime = if (snapshot.exists()) {
                snapshot.getLong("totalTime") ?: 0
            } else {
                0
            }
            
            transaction.set(screenTimeRef, mapOf(
                "totalTime" to currentTime + duration,
                "month" to month,
                "lastUpdated" to Date()
            ))
        }
    }

    fun getScreenTimeData(onSuccess: (List<MonthScreenTime>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        
        val db = FirebaseFirestore.getInstance()
        
        db.collection("screenTime")
            .document(userId)
            .collection("months")
            .orderBy("month", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val screenTimes = documents.mapNotNull { doc ->
                    val totalTime = doc.getLong("totalTime") ?: return@mapNotNull null
                    val month = doc.getLong("month")?.toInt() ?: return@mapNotNull null
                    MonthScreenTime(month, totalTime)
                }
                onSuccess(screenTimes)
            }
            .addOnFailureListener { e ->
                Log.e("ScreenTimeManager", "Error getting screen time data", e)
            }
    }

    fun simulateScreenTimeData(context: Context, onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Error: Not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val db = FirebaseFirestore.getInstance()
            val batch = db.batch()
            
            repeat(12) { month ->
                val randomHours = (1..5).random()
                val screenTime = randomHours * 60 * 60 * 1000L
                
                val docRef = db.collection("screenTime")
                    .document(userId)
                    .collection("months")
                    .document(month.toString())
                
                batch.set(docRef, mapOf(
                    "totalTime" to screenTime,
                    "month" to month,
                    "lastUpdated" to Date()
                ))
            }
            
            batch.commit()
                .addOnSuccessListener {
                    Toast.makeText(context, "Data simulated successfully!", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

data class MonthScreenTime(
    val month: Int,
    val totalTime: Long // milliseconds
)
