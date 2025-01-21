package com.example.kahoot.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.player.PinEntryFragment
import com.google.firebase.auth.FirebaseAuth

class RoleSelectionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_role_selection, container, false)
        val hostButton = view.findViewById<Button>(R.id.hostButton)
        val playerButton = view.findViewById<Button>(R.id.playerButton)

        hostButton.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                // Not logged in -> go to HostLoginFragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, HostLoginFragment())
                    .addToBackStack(null)
                    .commit()
            } else {
                // Already logged in -> go to HostDashboardFragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, HostDashboardFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        playerButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, PinEntryFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}
