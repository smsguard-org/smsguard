package com.example.smsguard.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smsguard.R
import com.example.smsguard.ui.components.SectionCard
import com.example.smsguard.ui.components.SectionHeader

@Composable
fun DashboardScreen(
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

        // System Overview
        item(span = { GridItemSpan(2) }) {
            SectionHeader(title = "Security Overview", icon = Icons.Default.Security)
        }

        item(span = { GridItemSpan(2) }) {
            SectionCard {
                Text(
                    text = "SMS Commands are active and protected by your secure PIN. The battery beacon is monitoring and will alert your trusted contact if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
