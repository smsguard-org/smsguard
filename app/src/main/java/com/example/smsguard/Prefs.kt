package com.example.smsguard

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences-backed persistence for all app settings, counters, and history. */
object Prefs {
    private const val NAME = "smsguard_prefs"

    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_SMS_ENABLED = "sms_enabled"
    private const val KEY_PIN = "pin"
    private const val KEY_TRUSTED_CONTACT = "trusted_contact"
    private const val KEY_BEACON_ENABLED = "beacon_enabled"
    private const val KEY_BATTERY_THRESHOLD = "battery_threshold"
    private const val KEY_BEACON_SENT_LEVEL = "beacon_sent_level"
    private const val KEY_COMMAND_COUNT = "command_count"
    private const val KEY_ALERT_COUNT = "alert_count"
    private const val KEY_LAST_COMMAND_TIME = "last_command_time"
    private const val KEY_LAST_COMMAND_NAME = "last_command_name"
    private const val KEY_COMMAND_HISTORY = "command_history"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_SERVICE_START_TIME = "service_start_time"
    private const val KEY_THEME_MODE = "theme_mode"

    const val DEFAULT_PIN = "1234"
    const val DEFAULT_BATTERY_THRESHOLD = 5

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun smsEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_SMS_ENABLED, true)

    fun setSmsEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_SMS_ENABLED, enabled).apply()
    }

    fun onboarded(context: Context): Boolean = sp(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    fun pin(context: Context): String = sp(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, pin: String) {
        sp(context).edit().putString(KEY_PIN, pin).apply()
    }

    fun trustedContact(context: Context): String =
        sp(context).getString(KEY_TRUSTED_CONTACT, "").orEmpty()

    fun setTrustedContact(context: Context, contact: String) {
        sp(context).edit().putString(KEY_TRUSTED_CONTACT, contact.trim()).apply()
    }

    fun batteryBeaconEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_BEACON_ENABLED, true)

    fun setBatteryBeaconEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_BEACON_ENABLED, enabled).apply()
    }

    fun batteryThreshold(context: Context): Int =
        sp(context).getInt(KEY_BATTERY_THRESHOLD, DEFAULT_BATTERY_THRESHOLD)

    fun setBatteryThreshold(context: Context, threshold: Int) {
        sp(context).edit().putInt(KEY_BATTERY_THRESHOLD, threshold.coerceIn(1, 20)).apply()
    }

    fun beaconSentLevel(context: Context): Int = sp(context).getInt(KEY_BEACON_SENT_LEVEL, -1)

    fun setBeaconSentLevel(context: Context, level: Int) {
        sp(context).edit().putInt(KEY_BEACON_SENT_LEVEL, level).apply()
    }

    fun commandCount(context: Context): Int = sp(context).getInt(KEY_COMMAND_COUNT, 0)

    fun alertCount(context: Context): Int = sp(context).getInt(KEY_ALERT_COUNT, 0)

    fun incrementCommandCount(context: Context, commandName: String) {
        val current = commandCount(context)
        val time = System.currentTimeMillis()
        sp(context).edit()
            .putInt(KEY_COMMAND_COUNT, current + 1)
            .putLong(KEY_LAST_COMMAND_TIME, time)
            .putString(KEY_LAST_COMMAND_NAME, commandName)
            .apply()
        
        addToHistory(context, "$commandName|$time")
    }

    private fun addToHistory(context: Context, entry: String) {
        val history = commandHistory(context).toMutableList()
        history.add(0, entry)
        val limited = history.take(50)
        sp(context).edit().putStringSet(KEY_COMMAND_HISTORY, limited.toSet()).apply()
    }

    fun commandHistory(context: Context): List<String> {
        val set = sp(context).getStringSet(KEY_COMMAND_HISTORY, emptySet()) ?: emptySet()
        return set.toList().sortedByDescending { it.split("|").lastOrNull()?.toLongOrNull() ?: 0L }
    }

    fun incrementAlertCount(context: Context) {
        val current = alertCount(context)
        sp(context).edit().putInt(KEY_ALERT_COUNT, current + 1).apply()
    }

    fun lastCommandTime(context: Context): Long = sp(context).getLong(KEY_LAST_COMMAND_TIME, 0L)

    fun lastCommandName(context: Context): String = sp(context).getString(KEY_LAST_COMMAND_NAME, "None") ?: "None"

    fun serviceEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_SERVICE_ENABLED, true)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        val current = serviceEnabled(context)
        val startTime = serviceStartTime(context)
        
        // If already in the target state AND we have a valid start time (if enabling), skip.
        if (current == enabled && (!enabled || startTime > 0)) return
        
        val editor = sp(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled)
        if (enabled) {
            editor.putLong(KEY_SERVICE_START_TIME, System.currentTimeMillis())
        } else {
            editor.putLong(KEY_SERVICE_START_TIME, 0L)
        }
        editor.apply()
    }

    fun serviceStartTime(context: Context): Long = sp(context).getLong(KEY_SERVICE_START_TIME, 0L)

    fun isCommandEnabled(context: Context, commandKey: String): Boolean =
        sp(context).getBoolean("cmd_enabled_$commandKey", false)

    fun setCommandEnabled(context: Context, commandKey: String, enabled: Boolean) {
        sp(context).edit().putBoolean("cmd_enabled_$commandKey", enabled).apply()
    }

    fun setAllCommandsEnabled(context: Context, commandKeys: List<String>, enabled: Boolean) {
        val editor = sp(context).edit()
        for (key in commandKeys) {
            editor.putBoolean("cmd_enabled_$key", enabled)
        }
        editor.apply()
    }

    fun themeMode(context: Context): Int = sp(context).getInt(KEY_THEME_MODE, 0) // 0: System, 1: Light, 2: Dark

    fun setThemeMode(context: Context, mode: Int) {
        sp(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }
}