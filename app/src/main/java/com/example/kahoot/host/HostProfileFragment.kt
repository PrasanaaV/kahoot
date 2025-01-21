package com.example.kahoot.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.main.RoleSelectionFragment
import com.google.firebase.auth.FirebaseAuth

class HostProfileFragment : Fragment() {

    private lateinit var emailTextView: TextView
    private lateinit var logoutButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_profile, container, false)

        emailTextView = view.findViewById(R.id.hostEmailTextView)
        logoutButton = view.findViewById(R.id.hostLogoutButton)

        // Show current user’s email
        val user = FirebaseAuth.getInstance().currentUser
        emailTextView.text = if (user?.email != null) {
            "Logged in as: ${user.email}"
        } else {
            "No user email."
        }

        // Logout
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            requireActivity().supportFragmentManager.popBackStack(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, RoleSelectionFragment())
                .commit()
        }
        return view
    }
}
