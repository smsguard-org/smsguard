package com.example.smsguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement as ComposeArrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smsguard.ui.OnboardingScreen
import com.example.smsguard.ui.theme.SmsguardTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsguardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsGuardApp()
                }
            }
        }
    }
}

private val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.POST_NOTIFICATIONS
)

@Composable
private fun SmsGuardApp() {
    val context = LocalContext.current
    var permissionTrigger by remember { mutableIntStateOf(0) }
    var onboarded by remember { mutableStateOf(Prefs.onboarded(context)) }

    if (!onboarded) {
        OnboardingScreen(
            onComplete = { onboarded = true }
        )
        return
    }

    var smsEnabled by remember { mutableStateOf(Prefs.smsEnabled(context)) }
    var pin by remember { mutableStateOf(Prefs.pin(context)) }
    var beaconEnabled by remember { mutableStateOf(Prefs.batteryBeaconEnabled(context)) }
    var contact by remember { mutableStateOf(Prefs.trustedContact(context)) }
    var threshold by remember { mutableFloatStateOf(Prefs.batteryThreshold(context).toFloat()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTrigger++ }

    val missingPermissions by remember(permissionTrigger) {
        mutableStateOf(
            REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        )
    }

    Scaffold { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = ComposeArrangement.spacedBy(16.dp),
            verticalArrangement = ComposeArrangement.spacedBy(16.dp)
        ) {
            // Dashboard Header
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "System Secure & Active",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Summary Stats
            item {
                StatCard(
                    title = "Total Alerts",
                    value = "12",
                    icon = Icons.Default.NotificationsActive,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
            item {
                StatCard(
                    title = "Commands",
                    value = "45",
                    icon = Icons.Default.Sms,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }

            // Recent Activity
            item(span = { GridItemSpan(2) }) {
                RecentActivityCard(
                    lastCommand = "#LOCATE",
                    time = "2 hours ago",
                    status = "Success"
                )
            }

            // Section: Protection Settings
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = "Core Protection", icon = Icons.Default.Security)
            }

            item(span = { GridItemSpan(2) }) {
                SectionCard {
                    SwitchRow(
                        title = stringResource(R.string.enable_sms_commands),
                        subtitle = if (smsEnabled) stringResource(R.string.sms_commands_enabled)
                        else stringResource(R.string.sms_commands_disabled),
                        checked = smsEnabled,
                        onCheckedChange = {
                            smsEnabled = it
                            Prefs.setSmsEnabled(context, it)
                        }
                    )
                    if (smsEnabled) {
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = pin,
                            onValueChange = { value ->
                                val digits = value.filter { it.isDigit() }.take(SmsGuardCommand.PIN_MAX_LENGTH)
                                if (digits.isNotEmpty()) {
                                    pin = digits
                                    Prefs.setPin(context, digits)
                                }
                            },
                            label = { Text(stringResource(R.string.pin_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section: Battery Beacon
            item(span = { GridItemSpan(2) }) {
                SectionHeader(title = "Battery Guardian", icon = Icons.Default.BatteryChargingFull)
            }

            item(span = { GridItemSpan(2) }) {
                SectionCard {
                    SwitchRow(
                        title = stringResource(R.string.enable_battery_beacon),
                        subtitle = if (beaconEnabled) stringResource(R.string.battery_beacon_enabled)
                        else stringResource(R.string.battery_beacon_disabled),
                        checked = beaconEnabled,
                        onCheckedChange = {
                            beaconEnabled = it
                            Prefs.setBatteryBeaconEnabled(context, it)
                        }
                    )
                    if (beaconEnabled) {
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = contact,
                            onValueChange = {
                                contact = it
                                Prefs.setTrustedContact(context, it)
                            },
                            label = { Text(stringResource(R.string.trusted_contact_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Alert Threshold: ${threshold.toInt()}%")
                        Slider(
                            value = threshold,
                            onValueChange = { value ->
                                val rounded = value.roundToInt().toFloat()
                                threshold = rounded
                                Prefs.setBatteryThreshold(context, rounded.toInt())
                            },
                            valueRange = 1f..20f,
                            steps = 18
                        )
                    }
                }
            }

            // Permissions Status
            item(span = { GridItemSpan(2) }) {
                SectionCard(
                    containerColor = if (missingPermissions.isEmpty())
                        MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = ComposeArrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Permissions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (missingPermissions.isEmpty()) "All systems operational"
                                else "Action required: ${missingPermissions.size} permissions missing",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (missingPermissions.isNotEmpty()) {
                            FilledTonalButton(onClick = {
                                permissionLauncher.launch(missingPermissions.toTypedArray())
                            }) {
                                Text("Fix")
                            }
                        }
                    }
                }
            }
            
            item(span = { GridItemSpan(2) }) {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = ComposeArrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RecentActivityCard(
    lastCommand: String,
    time: String,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = ComposeArrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Last Activity", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "$lastCommand executed",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "$time • $status", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = ComposeArrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionCard(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = ComposeArrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
