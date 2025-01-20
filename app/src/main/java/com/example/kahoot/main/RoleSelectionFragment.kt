package com.example.kahoot.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.kahoot.host.HostFragment
import com.example.kahoot.player.PinEntryFragment
import com.example.kahoot.R

class RoleSelectionFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_role_selection, container, false)
        val hostButton = view.findViewById<Button>(R.id.hostButton)
        val playerButton = view.findViewById<Button>(R.id.playerButton)

        hostButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HostFragment())
                .addToBackStack(null)
                .commit()
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
