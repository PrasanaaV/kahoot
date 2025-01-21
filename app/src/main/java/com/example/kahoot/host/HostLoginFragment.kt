package com.example.kahoot.host

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kahoot.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider

class HostLoginFragment : Fragment() {

    companion object {
        // Request code for Google Sign-In
        private const val RC_GOOGLE_SIGN_IN = 101
        private const val TAG = "HostLoginFragment"
    }

    private lateinit var auth: FirebaseAuth

    // UI elements
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var googleSignInButton: Button
    private lateinit var githubSignInButton: Button

    // Google Sign-In client
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // Replace with your real web_client_id from the Firebase console
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_host_login, container, false)

        emailEditText = view.findViewById(R.id.emailEditText)
        passwordEditText = view.findViewById(R.id.passwordEditText)
        signInButton = view.findViewById(R.id.signInButton)
        registerButton = view.findViewById(R.id.registerButton)
        progressBar = view.findViewById(R.id.loginProgressBar)
        googleSignInButton = view.findViewById(R.id.googleSignInButton)
        githubSignInButton = view.findViewById(R.id.githubSignInButton)

        // Existing email/password sign-in
        signInButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            if (validateInputs(email, password)) {
                signInUserWithEmail(email, password)
            }
        }

        // Existing email/password register
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            if (validateInputs(email, password)) {
                registerUserWithEmail(email, password)
            }
        }

        // NEW: Sign in with Google
        googleSignInButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        }

        return view
    }

    // Validate email/password
    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            TextUtils.isEmpty(email) -> {
                Toast.makeText(requireContext(), "Email is required.", Toast.LENGTH_SHORT).show()
                false
            }
            TextUtils.isEmpty(password) -> {
                Toast.makeText(requireContext(), "Password is required.", Toast.LENGTH_SHORT).show()
                false
            }
            password.length < 6 -> {
                Toast.makeText(requireContext(), "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    // Handle email/password sign in
    private fun signInUserWithEmail(email: String, password: String) {
        progressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Sign in successful!", Toast.LENGTH_SHORT).show()
                    navigateToHostHomeFragment()
                } else {
                    val message = task.exception?.localizedMessage ?: "Login failed."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Handle email/password registration
    private fun registerUserWithEmail(email: String, password: String) {
        progressBar.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Account created successfully!", Toast.LENGTH_SHORT).show()
                    navigateToHostHomeFragment()
                } else {
                    val message = task.exception?.localizedMessage ?: "Registration failed."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Start GitHub sign-in flow using OAuthProvider
    private fun signInWithGitHub() {
        val provider = OAuthProvider.newBuilder("github.com")
        // You can add additional scopes if needed. Example:
        // val scopes = listOf("user:email")
        // provider.scopes = scopes

        // Check if there's already a pending result (to handle 2FA, for example)
        val pendingResultTask = auth.pendingAuthResult
        if (pendingResultTask != null) {
            // There's something already here! Finish the sign-in for your user.
            pendingResultTask
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "GitHub sign-in successful!", Toast.LENGTH_SHORT).show()
                    navigateToHostHomeFragment()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "GitHub sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // There's no pending result so you need to start the sign-in flow.
            auth.startActivityForSignInWithProvider(requireActivity(), provider.build())
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "GitHub sign-in successful!", Toast.LENGTH_SHORT).show()
                    navigateToHostHomeFragment()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "GitHub sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Receive the result from Google Sign-In
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
                    if (account != null) {
                        firebaseAuthWithGoogle(account)
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Google sign-in failed.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: ApiException) {
                    progressBar.visibility = View.GONE
                    Log.e(TAG, "Google sign-in error: ${e.message}", e)
                    Toast.makeText(requireContext(), "Google sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Google sign-in canceled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Exchange Google Sign-In token for a Firebase credential
    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Google sign-in successful!", Toast.LENGTH_SHORT).show()
                    navigateToHostHomeFragment()
                } else {
                    val message = task.exception?.localizedMessage ?: "Google sign-in failed."
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Navigate to your host dashboard on successful sign-in
    private fun navigateToHostHomeFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, HostHomeFragment())
            .commit()
    }
}
