package com.example.kahoot.player

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PinEntryFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var playerUsernameInput: EditText

    private lateinit var pinInput: EditText
    private lateinit var joinButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_pin_entry, container, false)

        playerUsernameInput = view.findViewById(R.id.playerUsernameInput)
        pinInput = view.findViewById(R.id.pinInput)
        joinButton = view.findViewById(R.id.joinButton)

        joinButton.setOnClickListener {
            val pin = pinInput.text.toString().trim()
            val username = playerUsernameInput.text.toString().trim()

            if (TextUtils.isEmpty(username)) {
                Toast.makeText(requireContext(), "Please enter a username.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pin.isEmpty()) {
                Toast.makeText(requireContext(), "Enter PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            validatePinAndJoinQuiz(pin, username)
        }

        return view
    }

    private fun validatePinAndJoinQuiz(pin: String, username: String) {
        ensurePlayerSignedIn {
            db.collection("quizzes")
                .whereEqualTo("pincode", pin)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) {
                        Toast.makeText(requireContext(), "Invalid PIN", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val quizDoc = querySnapshot.documents[0]
                    val quizId = quizDoc.id
                    val status = quizDoc.getString("status") ?: ""

                    if (status == "open_for_join") {
                        joinQuiz(quizId, username)
                    } else {
                        Toast.makeText(requireContext(),
                            "Quiz not open for joining (Status: $status)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to validate PIN: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun joinQuiz(quizId: String, username: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val quizRef = db.collection("quizzes").document(quizId)

        quizRef.update(
            "participants",
            FieldValue.arrayUnion(
                mapOf("uid" to userId, "username" to username, "score" to 0)
            )
        ).addOnSuccessListener {
            Toast.makeText(requireContext(), "Joined quiz successfully", Toast.LENGTH_SHORT).show()
            navigateToPlayerQuiz(quizId)
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to join quiz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToPlayerQuiz(quizId: String) {
        val transaction = parentFragmentManager.beginTransaction()
        val playerQuizFragment = PlayerQuizFragment.newInstance(quizId)
        transaction.replace(R.id.container, playerQuizFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    /**
     * If you want players to be anonymous, ensure they're signed in anonymously.
     * If you want them to also log in or register, do it differently.
     */
    private fun ensurePlayerSignedIn(onSignedIn: () -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSignedIn()
                } else {
                    Toast.makeText(requireContext(), "Failed to sign in anonymously", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            onSignedIn()
        }
    }
}
