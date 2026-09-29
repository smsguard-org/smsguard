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
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Whether a permission is satisfied, still refusable, or permanently unavailable. */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    BLOCKED
}

/** How the permissions inside a group combine into a single user-facing state. */
enum class PermissionMode {
    /** Every permission in the group must be granted, e.g. receiving and sending SMS. */
    ALL,

    /**
     * Any one of them is enough, e.g. fine *or* coarse location. Since Android 12 the user can
     * pick "Approximate", which grants only the coarse permission, so requiring fine here would
     * report a permission the app can already use as missing.
     */
    ANY
}

/** One permission as the user perceives it, which may be backed by several raw permissions. */
data class PermissionGroup(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val permissions: List<String>,
    val mode: PermissionMode,
    val required: Boolean = false
)

/** A group paired with its current state. */
data class PermissionEntry(val group: PermissionGroup, val status: PermissionStatus) {
    @get:StringRes
    val labelRes: Int get() = group.labelRes

    @get:StringRes
    val descRes: Int get() = group.descRes
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

    val LOCATION_PERMISSIONS = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** The permissions as the user sees them, in the order they are presented. */
    val permissionGroups: List<PermissionGroup> = buildList {
        add(
            PermissionGroup(
                labelRes = R.string.perm_receive_sms,
                descRes = R.string.perm_receive_sms_desc,
                permissions = listOf(Manifest.permission.RECEIVE_SMS),
                mode = PermissionMode.ALL,
                required = true
            )
        )
        add(
            PermissionGroup(
                labelRes = R.string.perm_send_sms,
                descRes = R.string.perm_send_sms_desc,
                permissions = listOf(Manifest.permission.SEND_SMS),
                mode = PermissionMode.ALL
            )
        )
        add(
            PermissionGroup(
                labelRes = R.string.perm_location,
                descRes = R.string.perm_location_desc,
                permissions = LOCATION_PERMISSIONS,
                mode = PermissionMode.ANY
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionGroup(
                    labelRes = R.string.perm_notifications,
                    descRes = R.string.perm_notifications_desc,
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    mode = PermissionMode.ALL
                )
            )
        }
    }

    val allPermissions: List<String> = permissionGroups.flatMap { it.permissions }.distinct()

    val requiredGroups: List<PermissionGroup> = permissionGroups.filter { it.required }

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

    fun statusOf(context: Context, group: PermissionGroup): PermissionStatus {
        val statuses = group.permissions.map { statusOf(context, it) }
        return when (group.mode) {
            PermissionMode.ALL -> aggregateAll(statuses)
            PermissionMode.ANY -> aggregateAny(statuses)
        }
    }

    /** Conjunction: every permission is needed, and any one being blocked blocks the group. */
    fun aggregateAll(statuses: List<PermissionStatus>): PermissionStatus = when {
        statuses.isEmpty() -> PermissionStatus.GRANTED
        statuses.all { it == PermissionStatus.GRANTED } -> PermissionStatus.GRANTED
        statuses.any { it == PermissionStatus.BLOCKED } -> PermissionStatus.BLOCKED
        else -> PermissionStatus.DENIED
    }

    /**
     * Disjunction: one grant is enough, so a granted sibling outranks a blocked one. Coarse
     * location being granted must not be downgraded by a blocked fine location.
     */
    fun aggregateAny(statuses: List<PermissionStatus>): PermissionStatus = when {
        statuses.isEmpty() -> PermissionStatus.DENIED
        statuses.any { it == PermissionStatus.GRANTED } -> PermissionStatus.GRANTED
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

    /** Every group with its live state, granted ones included. */
    fun entries(context: Context): List<PermissionEntry> =
        permissionGroups.map { PermissionEntry(it, statusOf(context, it)) }

    fun unresolved(context: Context): List<PermissionEntry> =
        entries(context).filter { it.status != PermissionStatus.GRANTED }

    /**
     * Raw permissions still worth prompting for.
     *
     * A group that already reads as granted contributes nothing, so a blocked fine location
     * stops nagging once coarse location is available.
     */
    fun missing(context: Context): List<String> =
        permissionGroups.filter { statusOf(context, it) != PermissionStatus.GRANTED }
            .flatMap { group -> group.permissions.filter { !isGranted(context, it) } }

    /** Raw-permission variant for callers that track individual permissions rather than groups. */
    fun missing(context: Context, permissions: List<String> = allPermissions): List<String> =
        permissions.filter { !isGranted(context, it) }

    @StringRes
    fun statusRes(status: PermissionStatus): Int = when (status) {
        PermissionStatus.GRANTED -> R.string.perm_status_granted
        PermissionStatus.DENIED -> R.string.perm_status_denied
        PermissionStatus.BLOCKED -> R.string.perm_status_blocked
    }

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
