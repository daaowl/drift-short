package com.localshorts.app

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localshorts.app.ui.HollowRoot

/**
 * Single activity. FragmentActivity is needed for the system unlock prompt.
 * Screenshots and screen recording are always allowed: FLAG_SECURE is never set.
 *
 * App lock asks once per fresh start of the app: after it was swiped away, closed by the system,
 * or the phone was turned off. Switching to another app and back does not ask again.
 */
class MainActivity : FragmentActivity() {
    /** True while the app lock screen should cover the content. */
    val locked = mutableStateOf(false)
    private var lockEnabled = false
    private var authInProgress = false
    /** Kept for callers that open system screens; with per-start locking it has nothing to do. */
    var suppressNextLock = false

    companion object {
        /** Lives as long as the app process. A new process (swiped away, killed, rebooted) starts locked. */
        @Volatile
        private var unlockedThisProcess = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        lockEnabled = getSharedPreferences("lock", MODE_PRIVATE).getBoolean("enabled", false)
        if (lockEnabled && !unlockedThisProcess) locked.value = true
        setContent {
            val vm: MainViewModel = viewModel()
            HollowRoot(vm, this)
        }
    }

    /** Mirrors the setting into a synchronous store so the lock can engage before the UI loads. */
    fun setLockEnabled(on: Boolean) {
        // Turning the lock on from Settings already passed an unlock, so don't ask again this run.
        // (Only on an off -> on change: at a cold start the lock is already on and must still ask.)
        if (on && !lockEnabled) unlockedThisProcess = true
        lockEnabled = on
        getSharedPreferences("lock", MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
        if (!on) locked.value = false
    }

    fun canUseDeviceAuth(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onResult: (Boolean) -> Unit = {}) {
        if (authInProgress) return
        if (!canUseDeviceAuth()) {
            // No screen lock on the phone: nothing to verify against, so don't trap the user
            locked.value = false
            onResult(false)
            return
        }
        authInProgress = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authInProgress = false
                unlockedThisProcess = true
                locked.value = false
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authInProgress = false
                onResult(false)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hollow")
            .setSubtitle("Use your fingerprint, face, or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
