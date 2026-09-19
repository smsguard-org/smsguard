package com.example.smsguard.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smsguard.Prefs
import com.example.smsguard.SmsGuardCommand
import kotlin.math.roundToInt

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.POST_NOTIFICATIONS
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    
    var permissionTrigger by remember { mutableIntStateOf(0) }
    
    // Shared state for settings
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val items = listOf(Screen.Dashboard, Screen.Settings)
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    missingPermissionsCount = missingPermissions.size,
                    onFixPermissions = {
                        permissionLauncher.launch(missingPermissions.toTypedArray())
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    smsEnabled = smsEnabled,
                    onSmsEnabledChange = {
                        smsEnabled = it
                        Prefs.setSmsEnabled(context, it)
                    },
                    pin = pin,
                    onPinChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(SmsGuardCommand.PIN_MAX_LENGTH)
                        if (digits.isNotEmpty()) {
                            pin = digits
                            Prefs.setPin(context, digits)
                        }
                    },
                    beaconEnabled = beaconEnabled,
                    onBeaconEnabledChange = {
                        beaconEnabled = it
                        Prefs.setBatteryBeaconEnabled(context, it)
                    },
                    contact = contact,
                    onContactChange = {
                        contact = it
                        Prefs.setTrustedContact(context, it)
                    },
                    threshold = threshold,
                    onThresholdChange = { value ->
                        val rounded = value.roundToInt().toFloat()
                        threshold = rounded
                        Prefs.setBatteryThreshold(context, rounded.toInt())
                    }
                )
            }
        }
    }
}
