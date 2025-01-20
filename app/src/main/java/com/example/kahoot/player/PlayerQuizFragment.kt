package com.example.kahoot.player

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects

class PlayerQuizFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null

    // UI
    private lateinit var waitingLayout: TextView
    private lateinit var questionLayout: LinearLayout
    private lateinit var questionTextView: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var countdownText: TextView

    // Current question data
    private var currentQuestionIndex: Int = 0
    private var currentQuestion: Map<String, Any>? = null
    private var timer: CountDownTimer? = null
    private var quizStatus: String = ""

    companion object {
        private const val ARG_QUIZ_ID = "arg_quiz_id"

        fun newInstance(quizId: String): PlayerQuizFragment {
            val fragment = PlayerQuizFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_QUIZ_ID, quizId)
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString(ARG_QUIZ_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_player_quiz, container, false)

        waitingLayout = view.findViewById(R.id.waitingTextView)
        questionLayout = view.findViewById(R.id.questionLayout)
        questionTextView = view.findViewById(R.id.questionTextView)
        countdownText = view.findViewById(R.id.countdownText)

        optionButtons = listOf(
            view.findViewById(R.id.optionButton1),
            view.findViewById(R.id.optionButton2),
            view.findViewById(R.id.optionButton3),
            view.findViewById(R.id.optionButton4)
        )

        optionButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                submitAnswer(index)
            }
        }

        listenToQuizChanges()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
    }

    private fun listenToQuizChanges() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            quizStatus = snapshot.getString("status") ?: ""
            currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0

            when (quizStatus) {
                Constants.STATUS_OPEN_FOR_JOIN -> {
                    // Show waiting screen, hide question UI
                    waitingLayout.visibility = View.VISIBLE
                    questionLayout.visibility = View.GONE
                    countdownText.text = "Waiting..."
                }
                Constants.STATUS_STARTED -> {
                    // Show question UI
                    waitingLayout.visibility = View.GONE
                    questionLayout.visibility = View.VISIBLE
                    loadCurrentQuestion(snapshot)
                }
                Constants.STATUS_ENDED -> {
                    // Navigate to scoreboard or show a message
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.container, ScoreboardFragment.newInstance(qId))
                        .commit()
                }
                else -> {
                    // Some unknown status
                    waitingLayout.text = "Unknown quiz status: $quizStatus"
                    waitingLayout.visibility = View.VISIBLE
                    questionLayout.visibility = View.GONE
                }
            }
        }
    }

    private fun loadCurrentQuestion(snapshot: com.google.firebase.firestore.DocumentSnapshot) {
        val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
        if (currentQuestionIndex >= questions.size) {
            // No more questions; possibly the quiz is done
            countdownText.text = "No more questions"
            return
        }

        currentQuestion = questions[currentQuestionIndex]
        val questionText = currentQuestion?.get("questionText") as? String ?: "No question"
        val options = currentQuestion?.get("options") as? List<String> ?: listOf("", "", "", "")
        val timeLimit = currentQuestion?.get("timeLimitSeconds") as? Long ?: 30

        // Update UI
        questionTextView.text = questionText
        optionButtons.forEachIndexed { i, btn ->
            btn.visibility = if (i < options.size) View.VISIBLE else View.GONE
            if (i < options.size) {
                btn.text = options[i]
            }
        }

        // Start the countdown
        startTimer(timeLimit)
    }

    private fun startTimer(timeLimitSeconds: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(timeLimitSeconds * 1000, 1000) {
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
        val qId = quizId ?: return

        db.collection("quizzes").document(qId)
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
            .addOnFailureListener { ex ->
                Toast.makeText(requireContext(), "Failed to submit answer: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
