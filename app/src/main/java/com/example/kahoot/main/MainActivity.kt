package com.example.kahoot.main

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kahoot.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        signInAnonymously()
    }

    private fun signInAnonymously() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // User is signed in, we can proceed
                // Here, navigate to either a Host UI or Player UI
                showMainMenu()
            } else {
                // Handle error
                Toast.makeText(this, "Auth failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMainMenu() {
        // For MVP, we can just choose roles: Host or Player
        // You can navigate to different fragments or activities
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, RoleSelectionFragment())
            .commit()
    }
}