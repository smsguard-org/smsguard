package com.example.smsguard

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility service used exclusively to lock this device after an authenticated
 * remote lock command. Android only permits this action while the user has enabled the service.
 */
class ScreenLockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun lockScreen(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    companion object {
        @Volatile
        private var instance: ScreenLockAccessibilityService? = null

        fun lockDevice(): Boolean = instance?.lockScreen() == true
    }
}
