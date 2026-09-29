package com.example.smsguard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Whether a runtime permission is satisfied, still refusable, or permanently unavailable. */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    BLOCKED
}

/**
 * Central permission truth.
 *
 * From Android 15 the SMS runtime permissions are hard-restricted for apps that were not
 * installed by an app store, so the system can refuse the request without ever showing a
 * dialog. The only user-visible remedy lives behind Settings, which the app has to be able to
 * detect and route to. Keeping the logic here stops the onboarding and dashboard from
 * disagreeing about the same permission.
 */
object PermissionState {

    val SMS_PERMISSIONS = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)

    val LOCATION_PERMISSIONS = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** Without this the app cannot see the commands that drive it. */
    val requiredPermissions = listOf(Manifest.permission.RECEIVE_SMS)

    val optionalPermissions = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val allPermissions = (requiredPermissions + optionalPermissions).distinct()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Classifies a permission from its raw signals.
     *
     * `showRationale` is false both before the first request and after a permanent denial, so
     * [hasRequested] is required to tell those two states apart. Reporting BLOCKED too early
     * would strand a user on a permission the system would happily have prompted for.
     */
    fun classify(granted: Boolean, showRationale: Boolean, hasRequested: Boolean): PermissionStatus =
        when {
            granted -> PermissionStatus.GRANTED
            showRationale -> PermissionStatus.DENIED
            hasRequested -> PermissionStatus.BLOCKED
            else -> PermissionStatus.DENIED
        }

    fun statusOf(context: Context, permission: String): PermissionStatus {
        if (isGranted(context, permission)) return PermissionStatus.GRANTED
        val activity = activityOf(context)
            ?: return PermissionStatus.DENIED
        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        return classify(
            granted = false,
            showRationale = showRationale,
            hasRequested = Prefs.hasRequestedPermissions(context)
        )
    }

    fun statusOf(context: Context, permissions: List<String>): PermissionStatus =
        aggregate(permissions.map { statusOf(context, it) })

    /** Folds per-permission states into one, letting a block outrank a plain refusal. */
    fun aggregate(statuses: List<PermissionStatus>): PermissionStatus = when {
        statuses.isEmpty() -> PermissionStatus.GRANTED
        statuses.all { it == PermissionStatus.GRANTED } -> PermissionStatus.GRANTED
        statuses.any { it == PermissionStatus.BLOCKED } -> PermissionStatus.BLOCKED
        else -> PermissionStatus.DENIED
    }

    /**
     * Reconciles a `RequestMultiplePermissions` result with live system state.
     *
     * The result map only carries entries for the permissions the system actually prompted
     * for, so anything absent has to be re-read or it keeps a stale value. An explicit `false`
     * stays `false`; only omitted keys fall back to [liveLookup].
     */
    fun mergeGrants(
        callback: Map<String, Boolean>,
        requested: List<String>,
        liveLookup: (String) -> Boolean
    ): Map<String, Boolean> = requested.associateWith { permission ->
        callback[permission] ?: liveLookup(permission)
    }

    fun mergeGrants(
        context: Context,
        callback: Map<String, Boolean>,
        requested: List<String>
    ): Map<String, Boolean> = mergeGrants(callback, requested) { isGranted(context, it) }

    fun missing(context: Context, permissions: List<String> = allPermissions): List<String> =
        permissions.filter { !isGranted(context, it) }

    /** Intent for the app's own Settings page, the only place a blocked permission can be cleared. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun activityOf(context: Context): Activity? {
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
