package com.example.kahoot.player

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.google.firebase.firestore.FirebaseFirestore

class ScoreboardFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null
    private lateinit var scoreListView: ListView
    private lateinit var titleText: TextView

    companion object {
        private const val ARG_QUIZ_ID = "quiz_id"

        fun newInstance(quizId: String): ScoreboardFragment {
            val fragment = ScoreboardFragment()
            val args = Bundle().apply {
                putString(ARG_QUIZ_ID, quizId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString(ARG_QUIZ_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scoreboard, container, false)
        scoreListView = view.findViewById(R.id.scoreListView)
        titleText = view.findViewById(R.id.titleText)
        
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
            val scores = participants.mapNotNull { it["uid"] as? String }
                .associateWith { 0 }
                .toMutableMap()

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
                        
                        // Only count points if the answer is correct (not -1 and matches correct option)
                        if (selectedOption != -1 && selectedOption == correctOption?.toInt()) {
                            scores[participantId] = (scores[participantId] ?: 0) + 1
                        }
                    }
                }

                // Create list items for adapter
                val listItems = scores.map { (participantId, score) ->
                    val participant = participants.find { it["uid"] == participantId }
                    mapOf(
                        "name" to (participant?.get("uid") as? String ?: "Unknown"),
                        "score" to "Score: $score/${questions.size}"
                    )
                }.sortedByDescending { 
                    // Extract the numeric score from the score string for proper sorting
                    val scoreText = it["score"] as String
                    scoreText.substringAfter(": ").substringBefore("/").toIntOrNull() ?: 0
                }

                // Create and set adapter
                if (!isAdded) return@addOnSuccessListener
                val adapter = SimpleAdapter(
                    requireContext(),
                    listItems,
                    android.R.layout.simple_list_item_2,
                    arrayOf("name", "score"),
                    intArrayOf(android.R.id.text1, android.R.id.text2)
                )
                
                scoreListView.adapter = adapter
                titleText.text = "Final Scores"
            }
        }
    }
}
