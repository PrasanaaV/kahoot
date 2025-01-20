package com.example.kahoot.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.models.Question
import com.example.kahoot.repository.FirebaseRepository
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FieldValue

class HostSetsQuestionsFragment : Fragment() {

    private val firebaseRepository = FirebaseRepository()
    private lateinit var questionTextInput: EditText
    private lateinit var option1Input: EditText
    private lateinit var option2Input: EditText
    private lateinit var option3Input: EditText
    private lateinit var option4Input: EditText
    private lateinit var correctIndexInput: EditText
    private lateinit var timeLimitInput: EditText
    private lateinit var addQuestionButton: Button
    private lateinit var saveQuizButton: Button

    private val questionsList = mutableListOf<Question>() // Store questions here
    private var quizId: String = "" // Store the quiz ID

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_host_sets_questions, container, false)

        // Initialize views
        initializeViews(view)

        // Set up button listeners
        addQuestionButton.setOnClickListener { addQuestion() }
        saveQuizButton.setOnClickListener { saveQuiz() }

        return view
    }

    private fun initializeViews(view: View) {
        questionTextInput = view.findViewById(R.id.questionTextInput)
        option1Input = view.findViewById(R.id.option1Input)
        option2Input = view.findViewById(R.id.option2Input)
        option3Input = view.findViewById(R.id.option3Input)
        option4Input = view.findViewById(R.id.option4Input)
        correctIndexInput = view.findViewById(R.id.correctIndexInput)
        timeLimitInput = view.findViewById(R.id.timeLimitInput)
        addQuestionButton = view.findViewById(R.id.addQuestionButton)
        saveQuizButton = view.findViewById(R.id.saveQuizButton)
    }

    private fun addQuestion() {
        val questionText = questionTextInput.text.toString().trim()
        val options = listOf(
            option1Input.text.toString().trim(),
            option2Input.text.toString().trim(),
            option3Input.text.toString().trim(),
            option4Input.text.toString().trim()
        )
        val correctIndex = correctIndexInput.text.toString().toIntOrNull() ?: -1
        val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 30

        // Validate inputs
        if (questionText.isEmpty() || options.any { it.isEmpty() } || correctIndex !in options.indices) {
            Toast.makeText(requireContext(), "Provide valid question, options, and correct answer", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a Question object
        val question = Question(
            questionText = questionText,
            options = options,
            correctOptionIndex = correctIndex,
            timeLimitSeconds = timeLimit
        )

        // Add question to the list
        questionsList.add(question)
        clearInputs()
        Toast.makeText(requireContext(), "Question added", Toast.LENGTH_SHORT).show()
    }

    private fun clearInputs() {
        questionTextInput.text.clear()
        option1Input.text.clear()
        option2Input.text.clear()
        option3Input.text.clear()
        option4Input.text.clear()
        correctIndexInput.text.clear()
        timeLimitInput.text.clear()
    }

    private fun saveQuiz() {
        if (questionsList.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least one question", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate a unique quiz ID
        quizId = firebaseRepository.generateQuizId()

        // Prepare quiz data
        val quizData = mapOf(
            "hostId" to (firebaseRepository.getCurrentUserId() ?: ""),
            "status" to Constants.STATUS_CREATED,
            "questions" to questionsList.map { it.toMap() }, // Convert questions to a map
            "participants" to emptyList<Map<String, Any>>(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        // Save quiz to Firestore
        firebaseRepository.createQuiz(quizId, quizData, onSuccess = {
            Toast.makeText(requireContext(), "Quiz saved with ID: $quizId", Toast.LENGTH_LONG).show()
            questionsList.clear() // Clear the questions list after saving
        }, onFailure = { e ->
            Toast.makeText(requireContext(), "Failed to save quiz: ${e.message}", Toast.LENGTH_SHORT).show()
        })
    }
}