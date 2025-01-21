package com.example.kahoot.host

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.player.ScoreboardFragment
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class HostQuizFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null
    private var timer: CountDownTimer? = null
    private var currentQuestionIndex: Int = 0

    private lateinit var questionTextView: TextView
    private lateinit var countdownText: TextView
    private lateinit var participantsProgressText: TextView

    companion object {
        private const val ARG_QUIZ_ID = "quiz_id"

        fun newInstance(quizId: String): HostQuizFragment {
            val fragment = HostQuizFragment()
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
    ): View? {
        val view = inflater.inflate(R.layout.fragment_host_quiz, container, false)
        questionTextView = view.findViewById(R.id.questionTextView)
        countdownText = view.findViewById(R.id.countdownText)
        participantsProgressText = view.findViewById(R.id.participantsProgressText)
        
        listenToQuizChanges()
        return view
    }

    private fun listenToQuizChanges() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("Quiz", "Error listening to quiz changes", e)
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                Log.d("Quiz", "Quiz document does not exist")
                return@addSnapshotListener
            }

            if (!isAdded) return@addSnapshotListener

            val status = snapshot.getString("status") ?: ""
            if (status == Constants.STATUS_ENDED) {
                timer?.cancel()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, ScoreboardFragment.newInstance(qId))
                    .commit()
                return@addSnapshotListener
            }

            if (status != Constants.STATUS_IN_PROGRESS) {
                Log.d("Quiz", "Quiz is not in progress: $status")
                return@addSnapshotListener
            }

            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()

            Log.d("Quiz", "Current question index: $currentQuestionIndex")

            if (currentQuestionIndex >= questions.size) {
                if (!isAdded) return@addSnapshotListener
                quizRef.update("status", Constants.STATUS_ENDED)
                return@addSnapshotListener
            }

            val currentQuestion = questions[currentQuestionIndex]
            val questionText = currentQuestion["questionText"] as? String ?: "No question"
            val timeLimit = (currentQuestion["timeLimitSeconds"] as? Number)?.toInt() ?: 30

            // Listen to responses in real-time
            quizRef.collection("responses")
                .whereEqualTo("questionIndex", currentQuestionIndex)
                .addSnapshotListener { responseSnapshot, responseError ->
                    if (responseError != null) {
                        Log.e("Quiz", "Error listening to responses", responseError)
                        return@addSnapshotListener
                    }

                    if (!isAdded) return@addSnapshotListener

                    val responseCount = responseSnapshot?.size() ?: 0
                    participantsProgressText.text = "Responses: $responseCount/${participants.size}"
                    
                    // If all participants have answered, cancel timer and move to next question
                    if (responseCount >= participants.size) {
                        timer?.cancel()
                        moveToNextQuestion()
                    }
                }

            questionTextView.text = questionText
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
                submitNoAnswerForRemainingParticipants()
            }
        }.start()
    }

    private fun submitNoAnswerForRemainingParticipants() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener

            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()
            
            // Get current responses
            quizRef.collection("responses")
                .whereEqualTo("questionIndex", currentQuestionIndex)
                .get()
                .addOnSuccessListener { responses ->
                    // Find participants who haven't answered
                    val respondedParticipants = responses.documents.mapNotNull { it.getString("participantId") }
                    val nonRespondedParticipants = participants.mapNotNull { it["uid"] as? String }
                        .filter { it !in respondedParticipants }

                    // Submit no-answer (-1) for each participant who hasn't answered
                    nonRespondedParticipants.forEach { participantId ->
                        val response = hashMapOf(
                            "participantId" to participantId,
                            "questionIndex" to currentQuestionIndex,
                            "selectedOption" to -1,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                        quizRef.collection("responses").add(response)
                    }

                    // After submitting no-answers, move to next question
                    moveToNextQuestion()
                }
        }
    }

    private fun moveToNextQuestion() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)
        
        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            
            val currentIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            
            if (currentIndex < questions.size - 1) {
                quizRef.update("currentQuestionIndex", currentIndex + 1)
            } else {
                // Quiz is finished, update status to ended
                quizRef.update("status", Constants.STATUS_ENDED)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
    }
}
