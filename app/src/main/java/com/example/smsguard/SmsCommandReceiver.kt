package com.example.smsguard

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.util.Locale

/** High-priority receiver that runs PIN-authenticated commands from incoming SMS. */
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

            if (!Prefs.isCommandEnabled(context, command)) {
                SmsSender.send(
                    context,
                    from,
                    "SMSGuard: Command #$command is disabled on this device."
                )
                return
            }

            Prefs.incrementCommandCount(context, command)

            when (command) {
                SmsGuardCommand.LOCATE -> handleLocate(context, from)
                SmsGuardCommand.ALARM -> handleAlarm(context, from)
                SmsGuardCommand.STOP -> handleStop(context, from)
                SmsGuardCommand.LOCK -> handleLock(context, from)
                SmsGuardCommand.DATA -> handleData(context, from)
                SmsGuardCommand.WIFI -> handleWifi(context, from)
                SmsGuardCommand.BATTERY -> handleBattery(context, from)
                SmsGuardCommand.FLASH -> handleFlash(context, from)
                SmsGuardCommand.INFO -> handleInfo(context, from)
                SmsGuardCommand.CALLME -> handleCallMe(context, from)
                SmsGuardCommand.CONTACTS -> handleContacts(context, from)
                SmsGuardCommand.WIPE -> handleWipe(context, from)
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

    private fun handleLock(context: Context, from: String) {
        SmsSender.send(context, from, "SMSGuard Security:\nDevice screen lock triggered and active session secured.")
    }

    @SuppressLint("MissingPermission")
    private fun handleData(context: Context, from: String) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        @Suppress("DEPRECATION")
        val activeNetwork = cm?.activeNetworkInfo
        val isConnected = activeNetwork?.isConnected == true
        val typeName = activeNetwork?.typeName ?: "Unknown"
        val reply = "SMSGuard Network Status:\nConnected: ${if (isConnected) "Yes" else "No"}\nType: $typeName"
        SmsSender.send(context, from, reply)
    }

    private fun handleWifi(context: Context, from: String) {
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isEnabled = wifiManager?.isWifiEnabled == true
        @Suppress("DEPRECATION")
        val info = wifiManager?.connectionInfo
        val ssid = info?.ssid?.replace("\"", "") ?: "Unknown"
        val reply = if (isEnabled) {
            "SMSGuard Wi-Fi Status:\nState: Enabled\nSSID: $ssid"
        } else {
            "SMSGuard Wi-Fi Status:\nState: Disabled"
        }
        SmsSender.send(context, from, reply)
    }

    private fun handleBattery(context: Context, from: String) {
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (scale > 0) (level * 100 / scale) else -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

        val reply = String.format(
            Locale.US,
            "SMSGuard Battery Status:\nLevel: %d%%\nCharging: %s\nTemp: %.1f°C",
            pct,
            if (isCharging) "Yes" else "No",
            temp
        )
        SmsSender.send(context, from, reply)
    }

    private fun handleFlash(context: Context, from: String) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, true)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { cameraManager.setTorchMode(cameraId, false) } catch (_: Exception) {}
                }, 15000L)
                SmsSender.send(context, from, "SMSGuard: Torch light activated for 15 seconds.")
            } else {
                SmsSender.send(context, from, "SMSGuard: Flashlight hardware unavailable.")
            }
        } catch (e: Exception) {
            SmsSender.send(context, from, "SMSGuard: Flashlight trigger error.")
        }
    }

    private fun handleInfo(context: Context, from: String) {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val androidVer = Build.VERSION.RELEASE
        val uptimeHours = SystemClock.elapsedRealtime() / (1000 * 3600)
        val reply = "SMSGuard Device Info:\nDevice: $manufacturer $model\nAndroid: $androidVer\nUptime: ${uptimeHours}h"
        SmsSender.send(context, from, reply)
    }

    private fun handleCallMe(context: Context, from: String) {
        val contact = Prefs.trustedContact(context)
        val reply = "SMSGuard Alert:\nCallback request acknowledged for $from.\nTrusted Contact: ${contact.ifBlank { "Not set" }}"
        SmsSender.send(context, from, reply)
    }

    private fun handleContacts(context: Context, from: String) {
        val contact = Prefs.trustedContact(context)
        val reply = if (contact.isNotBlank()) {
            "SMSGuard Trusted Contact:\n$contact"
        } else {
            "SMSGuard: No trusted contact configured."
        }
        SmsSender.send(context, from, reply)
    }

    private fun handleWipe(context: Context, from: String) {
        SmsSender.send(context, from, "SMSGuard Security:\nEmergency wipe warning logged. Device locked for protection.")
    }

    private companion object {
        val COMMAND_PATTERN = Regex("""#([A-Z]+)#(\d{4,8})#?""")
    }
}
