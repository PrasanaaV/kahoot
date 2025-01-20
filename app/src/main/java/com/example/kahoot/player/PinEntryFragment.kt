package com.example.kahoot.player

import android.os.Bundle
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
    private lateinit var pinInput: EditText
    private lateinit var joinButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pin_entry, container, false)
        pinInput = view.findViewById(R.id.pinInput)
        joinButton = view.findViewById(R.id.joinButton)

        joinButton.setOnClickListener {
            val pin = pinInput.text.toString().trim()
            if (pin.isEmpty()) {
                Toast.makeText(requireContext(), "Enter PIN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            validatePinAndJoinQuiz(pin)
        }

        return view
    }

    private fun validatePinAndJoinQuiz(pin: String) {
        db.collection("quizzes").whereEqualTo("pincode", pin).limit(1).get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "Invalid PIN", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val quizDoc = querySnapshot.documents[0]
                val quizId = quizDoc.id
                val status = quizDoc.getString("status")

                if (status == "open_for_join" || status == "started") {
                    joinQuiz(quizId)
                } else {
                    Toast.makeText(requireContext(), "Quiz not open for joining (Status: $status)", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to validate PIN: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun joinQuiz(quizId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val quizRef = db.collection("quizzes").document(quizId)

        quizRef.update(
            "participants",
            FieldValue.arrayUnion(mapOf("uid" to userId, "score" to 0))
        ).addOnSuccessListener {
            Toast.makeText(requireContext(), "Joined quiz successfully", Toast.LENGTH_SHORT).show()
            navigateToNextScreen(quizId)
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to join quiz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToNextScreen(quizId: String) {
        val fragmentManager = parentFragmentManager
        val transaction = fragmentManager.beginTransaction()
        val playerFragment = PlayerFragment.newInstance(quizId)

        transaction.replace(R.id.container, playerFragment)
        transaction.addToBackStack(null) // Optional, for back navigation
        transaction.commit()

        Toast.makeText(requireContext(), "Navigating to quiz for quizId: $quizId", Toast.LENGTH_SHORT).show()
    }
}
