package com.example.kahoot.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.host.HostFragment

class HostDashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_dashboard, container, false)

        val createQuizButton = view.findViewById<Button>(R.id.createQuizButton)
        val viewQuizzesButton = view.findViewById<Button>(R.id.viewQuizzesButton)

        createQuizButton.setOnClickListener {
            // Navigate to HostFragment for creating a new quiz
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HostFragment())
                .addToBackStack(null)
                .commit()
        }

        viewQuizzesButton.setOnClickListener {
            // Navigate to HostQuizzesListFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, HostQuizzesListFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }
}
