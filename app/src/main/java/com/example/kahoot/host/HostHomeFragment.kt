package com.example.kahoot.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.main.HostQuizzesListFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class HostHomeFragment : Fragment() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_home, container, false)

        bottomNav = view.findViewById(R.id.hostBottomNav)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_quiz -> {
                    // Example: Show HostFragment or your "create quiz" screen
                    replaceChildFragment(HostFragment())
                    true
                }
                R.id.navigation_dashboard -> {
                    // Example: Show HostDashboardFragment or HostQuizzesListFragment
                    replaceChildFragment(HostQuizzesListFragment())
                    true
                }
                R.id.navigation_profile -> {
                    // Example: Show profile screen
                    replaceChildFragment(HostProfileFragment())
                    true
                }
                else -> false
            }
        }

        // Default to the middle item (Dashboard)
        bottomNav.selectedItemId = R.id.navigation_dashboard

        return view
    }

    private fun replaceChildFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.hostHomeContainer, fragment)
            .commit()
    }
}
