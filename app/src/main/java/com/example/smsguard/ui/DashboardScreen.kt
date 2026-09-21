package com.example.smsguard.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smsguard.R
import com.example.smsguard.ui.components.SectionCard
import com.example.smsguard.ui.components.SectionHeader
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    serviceEnabled: Boolean,
    onServiceToggle: (Boolean) -> Unit,
    serviceStartTime: Long,
    commandCount: Int,
    alertCount: Int,
    commandHistory: List<String>,
    missingPermissionsCount: Int,
    onFixPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dashboard Header
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column {
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
        }

        // Main Service Toggle
        item(span = { GridItemSpan(2) }) {
            ServiceStatusCard(
                enabled = serviceEnabled,
                onToggle = onServiceToggle,
                startTime = serviceStartTime,
                hasError = missingPermissionsCount > 0
            )
        }

        // Summary Stats
        item {
            StatCard(
                title = "Alerts Fired",
                value = alertCount.toString(),
                icon = Icons.Default.NotificationsActive,
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
        item {
            StatCard(
                title = "Cmds Received",
                value = commandCount.toString(),
                icon = Icons.Default.Sms,
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        // Recent Activity
        item(span = { GridItemSpan(2) }) {
            SectionHeader(title = "Recent Activity", icon = Icons.Default.History)
        }

        if (commandHistory.isNotEmpty()) {
            items(commandHistory.size, span = { GridItemSpan(2) }) { index ->
                val parts = commandHistory[index].split("|")
                val name = parts.getOrNull(0) ?: "Unknown"
                val time = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                
                val timeString = DateUtils.getRelativeTimeSpanString(
                    time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()

                RecentActivityCard(
                    lastCommand = "#$name",
                    time = timeString,
                    status = "Executed"
                )
            }
        } else {
            item(span = { GridItemSpan(2) }) {
                SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No activity yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Send a command like #LOCATE#PIN to see it here.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Permissions Status
        item(span = { GridItemSpan(2) }) {
            SectionCard(
                containerColor = if (missingPermissionsCount == 0)
                    MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (missingPermissionsCount == 0) "All systems operational"
                            else "Action required: $missingPermissionsCount permissions missing",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (missingPermissionsCount > 0) {
                        FilledTonalButton(onClick = onFixPermissions) {
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

@Composable
private fun ServiceStatusCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    startTime: Long,
    hasError: Boolean
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(enabled) {
        if (enabled) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val runningTime = if (enabled && startTime > 0) {
        val seconds = (currentTime - startTime) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    } else {
        "00:00"
    }

    val dotColor = when {
        hasError -> MaterialTheme.colorScheme.error
        enabled -> Color(0xFF4CAF50) // Green
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) // Grey
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                
                Column {
                    val statusText = when {
                        hasError -> "System Issue"
                        enabled -> "Protection Active"
                        else -> "Protection Paused"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (enabled && !hasError) "Online for $runningTime" 
                               else if (hasError) "Check permissions"
                               else "System is offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
