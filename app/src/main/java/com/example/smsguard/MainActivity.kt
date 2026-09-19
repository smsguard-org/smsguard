package com.example.smsguard

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smsguard.ui.theme.SmsguardTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsguardTheme {
                SmsGuardApp()
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

    var smsEnabled by remember { mutableStateOf(Prefs.smsEnabled(context)) }
    var pin by remember { mutableStateOf(Prefs.pin(context)) }
    var beaconEnabled by remember { mutableStateOf(Prefs.batteryBeaconEnabled(context)) }
    var contact by remember { mutableStateOf(Prefs.trustedContact(context)) }
    var threshold by remember { mutableFloatStateOf(Prefs.batteryThreshold(context).toFloat()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTrigger++ }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionTrigger++ }

    val missingPermissions by remember(permissionTrigger) {
        mutableStateOf(
            REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val isDefaultSms by remember(permissionTrigger) {
        mutableStateOf(isDefaultSmsApp(context))
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = stringResource(R.string.section_commands)) {
                CommandRow(
                    command = stringResource(R.string.cmd_locate),
                    description = stringResource(R.string.cmd_locate_desc)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                CommandRow(
                    command = stringResource(R.string.cmd_alarm),
                    description = stringResource(R.string.cmd_alarm_desc)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                CommandRow(
                    command = stringResource(R.string.cmd_stop),
                    description = stringResource(R.string.cmd_stop_desc)
                )
                Text(
                    text = stringResource(R.string.cmd_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = stringResource(R.string.section_protection)) {
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
                    placeholder = { Text(stringResource(R.string.pin_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = smsEnabled,
                    supportingText = {
                        if (pin.length < SmsGuardCommand.PIN_MIN_LENGTH) {
                            Text(stringResource(R.string.pin_hint))
                        }
                    }
                )
            }

            SectionCard(title = stringResource(R.string.section_battery)) {
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = beaconEnabled
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.battery_threshold_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = threshold,
                    onValueChange = { value ->
                        val rounded = value.roundToInt().toFloat()
                        threshold = rounded
                        Prefs.setBatteryThreshold(context, rounded.toInt())
                    },
                    valueRange = 1f..20f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.battery_threshold_value, threshold.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SectionCard(title = stringResource(R.string.section_setup)) {
                if (missingPermissions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.permissions_granted),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(stringResource(R.string.permissions_missing))
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }) {
                        Text(stringResource(R.string.grant_permissions))
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.default_sms_note))
                Spacer(Modifier.height(10.dp))
                if (isDefaultSms) {
                    Text(
                        text = stringResource(R.string.default_sms_set),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.default_sms_not_set),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { requestDefaultSmsRole(context, defaultSmsLauncher) },
                        enabled = missingPermissions.isEmpty()
                    ) {
                        Text(stringResource(R.string.set_default_sms))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun CommandRow(command: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = command,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun isDefaultSmsApp(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }
    return runCatching {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }.getOrDefault(false)
}

private fun requestDefaultSmsRole(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return
    launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
}