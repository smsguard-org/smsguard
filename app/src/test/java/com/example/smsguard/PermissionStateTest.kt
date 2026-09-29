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
    fun aggregate_sendOnlyGrantIsNotEnough() {
        val result = PermissionState.aggregate(
            listOf(PermissionStatus.GRANTED, PermissionStatus.DENIED)
        )
        assertEquals(PermissionStatus.DENIED, result)
    }

    @Test
    fun aggregate_blockOutranksDenial() {
        val result = PermissionState.aggregate(
            listOf(PermissionStatus.DENIED, PermissionStatus.BLOCKED)
        )
        assertEquals(PermissionStatus.BLOCKED, result)
    }

    @Test
    fun aggregate_allGrantedIsGranted() {
        val result = PermissionState.aggregate(
            listOf(PermissionStatus.GRANTED, PermissionStatus.GRANTED)
        )
        assertEquals(PermissionStatus.GRANTED, result)
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
