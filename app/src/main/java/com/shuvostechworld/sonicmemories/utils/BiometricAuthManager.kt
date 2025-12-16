package com.shuvostechworld.sonicmemories.utils

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthManager {

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Good to go
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // If no security is available/setup, we bypass the lock (or could ask to setup)
                // For this app's UX, we allow access to avoid "Brick" state.
                onSuccess()
                return
            }
            else -> {
                // Other errors
                onError()
                return
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Errors like User Canceled, Lockout, etc.
                    onError()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Biometric is valid but not recognized (wrong finger etc.)
                    // Usually we don't close the prompt here, just let them retry.
                    // But we might want to notify UI if needed.
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Sonic Memories")
            .setSubtitle("Touch the sensor to access your diary")
            .setAllowedAuthenticators(authenticators)
            .build()
        
        // Handle issues where device credential might not be available on older APIs or specific configs
        // For simplicity following the Modern Android guideline
        
        biometricPrompt.authenticate(promptInfo)
    }
}
