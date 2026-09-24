package com.example.smsguard

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

data class TrustedContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val isPrimary: Boolean = false
)

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

    private const val KEY_CONTACTS_SET = "trusted_contacts_set"

    fun trustedContact(context: Context): String =
        sp(context).getString(KEY_TRUSTED_CONTACT, "").orEmpty()

    fun setTrustedContact(context: Context, contact: String) {
        sp(context).edit().putString(KEY_TRUSTED_CONTACT, contact.trim()).apply()
    }

    fun trustedContactsList(context: Context): List<TrustedContact> {
        val set = sp(context).getStringSet(KEY_CONTACTS_SET, emptySet()) ?: emptySet()
        val list = set.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 3) {
                TrustedContact(
                    id = parts[0],
                    name = parts[1],
                    phoneNumber = parts[2],
                    isPrimary = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                )
            } else null
        }
        if (list.isEmpty()) {
            val single = trustedContact(context)
            if (single.isNotBlank()) {
                val legacy = TrustedContact(name = "Emergency Contact", phoneNumber = single, isPrimary = true)
                return listOf(legacy)
            }
        }
        return list.sortedByDescending { it.isPrimary }
    }

    fun saveTrustedContacts(context: Context, contacts: List<TrustedContact>) {
        val set = contacts.map { "${it.id}|${it.name}|${it.phoneNumber}|${it.isPrimary}" }.toSet()
        sp(context).edit().putStringSet(KEY_CONTACTS_SET, set).apply()
        
        val primary = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
        if (primary != null) {
            setTrustedContact(context, primary.phoneNumber)
        }
    }

    fun addTrustedContact(context: Context, name: String, phoneNumber: String, isPrimary: Boolean = false) {
        val current = trustedContactsList(context).toMutableList()
        val shouldBePrimary = isPrimary || current.isEmpty()
        if (shouldBePrimary) {
            for (i in current.indices) {
                current[i] = current[i].copy(isPrimary = false)
            }
        }
        current.add(TrustedContact(name = name, phoneNumber = phoneNumber, isPrimary = shouldBePrimary))
        saveTrustedContacts(context, current)
    }

    fun removeTrustedContact(context: Context, contactId: String) {
        val current = trustedContactsList(context).filter { it.id != contactId }.toMutableList()
        if (current.isNotEmpty() && current.none { it.isPrimary }) {
            current[0] = current[0].copy(isPrimary = true)
        }
        saveTrustedContacts(context, current)
    }

    fun setPrimaryContact(context: Context, contactId: String) {
        val current = trustedContactsList(context).map { contact ->
            contact.copy(isPrimary = contact.id == contactId)
        }
        saveTrustedContacts(context, current)
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