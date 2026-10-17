package com.example.smsguard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.smsguard.PermissionState
import com.example.smsguard.Prefs
import com.example.smsguard.SirenController
import com.example.smsguard.AccessibilityPermission
import com.example.smsguard.ProtectionService
import com.example.smsguard.SmsGuardCommand
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Commands : Screen("commands", "Commands", Icons.AutoMirrored.Filled.ListAlt)
    object Contacts : Screen("contacts", "Contacts", Icons.Default.ContactPhone)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Activity : Screen("activity", "Activity", Icons.Default.History)
}

private const val SIREN_POLL_INTERVAL_MS = 1000L

@Composable
fun MainScreen(onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    
    var permissionTrigger by remember { mutableIntStateOf(0) }
    var lastRequestResult by remember {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }
    
    // Shared state for settings
    var themeMode by remember { mutableIntStateOf(Prefs.themeMode(context)) }
    var serviceEnabled by remember { mutableStateOf(Prefs.serviceEnabled(context)) }
    var smsEnabled by remember { mutableStateOf(Prefs.smsEnabled(context)) }
    var pin by remember { mutableStateOf(Prefs.pin(context)) }
    var beaconEnabled by remember { mutableStateOf(Prefs.batteryBeaconEnabled(context)) }
    var contact by remember { mutableStateOf(Prefs.trustedContact(context)) }
    var threshold by remember { mutableFloatStateOf(Prefs.batteryThreshold(context).toFloat()) }
    var sirenActive by remember { mutableStateOf(Prefs.sirenActive(context)) }
    var showScreenLockPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Prefs.setHasRequestedPermissions(context, true)
        lastRequestResult = PermissionState.mergeGrants(
            context,
            result,
            PermissionState.allPermissions
        )
        permissionTrigger++
    }

    // The dashboard reports a red "System Issue" from this same stale-snapshot pattern; a grant
    // made in system Settings must clear it as soon as the app comes back to the foreground.
    LifecycleResumeEffect(context) {
        permissionTrigger++
        sirenActive = Prefs.sirenActive(context)
        if (Prefs.serviceEnabled(context)) ProtectionService.start(context)
        onPauseOrDispose {}
    }

    // A siren can be started by a #ALARM#PIN SMS while this screen is already open, so poll the
    // persisted flag to surface the in-app stop button without needing a round trip.
    LaunchedEffect(Unit) {
        while (true) {
            sirenActive = Prefs.sirenActive(context)
            delay(SIREN_POLL_INTERVAL_MS)
        }
    }

    val permissionEntries = remember(permissionTrigger, lastRequestResult) {
        PermissionState.entries(context)
    }

    // Show this once whenever protection first becomes active without the optional screen-lock
    // capability, including immediately after the onboarding flow enables protection.
    LaunchedEffect(serviceEnabled) {
        if (serviceEnabled && !AccessibilityPermission.isScreenLockEnabled(context)) {
            showScreenLockPermission = true
        }
    }

    // SIM changes are observed by the foreground protection service. The persistent notification
    // is required so Android may keep the observer alive after the app leaves the screen.
    LaunchedEffect(serviceEnabled) {
        if (serviceEnabled) ProtectionService.start(context)
        else ProtectionService.stop(context)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val items = listOf(Screen.Dashboard, Screen.Commands, Screen.Contacts, Screen.Settings)
                
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
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    serviceEnabled = serviceEnabled,
                    onServiceToggle = {
                        serviceEnabled = it
                        Prefs.setServiceEnabled(context, it)
                        if (it) ProtectionService.start(context)
                        else ProtectionService.stop(context)
                    },
                    sirenActive = sirenActive,
                    onStopSiren = {
                        SirenController.stop(context)
                        sirenActive = false
                    },
                    serviceStartTime = Prefs.serviceStartTime(context),
                    commandCount = Prefs.commandCount(context),
                    alertCount = Prefs.alertCount(context),
                    suspiciousCount = Prefs.suspiciousCount(context),
                    commandHistory = Prefs.commandHistory(context),
                    permissionEntries = permissionEntries,
                    onFixPermissions = {
                        permissionLauncher.launch(
                            PermissionState.missing(context).toTypedArray()
                        )
                    },
                    onOpenAppSettings = {
                        runCatching {
                            context.startActivity(PermissionState.appSettingsIntent(context))
                        }
                    },
                    onSeeAllActivity = {
                        navController.navigate(Screen.Activity.route)
                    }
                )
            }
            composable(Screen.Activity.route) {
                ActivityScreen(
                    commandHistory = Prefs.commandHistory(context),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Commands.route) {
                CommandsScreen()
            }
            composable(Screen.Contacts.route) {
                ContactsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        Prefs.setThemeMode(context, it)
                        onThemeChange(it)
                    },
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

    if (showScreenLockPermission) {
        ScreenLockPermissionDialog(onDismiss = { showScreenLockPermission = false })
    }
}

@Composable
internal fun ScreenLockPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔒 Enable Screen Lock Permission") },
        text = {
            Text(
                "Required for Device Security\n\n" +
                    "To allow SMSGuard to securely lock your device screen, enable its Accessibility Service. " +
                    "This lets the authenticated remote lock command protect your device when you step away.\n\n" +
                    "What to do next:\n" +
                    "1. Tap Go to Settings.\n" +
                    "2. Tap Downloaded Apps or Installed Services.\n" +
                    "3. Select ${context.getString(com.example.smsguard.R.string.app_name)}.\n" +
                    "4. Turn the toggle on."
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching { context.startActivity(AccessibilityPermission.settingsIntent()) }
            }) { Text("Go to Settings") }
        }
    )
}
