package com.example.kahoot.host

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.models.Question
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class HostLobbyFragment : Fragment() {

    private lateinit var pinTextView: TextView
    private lateinit var participantsListView: ListView
    private lateinit var launchQuizButton: Button

    private var quizId: String? = null
    private var quizPin: String? = null

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: ArrayAdapter<String>
    private val participantsNames = mutableListOf<String>() // or store only UIDs if you want

    companion object {
        private const val ARG_QUIZ_ID = "arg_quiz_id"
        private const val ARG_PIN = "arg_pin"

        fun newInstance(quizId: String, pin: String): HostLobbyFragment {
            val fragment = HostLobbyFragment()
            val args = Bundle().apply {
                putString(ARG_QUIZ_ID, quizId)
                putString(ARG_PIN, pin)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString(ARG_QUIZ_ID)
        quizPin = arguments?.getString(ARG_PIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_lobby, container, false)

        pinTextView = view.findViewById(R.id.pinTextView)
        participantsListView = view.findViewById(R.id.participantsListView)
        launchQuizButton = view.findViewById(R.id.launchQuizButton)

        pinTextView.text = "Quiz PIN: $quizPin"

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, participantsNames)
        participantsListView.adapter = adapter

        launchQuizButton.setOnClickListener {
            onLaunchQuiz()
        }

        listenToParticipants()

        return view
    }

    private fun onLaunchQuiz() {
        if (participantsNames.isEmpty()) {
            Toast.makeText(requireContext(), "At least one player must join to start the quiz.", Toast.LENGTH_SHORT).show()
            return
        }

        val quizRef = firestore.collection("quizzes").document(quizId!!)
        val updates = mapOf(
            "status" to Constants.STATUS_IN_PROGRESS,
            "currentQuestionIndex" to 0
        )
        
        quizRef.update(updates)
            .addOnSuccessListener {
                Log.d("Quiz", "Quiz started successfully")
                if (!isAdded) return@addOnSuccessListener
                // Navigate to HostQuizFragment instead of HostFragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, HostQuizFragment.newInstance(quizId!!))
                    .commit()
            }
            .addOnFailureListener { e ->
                Log.e("Quiz", "Failed to start quiz", e)
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to start quiz.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenToParticipants() {
        val quizRef = firestore.collection("quizzes").document(quizId!!)
        quizRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            if (!isAdded) return@addSnapshotListener

            quizPin = snapshot.getString("pincode")
            pinTextView.text = "PIN: $quizPin"

            val status = snapshot.getString("status")
            // Remove this part as we handle navigation in onLaunchQuiz
            // if (status == Constants.STATUS_IN_PROGRESS) {
            //     parentFragmentManager.beginTransaction()
            //         .replace(R.id.container, HostQuizFragment.newInstance(quizId!!))
            //         .commit()
            //     return@addSnapshotListener
            // }

            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()
            participantsNames.clear()
            participants.forEach { participant ->
                val name = participant["uid"] as? String ?: "Unknown"
                participantsNames.add(name)
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun createQuiz() {
        val questions = listOf(
            Question(
                questionText = "Question 1",
                options = listOf("Option A", "Option B", "Option C", "Option D"),
                correctOptionIndex = 0,
                timeLimitSeconds = 30
            ).toMap(),
            Question(
                questionText = "Question 2",
                options = listOf("Option A", "Option B", "Option C", "Option D"),
                correctOptionIndex = 1,
                timeLimitSeconds = 45
            ).toMap()
        )

        val quizData = mapOf(
            "hostId" to FirebaseAuth.getInstance().currentUser?.uid,
            "pincode" to "1234",
            "status" to Constants.STATUS_OPEN_FOR_JOIN,
            "currentQuestionIndex" to 0,
            "participants" to listOf<Map<String, Any>>(),
            "questions" to questions
        )

        firestore.collection("quizzes").add(quizData)
            .addOnSuccessListener { documentReference ->
                Log.d("Quiz", "Quiz created with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w("Quiz", "Error adding quiz", e)
            }
    }
}
