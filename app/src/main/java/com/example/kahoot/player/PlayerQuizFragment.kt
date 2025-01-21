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
import com.airbnb.lottie.LottieAnimationView
import com.example.kahoot.R
import com.example.kahoot.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PlayerQuizFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null

    // UI
    private lateinit var waitingLayout: TextView
    private lateinit var questionLayout: LinearLayout
    private lateinit var questionTextView: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var countdownText: TextView
    private lateinit var progressAnimation: LottieAnimationView

    // Current question data
    private var currentQuestionIndex: Int = 0
    private var totalQuestions: Int = 0
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
        progressAnimation = view.findViewById(R.id.progressAnimation)

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
            if (e != null || snapshot == null || !snapshot.exists() || !isAdded) return@addSnapshotListener

            quizStatus = snapshot.getString("status") ?: ""
            currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            totalQuestions = questions.size

            if (quizStatus == Constants.STATUS_ENDED) {
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
                Toast.makeText(requireContext(), "Quiz not in progress.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            waitingLayout.visibility = View.GONE
            questionLayout.visibility = View.VISIBLE

            // Mettre à jour la barre de progression
            val progress = (currentQuestionIndex.toFloat() + 1) / totalQuestions
            progressAnimation.progress = progress

            if (currentQuestionIndex >= questions.size) {
                if (quizStatus != Constants.STATUS_ENDED) {
                    quizRef.update("status", Constants.STATUS_ENDED)
                }
                return@addSnapshotListener
            }

            val currentQuestion = questions[currentQuestionIndex]
            val questionText = currentQuestion["questionText"] as? String ?: "No question"
            val options = currentQuestion["options"] as? List<String> ?: listOf()
            val timeLimit = (currentQuestion["timeLimitSeconds"] as? Number)?.toInt() ?: 30

            questionTextView.text = questionText
            
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
        
        optionButtons.forEach { it.isEnabled = false }
        
        val response = hashMapOf(
            "participantId" to FirebaseAuth.getInstance().currentUser?.uid,
            "questionIndex" to currentQuestionIndex,
            "selectedOption" to selectedOptionIndex,
            "timestamp" to FieldValue.serverTimestamp()
        )

        quizRef.collection("responses").add(response)
            .addOnSuccessListener {
                showAnswerColors(quizRef, selectedOptionIndex)
                checkAllParticipantsAnswered(quizRef)
            }
            .addOnFailureListener { e ->
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
            
            val nextIndex = currentIndex + 1
            if (nextIndex < questions.size) {
                quizRef.update("currentQuestionIndex", nextIndex)
            } else {
                quizRef.update("status", Constants.STATUS_ENDED)
            }
        }
    }
}
