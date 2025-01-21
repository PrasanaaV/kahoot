package com.example.kahoot.host

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.example.kahoot.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HostLobbyFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var quizId: String? = null
    private var pincode: String? = null
    private var participantsNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var pinTextView: TextView
    private lateinit var participantsListView: ListView
    private lateinit var launchQuizButton: Button
    private lateinit var openQuizButton: Button
    private lateinit var shareButton: ImageButton
    private var snapshotListener: ListenerRegistration? = null

    companion object {
        private const val ARG_QUIZ_ID = "quiz_id"
        private const val ARG_PIN = "pin"

        fun newInstance(quizId: String, pin: String): HostLobbyFragment {
            val fragment = HostLobbyFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_QUIZ_ID, quizId)
                putString(ARG_PIN, pin)
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizId = arguments?.getString(ARG_QUIZ_ID)
        pincode = arguments?.getString(ARG_PIN)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_host_lobby, container, false)

        pinTextView = view.findViewById(R.id.pinTextView)
        participantsListView = view.findViewById(R.id.participantsListView)
        launchQuizButton = view.findViewById(R.id.launchQuizButton)
        openQuizButton = view.findViewById(R.id.openQuizButton)
        shareButton = view.findViewById(R.id.shareButton)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, participantsNames)
        participantsListView.adapter = adapter

        pinTextView.text = "Quiz PIN: $pincode"

        launchQuizButton.setOnClickListener { launchQuiz() }
        openQuizButton.setOnClickListener { openQuiz() }
        setupShareButton()

        setupQuizListener()
        return view
    }

    private fun setupShareButton() {
        shareButton.setOnClickListener {
            val pin = pincode ?: return@setOnClickListener
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join my Kahoot Quiz! PIN: $pin")
            }
            startActivity(Intent.createChooser(shareIntent, "Share PIN"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
    }

    private fun setupQuizListener() {
        val qId = quizId ?: return
        val quizRef = firestore.collection("quizzes").document(qId)

        snapshotListener = quizRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists() || !isAdded) return@addSnapshotListener

            val status = snapshot.getString("status") ?: ""
            val participants = snapshot.get("participants") as? List<Map<String, Any>> ?: emptyList()

            participantsNames.clear()
            participants.forEach { participant ->
                val uid = participant["uid"] as? String ?: ""
                val username = participant["username"] as? String ?: uid
                participantsNames.add(username)
            }
            adapter.notifyDataSetChanged()

            when (status) {
                Constants.STATUS_CREATED -> {
                    openQuizButton.visibility = View.VISIBLE
                    launchQuizButton.visibility = View.GONE
                }
                Constants.STATUS_ENDED -> {
                    openQuizButton.visibility = View.VISIBLE
                    launchQuizButton.visibility = View.GONE
                }
                Constants.STATUS_OPEN_FOR_JOIN -> {
                    openQuizButton.visibility = View.GONE
                    launchQuizButton.visibility = View.VISIBLE
                }
                else -> {
                    openQuizButton.visibility = View.GONE
                    launchQuizButton.visibility = View.GONE
                }
            }
        }
    }

    private fun openQuiz() {
        val qId = quizId ?: return
        val quizRef = firestore.collection("quizzes").document(qId)

        val updates = mapOf(
            "status" to Constants.STATUS_OPEN_FOR_JOIN,
            "currentQuestionIndex" to 0,
            "participants" to emptyList<Map<String, Any>>()
        )

        quizRef.update(updates)
            .addOnSuccessListener {
                quizRef.collection("responses").get().addOnSuccessListener { responses ->
                    val batch = firestore.batch()
                    responses.forEach { response ->
                        batch.delete(response.reference)
                    }
                    batch.commit()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to open quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun launchQuiz() {
        val qId = quizId ?: return
        val quizRef = firestore.collection("quizzes").document(qId)

        if (participantsNames.isEmpty()) {
            Toast.makeText(requireContext(), "At least one player must join to start the quiz.", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf(
            "status" to Constants.STATUS_IN_PROGRESS,
            "currentQuestionIndex" to 0
        )
        
        quizRef.update(updates)
            .addOnSuccessListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.hostHomeContainer, HostQuizFragment.newInstance(qId))
                    .commit()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to launch quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
