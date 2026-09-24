package com.example.smsguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores the protection notification/service after a reboot when protection mode is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if ((action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) &&
            Prefs.serviceEnabled(context)
        ) {
            ProtectionService.start(context)
        }
    }
}
