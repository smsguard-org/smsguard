package com.example.smsguard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.smsguard.ui.MainScreen
import com.example.smsguard.ui.OnboardingScreen
import com.example.smsguard.ui.theme.SmsguardTheme

/** Single-activity Compose entry point; shows onboarding until configured, then main screen. */
class MainActivity : ComponentActivity() {

    /**
     * Token from a `smsguard://pair?token=...` link that still needs to be handed to the pairing
     * screen. Held as Compose state so both a cold launch (onCreate) and a warm one (onNewIntent)
     * can drive navigation.
     */
    private var pendingPairingToken by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.applySimAlertCountFix(this)
        pendingPairingToken = intent?.pairingToken()
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableIntStateOf(Prefs.themeMode(context)) }
            
            SmsguardTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsGuardApp(
                        initialPairingToken = pendingPairingToken,
                        onPairingTokenConsumed = { pendingPairingToken = null },
                        onThemeChange = { themeMode = it }
                    )
                }
            }
        }
    }

    // launchMode="singleTask" means an incoming link while the app is running lands here instead
    // of creating a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.pairingToken()?.let { pendingPairingToken = it }
    }
}

/** Extracts the pairing token from `smsguard://pair?token=...`, or null for any other intent. */
private fun Intent.pairingToken(): String? =
    data?.takeIf { uri: Uri -> uri.scheme == "smsguard" && uri.host == "pair" }
        ?.getQueryParameter("token")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

@Composable
private fun SmsGuardApp(
    initialPairingToken: String?,
    onPairingTokenConsumed: () -> Unit,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var onboarded by remember { mutableStateOf(Prefs.onboarded(context)) }

    if (!onboarded) {
        OnboardingScreen(
            onComplete = { onboarded = true }
        )
    } else {
        MainScreen(
            onThemeChange = onThemeChange,
            incomingPairingToken = initialPairingToken,
            onIncomingPairingTokenConsumed = onPairingTokenConsumed
        )
    }
}
