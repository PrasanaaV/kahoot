package com.example.kahoot.player

import android.graphics.Color
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
import com.example.kahoot.repository.FirebaseRepository

class PlayerFragment : Fragment() {

    private val firebaseRepository = FirebaseRepository()

    private lateinit var questionTextView: TextView
    private lateinit var optionAButton: Button
    private lateinit var optionBButton: Button
    private lateinit var timerTextView: TextView

    private var quizId: String? = null
    private var currentQuestionIndex: Int = 0
    private var correctOptionIndex: Int = -1
    private var timer: CountDownTimer? = null

    companion object {
        fun newInstance(quizId: String): PlayerFragment {
            val fragment = PlayerFragment()
            val args = Bundle()
            args.putString("quizId", quizId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString("quizId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_player, container, false)
        questionTextView = view.findViewById(R.id.questionTextView)
        optionAButton = view.findViewById(R.id.optionAButton)
        optionBButton = view.findViewById(R.id.optionBButton)
        timerTextView = view.findViewById(R.id.timerTextView)

        optionAButton.setOnClickListener { submitAnswer(0) }
        optionBButton.setOnClickListener { submitAnswer(1) }

        listenToQuizUpdates()

        return view
    }

    private fun listenToQuizUpdates() {
        val id = quizId ?: return
        firebaseRepository.getQuiz(
            quizId = id,
            onSuccess = { snapshot ->
                val questions = snapshot["questions"] as? List<Map<String, Any>> ?: emptyList()
                currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0

                if (currentQuestionIndex < questions.size) {
                    loadQuestion(questions[currentQuestionIndex])
                } else {
                    displayQuizCompleted()
                }
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Failed to load quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadQuestion(question: Map<String, Any>) {
        val questionText = question["questionText"] as? String ?: "No question available"
        val options = question["options"] as? List<String> ?: emptyList()
        correctOptionIndex = (question["correctOptionIndex"] as? Long)?.toInt() ?: -1

        questionTextView.text = questionText
        optionAButton.text = options.getOrNull(0) ?: "Option A"
        optionBButton.text = options.getOrNull(1) ?: "Option B"

        optionAButton.visibility = if (options.isNotEmpty()) View.VISIBLE else View.GONE
        optionBButton.visibility = if (options.size > 1) View.VISIBLE else View.GONE

        startTimer()
    }

    private fun displayQuizCompleted() {
        questionTextView.text = "Quiz completed!"
        optionAButton.visibility = View.GONE
        optionBButton.visibility = View.GONE
        timerTextView.visibility = View.GONE
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerTextView.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                highlightAnswer(-1) // No answer selected
            }
        }.start()
    }

    private fun submitAnswer(optionIndex: Int) {
        timer?.cancel()
        highlightAnswer(optionIndex)

        val userId = firebaseRepository.getCurrentUserId()
        if (userId == null || quizId == null) {
            Toast.makeText(requireContext(), "Failed to submit answer", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseRepository.submitAnswer(
            quizId = quizId!!,
            questionIndex = currentQuestionIndex,
            userId = userId,
            optionIndex = optionIndex,
            onSuccess = {
                Toast.makeText(requireContext(), "Answer submitted", Toast.LENGTH_SHORT).show()
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Failed to submit answer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun highlightAnswer(selectedOptionIndex: Int) {
        optionAButton.setBackgroundColor(Color.LTGRAY)
        optionBButton.setBackgroundColor(Color.LTGRAY)

        if (selectedOptionIndex == correctOptionIndex) {
            if (selectedOptionIndex == 0) optionAButton.setBackgroundColor(Color.GREEN)
            if (selectedOptionIndex == 1) optionBButton.setBackgroundColor(Color.GREEN)
        } else {
            if (selectedOptionIndex == 0) optionAButton.setBackgroundColor(Color.RED)
            if (selectedOptionIndex == 1) optionBButton.setBackgroundColor(Color.RED)

            if (correctOptionIndex == 0) optionAButton.setBackgroundColor(Color.GREEN)
            if (correctOptionIndex == 1) optionBButton.setBackgroundColor(Color.GREEN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel() // Cancel the timer to avoid memory leaks
    }
}
