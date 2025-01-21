package com.example.kahoot.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.kahoot.R
import com.example.kahoot.models.Question
import com.example.kahoot.repository.FirebaseRepository
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FieldValue

class HostFragment : Fragment() {

    private val viewModel: HostViewModel by viewModels()
    private val firebaseRepository = FirebaseRepository()

    private lateinit var questionInput: EditText
    private lateinit var optionInputs: List<EditText>
    private lateinit var correctOptionSpinner: Spinner
    private lateinit var addQuestionButton: Button

    private lateinit var saveQuizButton: Button

    private lateinit var setQuizButton: Button

    private lateinit var questionsListView: ListView
    private lateinit var timeLimitInput: EditText

    private lateinit var questionsAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_host, container, false)

        initializeViews(view)
        setupCorrectOptionSpinner()
        setupQuestionsAdapter()
        setupObservers()

        addQuestionButton.setOnClickListener { handleAddQuestion() }
        saveQuizButton.setOnClickListener { handleSaveQuiz() }
        setQuizButton.setOnClickListener { handleCreateQuiz() }

        return view
    }

    private fun initializeViews(view: View) {
        questionInput = view.findViewById(R.id.questionInput)
        optionInputs = listOf(
            view.findViewById(R.id.optionAInput),
            view.findViewById(R.id.optionBInput),
            view.findViewById(R.id.optionCInput),
            view.findViewById(R.id.optionDInput)
        )
        correctOptionSpinner = view.findViewById(R.id.correctOptionSpinner)
        addQuestionButton = view.findViewById(R.id.addQuestionButton)

        saveQuizButton = view.findViewById(R.id.saveQuizButton)

        setQuizButton = view.findViewById(R.id.setQuizButton)

        questionsListView = view.findViewById(R.id.questionsListView)
        timeLimitInput = view.findViewById(R.id.timeLimitInput)
    }

    private fun setupCorrectOptionSpinner() {
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Option A", "Option B", "Option C", "Option D")
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        correctOptionSpinner.adapter = spinnerAdapter
    }

    private fun setupQuestionsAdapter() {
        questionsAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            mutableListOf()
        )
        questionsListView.adapter = questionsAdapter
    }

    private fun setupObservers() {
        viewModel.questions.observe(viewLifecycleOwner) { questions ->
            questionsAdapter.clear()
            val questionTitles = questions.map { it.questionText }
            questionsAdapter.addAll(questionTitles)
            questionsAdapter.notifyDataSetChanged()
        }
    }

    private fun handleAddQuestion() {
        val questionText = questionInput.text.toString().trim()
        val options = optionInputs.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
        val correctOptionIndex = correctOptionSpinner.selectedItemPosition
        val timeLimit = timeLimitInput.text.toString().toIntOrNull() ?: 30

        if (questionText.isEmpty() || options.size < 2) {
            Toast.makeText(requireContext(), "Provide a question and at least two options", Toast.LENGTH_SHORT).show()
            return
        }

        val question = Question(
            questionText = questionText,
            options = options,
            correctOptionIndex = correctOptionIndex,
            timeLimitSeconds = timeLimit
        )
        viewModel.addQuestion(question)
        clearInputs()
    }

    private fun clearInputs() {
        questionInput.text.clear()
        optionInputs.forEach { it.text.clear() }
        correctOptionSpinner.setSelection(0)
        timeLimitInput.text.clear()
    }

    private fun handleSaveQuiz() {
        val questions = viewModel.questions.value ?: emptyList()

        firebaseRepository.generateUniquePin(
            onSuccess = { pin ->
                val quizId = firebaseRepository.generateQuizId()
                val quizData = mapOf(
                    "hostId" to (firebaseRepository.getCurrentUserId() ?: ""),
                    "pincode" to pin,
                    "questions" to questions.map { it.toMap() },
                    "participants" to emptyList<Map<String, Any>>(),
                    "status" to Constants.STATUS_CREATED,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firebaseRepository.createQuiz(
                    quizId = quizId,
                    quizData = quizData,
                    onSuccess = {
                        Toast.makeText(requireContext(), "Quiz saved (status=created).", Toast.LENGTH_SHORT).show()

                        viewModel.clearQuestions()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(), "Failed to save quiz: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Failed to generate PIN: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleCreateQuiz() {
        val questions = viewModel.questions.value ?: emptyList()
        if (questions.isEmpty()) {
            Toast.makeText(requireContext(), "Add at least one question", Toast.LENGTH_SHORT).show()
            return
        }

        firebaseRepository.generateUniquePin(
            onSuccess = { pin ->
                val quizId = firebaseRepository.generateQuizId()
                val quizData = mapOf(
                    "hostId" to (firebaseRepository.getCurrentUserId() ?: ""),
                    "pincode" to pin,
                    "questions" to questions.map { it.toMap() },
                    "participants" to emptyList<Map<String, Any>>(),
                    "status" to Constants.STATUS_OPEN_FOR_JOIN,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firebaseRepository.createQuiz(
                    quizId = quizId,
                    quizData = quizData,
                    onSuccess = {
                        viewModel.clearQuestions()

                        val lobbyFragment = HostLobbyFragment.newInstance(quizId, pin)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.hostHomeContainer, lobbyFragment)
                            .commit()
                    },
                    onFailure = { e ->
                        Toast.makeText(requireContext(), "Failed to create quiz: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Failed to generate PIN: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
