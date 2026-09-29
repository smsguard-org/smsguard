package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the stale-permission-state bug where onboarding kept reporting SMS as
 * unauthorized after the user had granted it in system Settings.
 */
class PermissionStateTest {

    private val receiveSms = "android.permission.RECEIVE_SMS"
    private val sendSms = "android.permission.SEND_SMS"

    @Test
    fun classify_reportsGrantedWhenGranted() {
        val result = PermissionState.classify(
            granted = true,
            showRationale = false,
            hasRequested = false
        )
        assertEquals(PermissionStatus.GRANTED, result)
    }

    @Test
    fun classify_reportsDeniedWhenRationaleIsShown() {
        val result = PermissionState.classify(
            granted = false,
            showRationale = true,
            hasRequested = true
        )
        assertEquals(PermissionStatus.DENIED, result)
    }

    /**
     * shouldShowRequestPermissionRationale is also false before the very first request, so a
     * permission that has never been asked for must not be reported as blocked.
     */
    @Test
    fun classify_doesNotReportBlockedBeforeFirstRequest() {
        val result = PermissionState.classify(
            granted = false,
            showRationale = false,
            hasRequested = false
        )
        assertEquals(PermissionStatus.DENIED, result)
    }

    @Test
    fun classify_reportsBlockedWhenSystemWillNotPromptAgain() {
        val result = PermissionState.classify(
            granted = false,
            showRationale = false,
            hasRequested = true
        )
        assertEquals(PermissionStatus.BLOCKED, result)
    }

    /**
     * A split SMS grant must not let one permission inherit the other's result: the aggregate
     * used to mask a denial whenever the pair was folded into a single boolean.
     */
    @Test
    fun aggregateAll_sendOnlyGrantIsNotEnough() {
        val result = PermissionState.aggregateAll(
            listOf(PermissionStatus.GRANTED, PermissionStatus.DENIED)
        )
        assertEquals(PermissionStatus.DENIED, result)
    }

    @Test
    fun aggregateAll_blockOutranksDenial() {
        val result = PermissionState.aggregateAll(
            listOf(PermissionStatus.DENIED, PermissionStatus.BLOCKED)
        )
        assertEquals(PermissionStatus.BLOCKED, result)
    }

    @Test
    fun aggregateAll_allGrantedIsGranted() {
        val result = PermissionState.aggregateAll(
            listOf(PermissionStatus.GRANTED, PermissionStatus.GRANTED)
        )
        assertEquals(PermissionStatus.GRANTED, result)
    }

    @Test
    fun aggregateAll_emptyIsGranted() {
        assertEquals(PermissionStatus.GRANTED, PermissionState.aggregateAll(emptyList()))
    }

    /**
     * The reported bug: picking "Approximate" in the location dialog grants only the coarse
     * permission and leaves the fine one denied. Treating the pair as a conjunction reported a
     * permission the app can already use as missing, so onboarding never turned green.
     */
    @Test
    fun aggregateAny_coarseOnlyIsEnough() {
        val result = PermissionState.aggregateAny(
            listOf(PermissionStatus.DENIED, PermissionStatus.GRANTED)
        )
        assertEquals(PermissionStatus.GRANTED, result)
    }

    /**
     * A granted coarse location must also survive a fine permission the user permanently
     * refused, otherwise the pair would flip to BLOCKED and demand an impossible fix.
     */
    @Test
    fun aggregateAny_grantedOutranksBlocked() {
        val result = PermissionState.aggregateAny(
            listOf(PermissionStatus.BLOCKED, PermissionStatus.GRANTED)
        )
        assertEquals(PermissionStatus.GRANTED, result)
    }

    @Test
    fun aggregateAny_nothingGrantedIsDenied() {
        val result = PermissionState.aggregateAny(
            listOf(PermissionStatus.DENIED, PermissionStatus.DENIED)
        )
        assertEquals(PermissionStatus.DENIED, result)
    }

    @Test
    fun aggregateAny_blockOutranksDenial() {
        val result = PermissionState.aggregateAny(
            listOf(PermissionStatus.DENIED, PermissionStatus.BLOCKED)
        )
        assertEquals(PermissionStatus.BLOCKED, result)
    }

    @Test
    fun aggregateAny_emptyIsDenied() {
        assertEquals(PermissionStatus.DENIED, PermissionState.aggregateAny(emptyList()))
    }

    /** Only the SMS receive permission gates onboarding; location is one of the optional rows. */
    @Test
    fun requiredGroups_holdsOnlyReceiveSms() {
        val required = PermissionState.requiredGroups
        assertEquals(1, required.size)
        assertEquals(
            listOf("android.permission.RECEIVE_SMS"),
            required.flatMap { it.permissions }
        )
    }

    /** Coarse and fine location are one user-facing row, so they share a group. */
    @Test
    fun permissionGroups_foldsBothLocationPermissionsIntoOneEntry() {
        val location = PermissionState.permissionGroups.single {
            it.labelRes == com.example.smsguard.R.string.perm_location
        }
        assertEquals(
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION"
            ),
            location.permissions
        )
        assertEquals(PermissionMode.ANY, location.mode)
    }

    /**
     * RequestMultiplePermissions only returns entries for the permissions the system actually
     * prompted for, so omitted keys must be re-read rather than left at a stale value.
     */
    @Test
    fun mergeGrants_omittedKeyFallsBackToLiveState() {
        val merged = PermissionState.mergeGrants(
            callback = mapOf(receiveSms to false),
            requested = listOf(receiveSms, sendSms),
            liveLookup = { permission -> permission == sendSms }
        )

        assertEquals(false, merged[receiveSms])
        assertEquals(true, merged[sendSms])
    }

    @Test
    fun mergeGrants_callbackValueIsAuthoritative() {
        val merged = PermissionState.mergeGrants(
            callback = mapOf(receiveSms to true),
            requested = listOf(receiveSms, sendSms),
            liveLookup = { false }
        )

        assertEquals(true, merged[receiveSms])
        assertEquals(false, merged[sendSms])
    }

    @Test
    fun mergeGrants_explicitDenialIsNotMaskedByLiveState() {
        val merged = PermissionState.mergeGrants(
            callback = mapOf(receiveSms to false),
            requested = listOf(receiveSms),
            liveLookup = { true }
        )

        assertEquals(false, merged[receiveSms])
    }

    @Test
    fun mergeGrants_emptyCallbackFallsBackEntirelyToLiveState() {
        val merged = PermissionState.mergeGrants(
            callback = emptyMap(),
            requested = listOf(receiveSms, sendSms),
            liveLookup = { it == receiveSms }
        )

        assertEquals(true, merged[receiveSms])
        assertEquals(false, merged[sendSms])
    }
}
