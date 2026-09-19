package com.example.smsguard

import android.content.Context
import android.content.SharedPreferences

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
        val limited = history.take(5)
        sp(context).edit().putStringSet(KEY_COMMAND_HISTORY, limited.toSet()).apply()
        // Note: StringSets don't guarantee order, so we might need a different approach for true history.
        // But for dummy/simple history it might suffice if we parse the time.
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
}