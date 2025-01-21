package com.example.kahoot.player

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.kahoot.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
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
    private lateinit var confettiAnimation: LottieAnimationView
    private lateinit var correctAnswersChart: BarChart
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
        confettiAnimation = view.findViewById(R.id.confettiAnimation)
        correctAnswersChart = view.findViewById(R.id.correctAnswersChart)

        setupChart()

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, scoreboardList)
        listView.adapter = adapter

        confettiAnimation.apply {
            playAnimation()
            repeatCount = 3
        }

        loadScores()
        return view
    }

    private fun setupChart() {
        correctAnswersChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }
            
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
            }
            
            axisRight.isEnabled = false
            
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        confettiAnimation.cancelAnimation()
    }

    private fun loadScores() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.get().addOnSuccessListener { quizSnapshot ->
            if (!isAdded) return@addOnSuccessListener

            val questions = quizSnapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            val participants = quizSnapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()

            val scores = participants.associate { participant ->
                val uid = participant["uid"] as? String ?: return@associate "" to 0
                val username = participant["username"] as? String ?: uid
                username to 0
            }.toMutableMap()

            val correctAnswersPerQuestion = MutableList(questions.size) { 0 }

            quizRef.collection("responses").get().addOnSuccessListener { responses ->
                if (!isAdded) return@addOnSuccessListener

                responses.forEach { response ->
                    val participantId = response.getString("participantId") ?: return@forEach
                    val questionIndex = response.getLong("questionIndex")?.toInt() ?: return@forEach
                    val selectedOption = response.getLong("selectedOption")?.toInt() ?: return@forEach
                    
                    if (questionIndex < questions.size) {
                        val question = questions[questionIndex] as? Map<String, Any>
                        val correctOption = question?.get("correctOptionIndex") as? Long
                        
                        val participant = participants.find { it["uid"] == participantId }
                        val username = participant?.get("username") as? String ?: participantId

                        if (correctOption?.toInt() == selectedOption) {
                            scores[username] = (scores[username] ?: 0) + 1
                            correctAnswersPerQuestion[questionIndex]++
                        }
                    }
                }

                scoreboardList.clear()
                scores.entries.sortedByDescending { it.value }.forEach { (username, score) ->
                    scoreboardList.add("$username: $score points")
                }
                adapter.notifyDataSetChanged()

                updateChart(correctAnswersPerQuestion, participants.size)
            }
        }
    }

    private fun updateChart(correctAnswersPerQuestion: List<Int>, totalParticipants: Int) {
        val entries = correctAnswersPerQuestion.mapIndexed { index, count ->
            BarEntry(index.toFloat(), count.toFloat())
        }

        val dataSet = BarDataSet(entries, "Correct Answers").apply {
            color = Color.rgb(104, 241, 175)
            valueTextSize = 12f
        }

        val barData = BarData(dataSet)
        correctAnswersChart.data = barData

        val xLabels = correctAnswersPerQuestion.indices.map { "Q${it + 1}" }
        correctAnswersChart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)

        correctAnswersChart.axisLeft.axisMaximum = totalParticipants.toFloat()

        correctAnswersChart.invalidate()
    }
}
