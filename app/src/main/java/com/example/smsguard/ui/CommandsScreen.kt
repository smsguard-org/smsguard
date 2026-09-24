package com.example.smsguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smsguard.Prefs
import com.example.smsguard.SmsGuardCommand

enum class CommandCategory(val label: String) {
    ALL("All"),
    SECURITY("Security"),
    CONNECTIVITY("Connectivity"),
    HARDWARE("Hardware"),
    EMERGENCY("Emergency")
}

data class CommandItem(
    val key: String,
    val title: String,
    val description: String,
    val format: String,
    val category: CommandCategory,
    val icon: ImageVector
)

@Composable
fun CommandsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val allCommands = remember {
        listOf(
            CommandItem(
                key = SmsGuardCommand.LOCATE,
                title = "GPS Location Fix",
                description = "Auto-replies with precise GPS coordinates and a Google Maps link.",
                format = "#LOCATE#PIN",
                category = CommandCategory.SECURITY,
                icon = Icons.Default.LocationOn
            ),
            CommandItem(
                key = SmsGuardCommand.ALARM,
                title = "Emergency Siren",
                description = "Forces a maximum volume siren alert, overriding silent or Do Not Disturb.",
                format = "#ALARM#PIN",
                category = CommandCategory.SECURITY,
                icon = Icons.Default.NotificationsActive
            ),
            CommandItem(
                key = SmsGuardCommand.STOP,
                title = "Stop Siren",
                description = "Immediately halts an active siren alarm service.",
                format = "#STOP#PIN",
                category = CommandCategory.SECURITY,
                icon = Icons.Default.Stop
            ),
            CommandItem(
                key = SmsGuardCommand.LOCK,
                title = "Remote Device Lock",
                description = "Remotely locks device screen and secures active user session.",
                format = "#LOCK#PIN",
                category = CommandCategory.SECURITY,
                icon = Icons.Default.Lock
            ),
            CommandItem(
                key = SmsGuardCommand.DATA,
                title = "Mobile Data & Network",
                description = "Queries active cellular data network connection and carrier details.",
                format = "#DATA#PIN",
                category = CommandCategory.CONNECTIVITY,
                icon = Icons.Default.CellTower
            ),
            CommandItem(
                key = SmsGuardCommand.WIFI,
                title = "Wi-Fi Network Info",
                description = "Queries Wi-Fi connection status, connected network SSID, and local IP.",
                format = "#WIFI#PIN",
                category = CommandCategory.CONNECTIVITY,
                icon = Icons.Default.Wifi
            ),
            CommandItem(
                key = SmsGuardCommand.BATTERY,
                title = "Battery & Power Health",
                description = "Replies with battery percentage, charging state, and temperature.",
                format = "#BATTERY#PIN",
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.BatteryFull
            ),
            CommandItem(
                key = SmsGuardCommand.FLASH,
                title = "Flashlight Strobe Beacon",
                description = "Triggers camera LED strobe light for 15 seconds to locate phone in the dark.",
                format = "#FLASH#PIN",
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.FlashlightOn
            ),
            CommandItem(
                key = SmsGuardCommand.INFO,
                title = "Device Model & Specs",
                description = "Replies with device model, Android OS version, and system uptime.",
                format = "#INFO#PIN",
                category = CommandCategory.HARDWARE,
                icon = Icons.Default.Info
            ),
            CommandItem(
                key = SmsGuardCommand.CALLME,
                title = "Callback Request Alert",
                description = "Requests urgent phone callback and logs alert to trusted contact.",
                format = "#CALLME#PIN",
                category = CommandCategory.EMERGENCY,
                icon = Icons.Default.Phone
            ),
            CommandItem(
                key = SmsGuardCommand.CONTACTS,
                title = "Trusted Contacts Info",
                description = "Replies with configured trusted contact details over SMS.",
                format = "#CONTACTS#PIN",
                category = CommandCategory.EMERGENCY,
                icon = Icons.Default.Contacts
            ),
            CommandItem(
                key = SmsGuardCommand.WIPE,
                title = "Remote Safety Wipe Alert",
                description = "Triggers remote emergency wipe alert and locks device for safety.",
                format = "#WIPE#PIN",
                category = CommandCategory.EMERGENCY,
                icon = Icons.Default.Delete
            )
        )
    }

    // State map for toggles (defaults to false for all commands after onboard)
    val enabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            allCommands.forEach { cmd ->
                put(cmd.key, Prefs.isCommandEnabled(context, cmd.key))
            }
        }
    }

    var selectedCategory by remember { mutableStateOf(CommandCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCommands = remember(selectedCategory, searchQuery, enabledStates.size) {
        allCommands.filter { cmd ->
            val matchesCategory = selectedCategory == CommandCategory.ALL || cmd.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                    cmd.title.contains(searchQuery, ignoreCase = true) ||
                    cmd.key.contains(searchQuery, ignoreCase = true) ||
                    cmd.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val activeCount = enabledStates.values.count { it }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header & Summary Stats with Enable All button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SMS Command Controls",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "All commands are disabled by default. Enable specific commands below or enable all at once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Top Control Card with Enable All Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Commands",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$activeCount / ${allCommands.size} Enabled",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (activeCount < allCommands.size) {
                                Button(
                                    onClick = {
                                        allCommands.forEach { cmd ->
                                            enabledStates[cmd.key] = true
                                        }
                                        Prefs.setAllCommandsEnabled(context, allCommands.map { it.key }, true)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Enable All", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        allCommands.forEach { cmd ->
                                            enabledStates[cmd.key] = false
                                        }
                                        Prefs.setAllCommandsEnabled(context, allCommands.map { it.key }, false)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Disable All", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search commands by name or #keyword...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }

        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CommandCategory.entries) { category ->
                    val selected = selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Command Cards
        if (filteredCommands.isNotEmpty()) {
            items(filteredCommands, key = { it.key }) { command ->
                val isEnabled = enabledStates[command.key] ?: false

                CommandCard(
                    command = command,
                    isEnabled = isEnabled,
                    onToggleChanged = { newState ->
                        enabledStates[command.key] = newState
                        Prefs.setCommandEnabled(context, command.key, newState)
                    }
                )
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No matching commands found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Try clearing search filters or changing category selection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    command: CommandItem,
    isEnabled: Boolean,
    onToggleChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEnabled) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row with Icon, Title, and Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = command.icon,
                            contentDescription = null,
                            tint = if (isEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = command.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "#${command.key}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
            }

            // Description
            Text(
                text = command.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Format Badge & Status Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Syntax: ${command.format}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val statusText = if (isEnabled) "Active" else "Disabled"
                val statusColor = if (isEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}
