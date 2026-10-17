package com.example.smsguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Sends text messages once the SEND_SMS permission is granted. */
object SmsSender {
    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun send(context: Context, destination: String, body: String) {
        if (destination.isBlank() || !canSend(context)) return
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                // The status report and other detailed replies exceed a single SMS.
                smsManager.sendMultipartTextMessage(destination, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destination, null, body, null, null)
            }
        } catch (_: Exception) {
        }
    }
}