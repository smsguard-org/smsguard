package com.example.smsguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import java.util.Locale

/** Alerts a trusted contact via SMS when battery drops to the configured threshold. */
class BatteryBeaconReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
        if (!Prefs.batteryBeaconEnabled(context)) return
        val contacts = Prefs.trustedContactsList(context)
        val recipients = if (contacts.isNotEmpty()) {
            contacts.map { it.phoneNumber }
        } else {
            val single = Prefs.trustedContact(context)
            if (single.isNotBlank()) listOf(single) else emptyList()
        }
        if (recipients.isEmpty()) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (scale <= 0 || level < 0) return
        val percent = level * 100 / scale
        val threshold = Prefs.batteryThreshold(context)

        if (percent in 0..threshold) {
            if (Prefs.beaconSentLevel(context) == percent) return
            Prefs.setBeaconSentLevel(context, percent)

            val location = LocationHelper.lastKnown(context)
            val body = if (location != null) {
                String.format(
                    Locale.US, "SMSGuard alert: Battery %d%%.\n%s",
                    percent,
                    LocationHelper.mapsLink(location.latitude, location.longitude)
                )
            } else {
                String.format(Locale.US, "SMSGuard alert: Battery %d%%.", percent)
            }
            for (num in recipients) {
                SmsSender.send(context, num, body)
            }
        } else if (percent > threshold + 10) {
            Prefs.setBeaconSentLevel(context, -1)
        }
    }
}