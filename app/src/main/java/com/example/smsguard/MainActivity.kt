package com.example.smsguard

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableIntStateOf(Prefs.themeMode(context)) }
            
            SmsguardTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsGuardApp(onThemeChange = { themeMode = it })
                }
            }
        }
    }
}

@Composable
private fun SmsGuardApp(onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    var onboarded by remember { mutableStateOf(Prefs.onboarded(context)) }

    if (!onboarded) {
        OnboardingScreen(
            onComplete = { onboarded = true }
        )
    } else {
        MainScreen(onThemeChange = onThemeChange)
    }
}
