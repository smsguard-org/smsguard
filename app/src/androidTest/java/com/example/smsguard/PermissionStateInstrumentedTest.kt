package com.example.smsguard

import android.Manifest
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that permission reads are always live rather than cached.
 *
 * The onboarding bug was a snapshot that only refreshed on the launcher callback, so a grant made
 * in system Settings stayed invisible. These tests pin the property the fix relies on: two
 * consecutive reads bracket by a change in the OS must report the change.
 */
@RunWith(AndroidJUnit4::class)
class PermissionStateInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val automation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private var hadRequestedBefore = false

    @Before
    fun resetRequestedFlag() {
        hadRequestedBefore = Prefs.hasRequestedPermissions(context)
        Prefs.setHasRequestedPermissions(context, false)
    }

    @After
    fun restore() {
        automation.revokeRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        Prefs.setHasRequestedPermissions(context, hadRequestedBefore)
    }

    @Test
    fun statusOf_followsTheSystemRatherThanACachedValue() {
        automation.revokeRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        assertFalse(PermissionState.isGranted(context, Manifest.permission.RECEIVE_SMS))

        automation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECEIVE_SMS
        )
        assertTrue(PermissionState.isGranted(context, Manifest.permission.RECEIVE_SMS))
        assertEquals(
            PermissionStatus.GRANTED,
            PermissionState.statusOf(context, Manifest.permission.RECEIVE_SMS)
        )

        automation.revokeRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        assertFalse(PermissionState.isGranted(context, Manifest.permission.RECEIVE_SMS))
    }

    @Test
    fun statusOf_isNotBlockedBeforeAnyRequestWasMade() {
        automation.revokeRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        Prefs.setHasRequestedPermissions(context, false)

        assertFalse(
            "A permission that was never requested must not be reported as blocked",
            PermissionState.statusOf(context, Manifest.permission.RECEIVE_SMS) ==
                PermissionStatus.BLOCKED
        )
    }

    @Test
    fun statusOf_reportsBlockedOnceTheSystemWillNotPromptAgain() {
        automation.revokeRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)
        Prefs.setHasRequestedPermissions(context, true)

        assertEquals(
            PermissionStatus.BLOCKED,
            PermissionState.statusOf(context, Manifest.permission.RECEIVE_SMS)
        )
    }

    @Test
    fun mergeGrants_contextOverloadFallsBackToLiveState() {
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)

        val merged = PermissionState.mergeGrants(
            context = context,
            callback = emptyMap(),
            requested = listOf(Manifest.permission.RECEIVE_SMS)
        )

        assertEquals(true, merged[Manifest.permission.RECEIVE_SMS])
    }

    @Test
    fun missing_dropsPermissionsThatAreGranted() {
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECEIVE_SMS)

        assertFalse(
            Manifest.permission.RECEIVE_SMS in
                PermissionState.missing(context, listOf(Manifest.permission.RECEIVE_SMS))
        )
    }
}
