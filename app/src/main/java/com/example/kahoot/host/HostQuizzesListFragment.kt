package com.example.kahoot.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.host.HostLobbyFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HostQuizzesListFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    private val quizzesData = mutableListOf<Pair<String, String>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_quizzes_list, container, false)
        listView = view.findViewById(R.id.hostQuizzesListView)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val (quizId, quizPin) = quizzesData[position]
            val lobbyFragment = HostLobbyFragment.newInstance(quizId, quizPin)
            parentFragmentManager.beginTransaction()
                .replace(R.id.hostHomeContainer, lobbyFragment)
                .addToBackStack(null)
                .commit()
        }

        loadHostQuizzes()
        return view
    }

    private fun loadHostQuizzes() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("quizzes")
            .whereEqualTo("hostId", userId)
            .get()
            .addOnSuccessListener { snap ->
                val itemsToDisplay = mutableListOf<String>()
                quizzesData.clear()

                for (doc in snap.documents) {
                    val quizId = doc.id
                    val pincode = doc.getString("pincode") ?: "N/A"
                    val status = doc.getString("status") ?: "N/A"

                    val displayStr = "PIN: $pincode  (Status: $status)"
                    itemsToDisplay.add(displayStr)

                    quizzesData.add(quizId to pincode)
                }

                adapter.clear()
                adapter.addAll(itemsToDisplay)
                adapter.notifyDataSetChanged()

                if (itemsToDisplay.isEmpty()) {
                    Toast.makeText(requireContext(), "No quizzes found.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load quizzes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
