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
import com.example.kahoot.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PlayerQuizFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null

    private lateinit var waitingLayout: TextView
    private lateinit var questionLayout: LinearLayout
    private lateinit var questionTextView: TextView
    private lateinit var optionButtons: List<Button>
    private lateinit var countdownText: TextView
    private lateinit var progressAnimation: LottieAnimationView

    private var currentQuestionIndex: Int = 0
    private var totalQuestions: Int = 0
    private var timer: CountDownTimer? = null

    private var canMoveToNextQuestion = false
    private var hasSubmittedAnswer = false

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
        timer = null
        canMoveToNextQuestion = false
        hasSubmittedAnswer = false
    }

    override fun onPause() {
        super.onPause()
        timer?.cancel()
        timer = null
    }

    private fun listenToQuizChanges() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists() || !isAdded) return@addSnapshotListener

            val status = snapshot.getString("status") ?: ""
            currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            totalQuestions = questions.size

            when (status) {
                Constants.STATUS_IN_PROGRESS -> {
                    if (currentQuestionIndex < questions.size) {
                        showCurrentQuestion(questions[currentQuestionIndex])
                        if (currentQuestionIndex == 0) {
                            questionLayout.post {
                                context?.let { NotificationHelper.showQuizStartNotification(it) }
                            }
                        }
                    }
                }
                Constants.STATUS_ENDED -> {
                    navigateToScoreboard()
                }
                Constants.STATUS_OPEN_FOR_JOIN -> {
                    showWaitingScreen()
                }
            }
        }
    }

    private fun showCurrentQuestion(question: Map<String, Any>) {
        waitingLayout.visibility = View.GONE
        questionLayout.visibility = View.VISIBLE

        canMoveToNextQuestion = false
        hasSubmittedAnswer = false

        val questionText = question["questionText"] as? String ?: "No question"
        val options = question["options"] as? List<String> ?: listOf()
        val timeLimit = (question["timeLimitSeconds"] as? Number)?.toInt() ?: 30

        progressAnimation.progress = currentQuestionIndex.toFloat() / (totalQuestions - 1)

        questionTextView.text = "Question ${currentQuestionIndex + 1}/$totalQuestions\n$questionText"
        
        resetButtonColors()
        
        optionButtons.forEachIndexed { index, button ->
            if (index < options.size) {
                button.visibility = View.VISIBLE
                button.text = options[index]
                button.isEnabled = true
            } else {
                button.visibility = View.GONE
            }
        }
        
        startTimer(timeLimit.toLong())
    }

    private fun navigateToScoreboard() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, ScoreboardFragment.newInstance(quizId ?: return))
            .commit()
    }

    private fun showWaitingScreen() {
        waitingLayout.visibility = View.VISIBLE
        questionLayout.visibility = View.GONE
        waitingLayout.text = "Waiting for quiz to start..."
    }

    private fun startTimer(timeLimitSeconds: Long) {
        canMoveToNextQuestion = false
        hasSubmittedAnswer = false
        
        timer?.cancel()
        timer = null

        countdownText.text = "Time left: ${timeLimitSeconds}s"

        timer = object : CountDownTimer(timeLimitSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) {
                    cancel()
                    return
                }
                countdownText.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                if (!isAdded) return
                countdownText.text = "Time's up!"
                if (!hasSubmittedAnswer) {
                    submitAnswer(-1)
                }
                canMoveToNextQuestion = true
                checkAllParticipantsAnswered(db.collection("quizzes").document(quizId ?: return))
            }
        }.start()
    }

    private fun submitAnswer(selectedOptionIndex: Int) {
        val qId = quizId ?: return
        if (!isAdded || hasSubmittedAnswer) return
        
        hasSubmittedAnswer = true
        
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
                    hasSubmittedAnswer = false
                    optionButtons.forEach { it.isEnabled = true }
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
        if (!isAdded) return

        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener

            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()
            val currentStatus = snapshot.getString("status")
            
            if (currentStatus != Constants.STATUS_IN_PROGRESS) return@addOnSuccessListener
            
            quizRef.collection("responses")
                .whereEqualTo("questionIndex", currentQuestionIndex)
                .get()
                .addOnSuccessListener { responses ->
                    if (!isAdded) return@addOnSuccessListener

                    if (responses.size() >= participants.size) {
                        canMoveToNextQuestion = true
                        
                        optionButtons.forEach { it.isEnabled = false }
                        
                        timer?.cancel()
                        countdownText.text = "Time's up!"
                        
                        questionLayout.postDelayed({
                            if (isAdded) {
                                quizRef.update("forceNextQuestion", true)
                            }
                        }, 3000)
                    }
                }
        }
    }
}
