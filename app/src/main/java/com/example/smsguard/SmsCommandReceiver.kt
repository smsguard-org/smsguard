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
                val text = String.format(
                    Locale.US, "SMSGuard location: %.4f, %.4f (accuracy %dm)\n%s",
                    location.latitude, location.longitude,
                    location.accuracy.toInt(),
                    LocationHelper.mapsLink(location.latitude, location.longitude)
                )
                text
            } else {
                "SMSGuard: Location unavailable. Turn on location and try again."
            }
            SmsSender.send(context, from, reply)
            pendingResult.finish()
        }
    }

    private fun handleAlarm(context: Context, from: String) {
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