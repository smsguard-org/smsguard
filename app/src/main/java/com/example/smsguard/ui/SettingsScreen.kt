package com.example.smsguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smsguard.R
import com.example.smsguard.ui.components.SectionCard
import com.example.smsguard.ui.components.SectionHeader
import com.example.smsguard.ui.components.SwitchRow

@Composable
fun SettingsScreen(
    smsEnabled: Boolean,
    onSmsEnabledChange: (Boolean) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    beaconEnabled: Boolean,
    onBeaconEnabledChange: (Boolean) -> Unit,
    contact: String,
    onContactChange: (String) -> Unit,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Section: Protection Settings
        item {
            SectionHeader(title = "Core Protection", icon = Icons.Default.Security)
        }

        item {
            SectionCard {
                SwitchRow(
                    title = stringResource(R.string.enable_sms_commands),
                    subtitle = if (smsEnabled) stringResource(R.string.sms_commands_enabled)
                    else stringResource(R.string.sms_commands_disabled),
                    checked = smsEnabled,
                    onCheckedChange = onSmsEnabledChange
                )
                if (smsEnabled) {
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = pin,
                        onValueChange = onPinChange,
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
        item {
            SectionHeader(title = "Battery Guardian", icon = Icons.Default.BatteryChargingFull)
        }

        item {
            SectionCard {
                SwitchRow(
                    title = stringResource(R.string.enable_battery_beacon),
                    subtitle = if (beaconEnabled) stringResource(R.string.battery_beacon_enabled)
                    else stringResource(R.string.battery_beacon_disabled),
                    checked = beaconEnabled,
                    onCheckedChange = onBeaconEnabledChange
                )
                if (beaconEnabled) {
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = contact,
                        onValueChange = onContactChange,
                        label = { Text(stringResource(R.string.trusted_contact_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Alert Threshold: ${threshold.toInt()}%")
                    Slider(
                        value = threshold,
                        onValueChange = onThresholdChange,
                        valueRange = 1f..20f,
                        steps = 18
                    )
                }
            }
        }
        
        item {
            Spacer(Modifier.height(24.dp))
        }
    }
}
