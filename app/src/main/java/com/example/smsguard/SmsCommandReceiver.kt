package com.example.smsguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.util.Locale

class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.serviceEnabled(context)) return
        if (!Prefs.smsEnabled(context)) return

        val pin = Prefs.pin(context)
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val from = message.originatingAddress
            val body = (message.messageBody ?: "").trim().uppercase(Locale.US)
            val match = COMMAND_PATTERN.find(body) ?: return
            val command = match.groupValues[1]
            val commandPin = match.groupValues[2]
            if (commandPin != pin || from == null) return
            
            Prefs.incrementCommandCount(context, command)

            when (command) {
                SmsGuardCommand.LOCATE -> handleLocate(context, from)
                SmsGuardCommand.ALARM -> handleAlarm(context, from)
                SmsGuardCommand.STOP -> handleStop(context, from)
            }
            return
        }
    }

    private fun handleLocate(context: Context, from: String) {
        if (!LocationHelper.hasPermission(context)) {
            SmsSender.send(
                context, from,
                "SMSGuard: Location permission not granted. Open SMSGuard and grant location access."
            )
            return
        }
        val pendingResult = goAsync()
        LocationHelper.requestFreshFix(context) { location ->
            val reply = if (location != null) {
                String.format(
                    Locale.US,
                    "SMSGuard Location:\nLat: %.6f\nLong: %.6f\nMaps: %s",
                    location.latitude,
                    location.longitude,
                    LocationHelper.mapsLink(location.latitude, location.longitude)
                )
            } else {
                "SMSGuard: Unable to fetch fresh GPS fix. Ensure location is enabled."
            }
            SmsSender.send(context, from, reply)
            pendingResult.finish()
        }
    }

    private fun handleAlarm(context: Context, from: String) {
        Prefs.incrementAlertCount(context)
        val intent = Intent(context, SirenService::class.java)
            .setAction(SirenServiceActions.START)
        ContextCompat.startForegroundService(context, intent)
        SmsSender.send(context, from, "SMSGuard: Alarm started.")
    }

    private fun handleStop(context: Context, from: String) {
        context.stopService(Intent(context, SirenService::class.java))
        SmsSender.send(context, from, "SMSGuard: Alarm stopped.")
    }

    private companion object {
        val COMMAND_PATTERN = Regex("""#([A-Z]+)#(\d{4,8})#?""")
    }
}