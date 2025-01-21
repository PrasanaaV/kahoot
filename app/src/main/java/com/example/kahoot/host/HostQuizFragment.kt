package com.example.kahoot.host

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.example.kahoot.R
import com.example.kahoot.player.ScoreboardFragment
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentReference

class HostQuizFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private var quizId: String? = null
    private var timer: CountDownTimer? = null
    private var currentQuestionIndex: Int = 0

    private lateinit var questionTextView: TextView
    private lateinit var countdownText: TextView
    private lateinit var participantsProgressText: TextView
    private lateinit var progressAnimation: LottieAnimationView
    private var totalQuestions: Int = 0

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
        progressAnimation = view.findViewById(R.id.progressAnimation)
        
        listenToQuizChanges()
        listenToQuestionControl()
        return view
    }

    private fun listenToQuestionControl() {
        val qId = quizId ?: return
        val quizRef = db.collection("quizzes").document(qId)

        quizRef.collection("questionControl")
            .document("status")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("HostQuizFragment", "Error listening to question control", e)
                    return@addSnapshotListener
                }

                if (!isAdded || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                Log.d("HostQuizFragment", "Received question control update: ${snapshot.data}")

                val allAnswered = snapshot.getBoolean("allAnswered") ?: false
                val questionIndex = snapshot.getLong("questionIndex")?.toInt() ?: -1
                val totalResponses = snapshot.getLong("totalResponses")?.toInt() ?: 0
                val totalParticipants = snapshot.getLong("totalParticipants")?.toInt() ?: 0

                Log.d("HostQuizFragment", "Question control - allAnswered: $allAnswered, questionIndex: $questionIndex, current: $currentQuestionIndex")

                if (allAnswered && questionIndex == currentQuestionIndex) {
                    Log.d("HostQuizFragment", "All participants answered ($totalResponses/$totalParticipants). Moving to next question.")
                    view?.postDelayed({
                        if (isAdded) {
                            moveToNextQuestion(quizRef)
                            snapshot.reference.delete()
                                .addOnSuccessListener {
                                    Log.d("HostQuizFragment", "Successfully deleted question control status")
                                }
                                .addOnFailureListener { error ->
                                    Log.e("HostQuizFragment", "Failed to delete question control status", error)
                                }
                        }
                    }, 3000)
                }
            }
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
                    .replace(R.id.hostHomeContainer, ScoreboardFragment.newInstance(qId))
                    .commit()
                return@addSnapshotListener
            }

            if (status != Constants.STATUS_IN_PROGRESS) {
                Log.d("Quiz", "Quiz is not in progress: $status")
                return@addSnapshotListener
            }

            val forceNext = snapshot.getBoolean("forceNextQuestion") ?: false
            if (forceNext) {
                timer?.cancel()
                moveToNextQuestion(quizRef)
                quizRef.update("forceNextQuestion", false)
                return@addSnapshotListener
            }

            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            currentQuestionIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            totalQuestions = questions.size
            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()

            if (currentQuestionIndex >= questions.size) {
                if (!isAdded) return@addSnapshotListener
                quizRef.update("status", Constants.STATUS_ENDED)
                return@addSnapshotListener
            }

            val currentQuestion = questions[currentQuestionIndex]
            val questionText = currentQuestion["questionText"] as? String ?: "No question"
            val timeLimit = (currentQuestion["timeLimitSeconds"] as? Number)?.toInt() ?: 30

            questionTextView.text = "Question ${currentQuestionIndex + 1}/$totalQuestions\n$questionText"
            progressAnimation.progress = currentQuestionIndex.toFloat() / (totalQuestions - 1)

            quizRef.collection("responses")
                .whereEqualTo("questionIndex", currentQuestionIndex)
                .get()
                .addOnSuccessListener { responses ->
                    if (!isAdded) return@addOnSuccessListener
                    updateParticipantsProgress(responses.size(), participants.size)
                }

            startTimer(timeLimit.toLong(), quizRef)
        }
    }

    private fun startTimer(timeLimitSeconds: Long, quizRef: DocumentReference) {
        timer?.cancel()
        timer = object : CountDownTimer(timeLimitSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) return
                countdownText.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                if (!isAdded) return
                countdownText.text = "Time's up!"
                view?.postDelayed({
                    if (isAdded) {
                        moveToNextQuestion(quizRef)
                    }
                }, 3000)
            }
        }.start()
    }

    private fun updateParticipantsProgress(responsesCount: Int, totalParticipants: Int) {
        participantsProgressText.text = "Responses: $responsesCount/$totalParticipants"
    }

    private fun moveToNextQuestion(quizRef: DocumentReference) {
        Log.d("HostQuizFragment", "Moving to next question")
        
        quizRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded) return@addOnSuccessListener
            
            val currentIndex = snapshot.getLong("currentQuestionIndex")?.toInt() ?: 0
            val questions = snapshot.get("questions") as? List<Map<String, Any>> ?: emptyList()
            
            val nextIndex = currentIndex + 1
            if (nextIndex < questions.size) {
                Log.d("HostQuizFragment", "Updating currentQuestionIndex to $nextIndex")
                quizRef.update(
                    mapOf(
                        "currentQuestionIndex" to nextIndex,
                        "lastQuestionChange" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    Log.d("HostQuizFragment", "Successfully updated question index")
                }.addOnFailureListener { e ->
                    Log.e("HostQuizFragment", "Failed to update question index", e)
                }
            } else {
                Log.d("HostQuizFragment", "Quiz finished, updating status to ENDED")
                quizRef.update("status", Constants.STATUS_ENDED)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
    }
}
