package com.shuvostechworld.sonicmemories.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.shuvostechworld.sonicmemories.MainActivity
import com.shuvostechworld.sonicmemories.databinding.ActivitySignInBinding
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private lateinit var credentialManager: CredentialManager

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialManager = CredentialManager.create(this)

        binding.btnSignIn.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val clientIdRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        val clientId = if (clientIdRes != 0) getString(clientIdRes) else ""

        if (clientId.isEmpty()) {
            Toast.makeText(this, "Google Sign-In configuration missing. Update google-services.json", Toast.LENGTH_LONG).show()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false) // Allow user to choose even if they only have one account
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@SignInActivity,
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                // Determine if it was a user cancellation or other error
                if (e.message?.contains("User canceled") == true || e.message?.contains("Interrupted") == true) {
                    Toast.makeText(this@SignInActivity, "Sign-In Canceled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SignInActivity, "Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid Credential: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Unexpected Credential Type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    AccessibilityUtils.announceToScreenReader(binding.root, "Sign In Successful")
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Auth Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
