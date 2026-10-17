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
import java.util.Locale

/** High-priority receiver that runs PIN-authenticated commands sent from trusted contacts. */
class SmsCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.serviceEnabled(context)) return
        if (!Prefs.smsEnabled(context)) return

        val pin = Prefs.pin(context)
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (message in messages) {
            val from = message.originatingAddress ?: continue
            val body = (message.messageBody ?: "").trim().uppercase(Locale.US)
            val match = COMMAND_PATTERN.find(body) ?: continue
            val command = match.groupValues[1]
            val commandPin = match.groupValues[2]

            // Only numbers saved as trusted contacts are allowed to trigger anything. Anyone
            // else is logged for the user to review and then ignored without a reply.
            if (!Prefs.isTrustedNumber(context, from)) {
                Prefs.recordSuspiciousCommand(context, from, command)
                continue
            }

            if (commandPin != pin) continue

            if (!Prefs.isCommandEnabled(context, command)) {
                SmsSender.send(
                    context,
                    from,
                    "SMSGuard: Command #$command is disabled on this device."
                )
                continue
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
                SmsGuardCommand.STATUS -> handleStatus(context, from)
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
        SirenController.start(context)
        SmsSender.send(context, from, "SMSGuard: Alarm started.")
    }

    private fun handleStop(context: Context, from: String) {
        SirenController.stop(context)
        SmsSender.send(context, from, "SMSGuard: Alarm stopped.")
    }

    private fun handleLock(context: Context, from: String) {
        val locked = lockDevice(context)
        val reply = if (locked) {
            "SMSGuard Security:\nDevice screen lock triggered and active session secured."
        } else {
            "SMSGuard: Screen lock is unavailable. Open SMSGuard and enable the Screen Lock Accessibility Service."
        }
        SmsSender.send(context, from, reply)
    }

    /** Locks the screen through the opt-in accessibility service; false if it is unavailable. */
    private fun lockDevice(context: Context): Boolean =
        AccessibilityPermission.isScreenLockEnabled(context) &&
            ScreenLockAccessibilityService.lockDevice()

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
        Prefs.incrementAlertCount(context)
        val locked = lockDevice(context)
        val reply = if (locked) {
            "SMSGuard Security:\nEmergency wipe alert logged. Device locked for protection."
        } else {
            "SMSGuard Security:\nEmergency wipe alert logged, but the device could not be locked. Open SMSGuard and enable the Screen Lock Accessibility Service."
        }
        SmsSender.send(context, from, reply)
    }

    private fun handleStatus(context: Context, from: String) {
        SmsSender.send(context, from, DeviceStatus.summary(context))
    }

    private companion object {
        val COMMAND_PATTERN = Regex("""#([A-Z]+)#(\d{4,8})#?""")
    }
}
