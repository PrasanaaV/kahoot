package com.example.kahoot.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.kahoot.R
import com.example.kahoot.utils.ScreenTimeManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        showMainMenu()
    }

    private fun showMainMenu() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, RoleSelectionFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        ScreenTimeManager.startSession()
    }

    override fun onPause() {
        super.onPause()
        ScreenTimeManager.endSession()
    }
}
