package com.example.kahoot.player

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PlayerQuizFragment : Fragment() {
    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null
    private lateinit var questionTextView: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var countdownText: TextView

    companion object {
        fun newInstance(quizId: String): PlayerQuizFragment {
            val f = PlayerQuizFragment()
            f.arguments = Bundle().apply { putString("quizId", quizId) }
            return f
        }
    }

    private var currentQuestionIndex: Int = 0
    private var currentQuestion: Map<String, Any?>? = null
    private var timer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_player_quiz, container, false)
        questionTextView = view.findViewById(R.id.questionTextView)
        optionButtons = listOf(
            view.findViewById(R.id.optionButton1),
            view.findViewById(R.id.optionButton2),
            view.findViewById(R.id.optionButton3),
            view.findViewById(R.id.optionButton4)
        )
        countdownText = view.findViewById(R.id.countdownText)

        quizId = arguments?.getString("quizId")

        listenToQuizChanges()

        optionButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                submitAnswer(i)
            }
        }

        return view
    }

    private fun listenToQuizChanges() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)
        quizRef.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val status = snapshot.getString("status")
            if (status == "ended") {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, ScoreboardFragment.newInstance(qId))
                    .commit()
                return@addSnapshotListener
            }

            val cqi = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val questions = snapshot.get("questions") as? List<Map<String, Any?>>
            if (questions == null || questions.size <= cqi) return@addSnapshotListener

            currentQuestionIndex = cqi
            currentQuestion = questions[cqi]

            val qText = currentQuestion?.get("questionText") as? String ?: ""
            val options = currentQuestion?.get("options") as? List<String> ?: listOf("", "", "", "")
            val timeLimit = currentQuestion?.get("timeLimitSeconds") as? Long ?: 10
            val startTime = currentQuestion?.get("startTime")

            questionTextView.text = qText
            optionButtons.forEachIndexed { i, btn ->
                btn.text = options.getOrNull(i) ?: ""
            }

            if (startTime is com.google.firebase.Timestamp) {
                val questionStart = startTime.toDate().time
                val now = System.currentTimeMillis()
                val timeElapsed = (now - questionStart) / 1000
                val timeRemaining = timeLimit - timeElapsed
                startTimer(timeRemaining.toInt())
            } else {
                countdownText.text = "Waiting..."
            }
        }
    }

    private fun startTimer(seconds: Int) {
        timer?.cancel()
        if (seconds <= 0) {
            countdownText.text = "Time's up!"
            return
        }
        timer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                countdownText.text = "Time's up!"
            }
        }.start()
    }

    private fun submitAnswer(optionIndex: Int) {
        timer?.cancel()

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (quizId == null) {
            Toast.makeText(requireContext(), "Failed to submit answer", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("quizzes").document(quizId!!)
            .collection("responses")
            .document(currentQuestionIndex.toString())
            .collection("answers")
            .document(userId)
            .set(
                mapOf(
                    "optionChosen" to optionIndex,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Answer submitted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to submit answer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayQuizCompleted() {
        questionTextView.text = "Quiz completed!"
        optionButtons.forEach { it.visibility = View.GONE }
        countdownText.visibility = View.GONE
    }
}
