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
        // We no longer sign in anonymously here.
        // Instead, let the host log in or register from the new HostLoginFragment.
        showMainMenu()
    }

    private fun showMainMenu() {
        // For MVP, we can just choose roles: Host or Player
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, RoleSelectionFragment())
            .commit()
    }
}
