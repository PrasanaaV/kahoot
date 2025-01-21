package com.example.kahoot.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
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

        Log.d("Quiz", "Setting up snapshot listener for quizId: $qId")
        quizRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("Quiz", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                Log.d("Quiz", "Current data: null")
                return@addSnapshotListener
            }

            if (!isAdded) return@addSnapshotListener

            Log.d("Quiz", "Current data: ${snapshot.data}")
            
            quizStatus = snapshot.getString("status") ?: ""
            currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0

            Log.d("Quiz", "Status: $quizStatus, Current Question Index: $currentQuestionIndex")

            if (quizStatus == Constants.STATUS_ENDED) {
                // Navigate to scoreboard when quiz ends
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, ScoreboardFragment.newInstance(qId))
                    .commit()
                return@addSnapshotListener
            }

            if (quizStatus == Constants.STATUS_OPEN_FOR_JOIN) {
                waitingLayout.visibility = View.VISIBLE
                questionLayout.visibility = View.GONE
                waitingLayout.text = "Waiting for quiz to start..."
                return@addSnapshotListener
            }

            if (quizStatus != Constants.STATUS_IN_PROGRESS) {
                if (!isAdded) return@addSnapshotListener
                Toast.makeText(requireContext(), "Quiz not in progress.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            waitingLayout.visibility = View.GONE
            questionLayout.visibility = View.VISIBLE

            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            
            if (currentQuestionIndex >= questions.size) {
                // Si nous sommes après la dernière question, terminer le quiz
                if (quizStatus != Constants.STATUS_ENDED) {
                    quizRef.update("status", Constants.STATUS_ENDED)
                }
                return@addSnapshotListener
            }

            val currentQuestion = questions[currentQuestionIndex]
            val questionText = currentQuestion["questionText"] as? String ?: "No question"
            val options = currentQuestion["options"] as? List<String> ?: listOf()
            val timeLimit = (currentQuestion["timeLimitSeconds"] as? Number)?.toInt() ?: 30

            Log.d("Quiz", "Current Question: $questionText, Time Limit: $timeLimit seconds")

            questionTextView.text = questionText
            
            // Reset button colors and update options for new question
            resetButtonColors()
            
            optionButtons.forEachIndexed { index, button ->
                if (index < options.size) {
                    button.visibility = View.VISIBLE
                    button.text = options[index]
                } else {
                    button.visibility = View.GONE
                }
            }
            
            startTimer(timeLimit.toLong())
        }
    }

    private fun startTimer(timeLimitSeconds: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(timeLimitSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) return
                countdownText.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                if (!isAdded) return
                // If time is up and player hasn't answered, submit a no-answer (-1)
                if (optionButtons.any { it.isEnabled }) {
                    submitAnswer(-1)
                }
            }
        }.start()
    }

    private fun submitAnswer(selectedOptionIndex: Int) {
        val qId = quizId ?: return
        if (!isAdded) return
        
        val quizRef = db.collection("quizzes").document(qId)
        
        // Disable all option buttons after answering
        optionButtons.forEach { it.isEnabled = false }
        
        val response = hashMapOf(
            "participantId" to FirebaseAuth.getInstance().currentUser?.uid,
            "questionIndex" to currentQuestionIndex,
            "selectedOption" to selectedOptionIndex,
            "timestamp" to FieldValue.serverTimestamp()
        )

        quizRef.collection("responses").add(response)
            .addOnSuccessListener {
                Log.d("Quiz", "Response submitted successfully")
                // After submitting answer, show correct/incorrect colors
                showAnswerColors(quizRef, selectedOptionIndex)
                checkAllParticipantsAnswered(quizRef)
            }
            .addOnFailureListener { e ->
                Log.e("Quiz", "Error submitting response", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to submit answer", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showAnswerColors(quizRef: DocumentReference, selectedOptionIndex: Int) {
        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener

            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            if (currentQuestionIndex >= questions.size) return@addOnSuccessListener

            val currentQuestion = questions[currentQuestionIndex] as? Map<String, Any>
            val correctOptionIndex = currentQuestion?.get("correctOptionIndex") as? Long ?: return@addOnSuccessListener

            // Color all buttons based on correctness
            optionButtons.forEachIndexed { index, button ->
                val backgroundColor = when (index) {
                    correctOptionIndex.toInt() -> ContextCompat.getColor(requireContext(), R.color.correct_answer)
                    selectedOptionIndex -> if (selectedOptionIndex != correctOptionIndex.toInt()) {
                        ContextCompat.getColor(requireContext(), R.color.incorrect_answer)
                    } else {
                        ContextCompat.getColor(requireContext(), R.color.correct_answer)
                    }
                    else -> button.backgroundTintList?.defaultColor ?: Color.GRAY
                }
                
                button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
                // Keep text white and visible even when button is disabled
                button.setTextColor(Color.WHITE)
            }
        }
    }

    private fun resetButtonColors() {
        optionButtons.forEach { button ->
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.purple_500))
            button.setTextColor(Color.WHITE)
            button.isEnabled = true
        }
    }

    private fun checkAllParticipantsAnswered(quizRef: DocumentReference) {
        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener

            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()
            
            quizRef.collection("responses")
                .whereEqualTo("questionIndex", currentQuestionIndex)
                .get()
                .addOnSuccessListener { responses ->
                    if (responses.size() >= participants.size) {
                        // All participants have answered, move to next question
                        moveToNextQuestion(quizRef)
                    }
                }
        }
    }

    private fun moveToNextQuestion(quizRef: DocumentReference) {
        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            
            val currentIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            
            // Passer à la question suivante
            val nextIndex = currentIndex + 1
            if (nextIndex < questions.size) {
                // S'il y a encore des questions, passer à la suivante
                quizRef.update("currentQuestionIndex", nextIndex)
            } else {
                // Si c'était la dernière question, terminer le quiz
                quizRef.update("status", Constants.STATUS_ENDED)
            }
        }
    }
}
