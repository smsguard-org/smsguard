package com.example.smsguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smsguard.Prefs
import com.example.smsguard.ProtectionService
import com.example.smsguard.R
import com.example.smsguard.SmsGuardCommand

private val ONBOARDING_PERMISSIONS = listOfNotNull(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.ACCESS_FINE_LOCATION,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
)

private const val WELCOME_STEP = 0
private const val PERMISSIONS_STEP = 1
private const val SECURITY_STEP = 2
private const val DONE_STEP = 3
private const val TOTAL_STEPS = 4

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(WELCOME_STEP) }
    
    var permissionTrigger by remember { mutableIntStateOf(0) }

    var smsEnabled by remember { mutableStateOf(true) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTrigger++ }

    val missingPermissions by remember(permissionTrigger) {
        mutableStateOf(
            ONBOARDING_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }.toSet()
        )
    }

    val smsGranted = Manifest.permission.RECEIVE_SMS !in missingPermissions && 
                     Manifest.permission.SEND_SMS !in missingPermissions
    val locationGranted = Manifest.permission.ACCESS_FINE_LOCATION !in missingPermissions
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS !in missingPermissions
    } else true

    val allGranted = smsGranted && locationGranted && notificationsGranted

    val pinValid = SmsGuardCommand.forPin(pin) != null
    val pinMatches = pin == pinConfirm && pin.isNotEmpty()

    BackHandler(enabled = step > WELCOME_STEP) {
        step--
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            topBar = {
                val animatedProgress by animateFloatAsState(
                    targetValue = (step + 1) / TOTAL_STEPS.toFloat(),
                    label = "ProgressAnimation"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    when (step) {
                        WELCOME_STEP -> FullWidthButton(
                            text = stringResource(R.string.onboarding_continue),
                            onClick = { step++ }
                        )
                        DONE_STEP -> FullWidthButton(
                            text = stringResource(R.string.onboarding_get_started),
                            onClick = {
                                Prefs.setServiceEnabled(context, true)
                                Prefs.setOnboarded(context, true)
                                ProtectionService.start(context)
                                onComplete()
                            }
                        )
                        else -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = { step-- },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.onboarding_back))
                            }
                            if (step == PERMISSIONS_STEP && !allGranted) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(missingPermissions.toTypedArray())
                                    }, 
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Text(stringResource(R.string.grant_permissions_button))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (step == SECURITY_STEP) {
                                            Prefs.setPin(context, pin)
                                            Prefs.setSmsEnabled(context, smsEnabled)
                                        }
                                        step++
                                    },
                                    enabled = when (step) {
                                        SECURITY_STEP -> pinValid && pinMatches
                                        else -> true
                                    },
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Text(stringResource(R.string.onboarding_continue))
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "StepTransition"
            ) { targetStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (targetStep) {
                        WELCOME_STEP -> WelcomeStep()
                        PERMISSIONS_STEP -> PermissionsStep(
                            smsGranted = smsGranted,
                            locationGranted = locationGranted,
                            notificationsGranted = notificationsGranted,
                            onRequest = { permissionLauncher.launch(missingPermissions.toTypedArray()) }
                        )
                        SECURITY_STEP -> SecurityStep(
                            pin = pin,
                            onPinChange = { value ->
                                pin = value.filter { it.isDigit() }.take(SmsGuardCommand.PIN_MAX_LENGTH)
                            },
                            pinConfirm = pinConfirm,
                            onPinConfirmChange = { value ->
                                pinConfirm = value.filter { it.isDigit() }.take(SmsGuardCommand.PIN_MAX_LENGTH)
                            },
                            pinValid = pinValid,
                            pinMatches = pinMatches
                        )
                        DONE_STEP -> DoneStep(
                            smsEnabled = smsEnabled
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun PermissionsStep(
    smsGranted: Boolean,
    locationGranted: Boolean,
    notificationsGranted: Boolean,
    onRequest: () -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_permissions_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_permissions_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    PermissionRow(
        title = "SMS Permission",
        description = "Detect commands and send automated replies",
        granted = smsGranted
    )
    PermissionRow(
        title = stringResource(R.string.perm_location),
        description = stringResource(R.string.perm_location_desc),
        granted = locationGranted
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionRow(
            title = stringResource(R.string.perm_notifications),
            description = stringResource(R.string.perm_notifications_desc),
            granted = notificationsGranted
        )
    }

    if (!(smsGranted && locationGranted && notificationsGranted)) {
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.grant_permissions_button))
        }
    }
}

@Composable
private fun SecurityStep(
    pin: String,
    onPinChange: (String) -> Unit,
    pinConfirm: String,
    onPinConfirmChange: (String) -> Unit,
    pinValid: Boolean,
    pinMatches: Boolean
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_sec_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_sec_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TextField(
        value = pin,
        onValueChange = onPinChange,
        label = { Text(stringResource(R.string.pin_label)) },
        placeholder = { Text(stringResource(R.string.pin_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = pin.isNotEmpty() && !pinValid,
        supportingText = {
            if (pin.isNotEmpty() && !pinValid) {
                Text(stringResource(R.string.pin_too_short))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )

    TextField(
        value = pinConfirm,
        onValueChange = onPinConfirmChange,
        label = { Text(stringResource(R.string.pin_confirm_label)) },
        placeholder = { Text(stringResource(R.string.pin_confirm_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = pinConfirm.isNotEmpty() && !pinMatches,
        supportingText = {
            if (pinConfirm.isNotEmpty() && !pinMatches) {
                Text(stringResource(R.string.pin_mismatch))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DoneStep(
    smsEnabled: Boolean
) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_done_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = stringResource(R.string.onboarding_secure_defaults),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    SummaryRow(stringResource(R.string.enable_sms_commands), if (smsEnabled) "On" else "Off")
    SummaryRow("Protection Mode", "Active")
    Text(
        text = "You can enable background protection and configure the battery beacon in the Settings tab later.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val dotColor = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FullWidthButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(text)
    }
}
