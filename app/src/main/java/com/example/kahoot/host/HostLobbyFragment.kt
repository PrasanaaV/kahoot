package com.example.kahoot.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore

class HostLobbyFragment : Fragment() {

    private lateinit var pinTextView: TextView
    private lateinit var participantsListView: ListView
    private lateinit var launchQuizButton: Button

    private var quizId: String? = null
    private var quizPin: String? = null

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: ArrayAdapter<String>
    private val participantsNames = mutableListOf<String>() // or store only UIDs if you want

    companion object {
        private const val ARG_QUIZ_ID = "arg_quiz_id"
        private const val ARG_PIN = "arg_pin"

        fun newInstance(quizId: String, pin: String): HostLobbyFragment {
            val fragment = HostLobbyFragment()
            val args = Bundle().apply {
                putString(ARG_QUIZ_ID, quizId)
                putString(ARG_PIN, pin)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString(ARG_QUIZ_ID)
        quizPin = arguments?.getString(ARG_PIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_lobby, container, false)

        pinTextView = view.findViewById(R.id.pinTextView)
        participantsListView = view.findViewById(R.id.participantsListView)
        launchQuizButton = view.findViewById(R.id.launchQuizButton)

        pinTextView.text = "Quiz PIN: $quizPin"

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, participantsNames)
        participantsListView.adapter = adapter

        launchQuizButton.setOnClickListener {
            launchQuiz()
        }

        listenForParticipants()

        return view
    }

    private fun listenForParticipants() {
        val qId = quizId ?: return
        val quizRef = firestore.collection("quizzes").document(qId)

        // Listen to any changes in the quiz doc, including "participants"
        quizRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()
            participantsNames.clear()

            // If you store more info for each participant, you can parse them accordingly
            for (participant in participants) {
                val uid = participant["uid"] as? String ?: "Unknown"
                participantsNames.add(uid)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun launchQuiz() {
        val qId = quizId ?: return
        val quizRef = firestore.collection("quizzes").document(qId)

        quizRef.update("status", Constants.STATUS_STARTED)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Quiz launched!", Toast.LENGTH_SHORT).show()
                // Optionally navigate to a "HostQuestionFragment" or keep on this screen
                // For now, we’ll just stay here. Players will see the quiz status is "started".
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to launch quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
