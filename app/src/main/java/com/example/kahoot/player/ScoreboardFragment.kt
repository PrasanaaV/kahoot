package com.example.kahoot.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.google.firebase.firestore.FirebaseFirestore

class ScoreboardFragment : Fragment() {
    companion object {
        private const val ARG_QUIZ_ID = "quiz_id"

        fun newInstance(quizId: String): ScoreboardFragment {
            val fragment = ScoreboardFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_QUIZ_ID, quizId)
            }
            return fragment
        }
    }

    private var quizId: String? = null
    private val db = FirebaseFirestore.getInstance()
    
    private lateinit var titleTextView: TextView
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val scoreboardList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString(ARG_QUIZ_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_scoreboard, container, false)
        
        titleTextView = view.findViewById(R.id.titleText)
        listView = view.findViewById(R.id.scoreListView)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, scoreboardList)
        listView.adapter = adapter

        loadScores()
        return view
    }

    private fun loadScores() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.get().addOnSuccessListener { quizSnapshot ->
            if (!isAdded) return@addOnSuccessListener

            val questions = quizSnapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            val participants = quizSnapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()

            // Initialize scores for all participants
            val scores = participants.associate { participant ->
                val uid = participant["uid"] as? String ?: return@associate "" to 0
                val username = participant["username"] as? String ?: uid
                username to 0
            }.toMutableMap()

            // Get all responses
            quizRef.collection("responses").get().addOnSuccessListener { responses ->
                if (!isAdded) return@addOnSuccessListener

                responses.forEach { response ->
                    val participantId = response.getString("participantId") ?: return@forEach
                    val questionIndex = response.getLong("questionIndex")?.toInt() ?: return@forEach
                    val selectedOption = response.getLong("selectedOption")?.toInt() ?: return@forEach
                    
                    if (questionIndex < questions.size) {
                        val question = questions[questionIndex] as? Map<String, Any>
                        val correctOption = question?.get("correctOptionIndex") as? Long
                        
                        // Find participant's username
                        val participant = participants.find { it["uid"] == participantId }
                        val username = participant?.get("username") as? String ?: participantId
                        
                        // Only count points if the answer is correct
                        if (selectedOption != -1 && selectedOption == correctOption?.toInt()) {
                            scores[username] = (scores[username] ?: 0) + 1
                        }
                    }
                }

                // Sort scores by descending order and update UI
                scoreboardList.clear()
                scores.entries.sortedByDescending { it.value }.forEach { (username, score) ->
                    scoreboardList.add("$username: $score/${questions.size}")
                }
                
                titleTextView.text = "Final Scores"
                adapter.notifyDataSetChanged()
            }
        }
    }
}
