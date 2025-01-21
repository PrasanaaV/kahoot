package com.example.kahoot.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
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
        listView = view.findViewById(R.id.scoreboardListView)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, scoreboardList)
        listView.adapter = adapter

        loadScores() // Retrieve participants with username & score
        return view
    }

    private fun loadScores() {
        val qId = quizId ?: return
        db.collection("quizzes").document(qId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val participants = doc.get("participants") as? List<Map<String, Any>> ?: emptyList()
                    scoreboardList.clear()

                    for (p in participants) {
                        val username = p["username"] as? String ?: "Unknown"
                        val score = (p["score"] as? Long)?.toInt() ?: 0
                        scoreboardList.add("$username - Score: $score")
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }
}
