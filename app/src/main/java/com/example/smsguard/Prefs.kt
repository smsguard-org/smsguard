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
    private const val KEY_SIREN_ACTIVE = "siren_active"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
    private const val KEY_SUSPICIOUS_NUMBERS = "suspicious_numbers"
    private const val KEY_SIM_SNAPSHOT = "sim_snapshot"
    private const val KEY_SIM_ALERT_FIX = "sim_alert_false_positive_fix"
    private const val KEY_PAIRING_BASE_URL = "pairing_base_url"
    private const val KEY_PAIRED_DEVICE_NAME = "paired_device_name"

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

    /**
     * True once a runtime permission request has actually been made.
     *
     * `shouldShowRequestPermissionRationale` returns false both before the first request and
     * after a permanent denial, so it cannot distinguish "not asked yet" from "will never be
     * asked again" on its own.
     */
    fun hasRequestedPermissions(context: Context): Boolean =
        sp(context).getBoolean(KEY_PERMISSIONS_REQUESTED, false)

    fun setHasRequestedPermissions(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_PERMISSIONS_REQUESTED, value).apply()
    }

    fun pin(context: Context): String = sp(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    /** Stores the PIN as a salted SHA-256 hash; the plaintext is never kept. */
    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        sp(context).edit()
            .putString(KEY_PIN_HASH, sha256(pin.toByteArray() + salt))
            .putString(KEY_PIN_SALT, bytesToHex(salt))
            .remove(KEY_PIN)
            .apply()
    }

    /**
     * True when [input] matches the configured PIN. Falls back to comparing the legacy
     * plaintext value (or the default) so existing installs keep working until the user
     * changes their PIN, at which point it is migrated to the hashed form.
     */
    fun verifyPin(context: Context, input: String): Boolean {
        val prefs = sp(context)
        val hash = prefs.getString(KEY_PIN_HASH, null)
        val saltHex = prefs.getString(KEY_PIN_SALT, null)
        if (hash == null || saltHex == null) return pin(context) == input
        return sha256(input.toByteArray() + hexToBytes(saltHex)) == hash
    }

    private fun sha256(value: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(value)
            .joinToString("") { "%02x".format(it) }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

     private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // --- Security state: PIN brute-force lockout, replay protection, audit log ------------

    private const val KEY_AUTH_BLOCK = "auth_block_"
    private const val KEY_AUTH_FAIL = "auth_fail_"
    private const val KEY_AUTH_FAIL_COUNT = "auth_fail_count_"
    private const val KEY_REPLAY_SEEN = "replay_seen"
    private const val KEY_SECURITY_LOG = "security_log"
    private const val MAX_AUTH_FAILURES = 5
    private const val LOCKOUT_WINDOW_MS = 10 * 60 * 1000L
    private const val REPLAY_WINDOW_MS = 5 * 60 * 1000L

    /** True while [sender] is locked out after too many wrong PINs. */
    fun isSenderBlocked(context: Context, sender: String): Boolean =
        sp(context).getLong(KEY_AUTH_BLOCK + sender, 0L) > System.currentTimeMillis()

    /** Records one failed PIN attempt; true when this attempt started a 10-minute lockout. */
    fun recordAuthFailure(context: Context, sender: String): Boolean {
        val prefs = sp(context)
        val now = System.currentTimeMillis()
        val lastFailure = prefs.getLong(KEY_AUTH_FAIL + sender, 0L)
        val failures = if (now - lastFailure > LOCKOUT_WINDOW_MS) {
            1L
        } else {
            prefs.getLong(KEY_AUTH_FAIL_COUNT + sender, 0L) + 1
        }
        val editor = prefs.edit()
            .putLong(KEY_AUTH_FAIL + sender, now)
            .putLong(KEY_AUTH_FAIL_COUNT + sender, failures)
        if (failures >= MAX_AUTH_FAILURES) {
            editor.putLong(KEY_AUTH_BLOCK + sender, now + LOCKOUT_WINDOW_MS)
            editor.remove(KEY_AUTH_FAIL_COUNT + sender)
        }
        editor.apply()
        return failures >= MAX_AUTH_FAILURES
    }

    fun clearAuthFailures(context: Context, sender: String) {
        sp(context).edit()
            .remove(KEY_AUTH_FAIL_COUNT + sender)
            .remove(KEY_AUTH_FAIL + sender)
            .apply()
    }

    /** True when the identical SMS from [sender] already ran within the replay window. */
    fun isReplay(context: Context, sender: String, body: String): Boolean {
        val prefs = sp(context)
        val now = System.currentTimeMillis()
        val digest = sha256(body.toByteArray())
        val seen = prefs.getStringSet(KEY_REPLAY_SEEN, emptySet()) ?: emptySet()
        val fresh = seen.filter { entry ->
            (now - (entry.substringAfterLast('|').toLongOrNull() ?: 0L)) <= REPLAY_WINDOW_MS
        }
        val hit = fresh.any { it == "$sender|$digest" }
        if (!hit) {
            prefs.edit().putStringSet(KEY_REPLAY_SEEN, fresh.toSet() + "$sender|$digest|$now").apply()
        }
        return hit
    }

    /** Appends [message] to the in-app security log (kept to the most recent 50 entries). */
    fun logSecurityEvent(context: Context, message: String) {
        val prefs = sp(context)
        val lines = (prefs.getString(KEY_SECURITY_LOG, "") ?: "")
            .split('\n').filter { it.isNotBlank() }.toMutableList()
        lines += "${System.currentTimeMillis()}|$message"
        prefs.edit().putString(KEY_SECURITY_LOG, lines.takeLast(50).joinToString("\n")).apply()
    }

    fun securityLog(context: Context): List<String> =
        (sp(context).getString(KEY_SECURITY_LOG, "") ?: "")
            .split('\n').filter { it.isNotBlank() }
            .sortedBy { it.substringBefore('|').toLongOrNull() ?: 0L }
            .map { it.substringAfter('|') }

    private const val KEY_CONTACTS_SET = "trusted_contacts_set"

    /** Stable id so the legacy single-contact fallback can be removed like any other contact. */
    private const val LEGACY_CONTACT_ID = "legacy"

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
                val legacy = TrustedContact(
                    id = LEGACY_CONTACT_ID,
                    name = "Emergency Contact",
                    phoneNumber = single,
                    isPrimary = true
                )
                return listOf(legacy)
            }
        }
        return list.sortedByDescending { it.isPrimary }
    }

    fun saveTrustedContacts(context: Context, contacts: List<TrustedContact>) {
        val set = contacts.map { "${it.id}|${it.name}|${it.phoneNumber}|${it.isPrimary}" }.toSet()
        val editor = sp(context).edit().putStringSet(KEY_CONTACTS_SET, set)

        // Keep the legacy single-contact key in sync. Clearing it when the list is emptied is
        // important: otherwise the old number would keep authorizing commands through the
        // legacy fallback in [trustedContactsList].
        val primary = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
        if (primary != null) {
            editor.putString(KEY_TRUSTED_CONTACT, primary.phoneNumber)
        } else {
            editor.remove(KEY_TRUSTED_CONTACT)
        }
        editor.apply()
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

    /**
     * Whether [phoneNumber] may trigger a command.
     *
     * Matching is format-tolerant (see [PhoneNumbers]) so a contact saved as "+1 555…" still
     * authorizes the same sender arriving as "1555…".
     */
    fun isTrustedNumber(context: Context, phoneNumber: String): Boolean =
        trustedContactsList(context).any { PhoneNumbers.matches(it.phoneNumber, phoneNumber) }

    // --- Suspicious (untrusted) senders -----------------------------------------------------

    /** Untrusted numbers that recently texted a command, newest first. */
    fun suspiciousNumbers(context: Context): List<SuspiciousNumber> {
        val set = sp(context).getStringSet(KEY_SUSPICIOUS_NUMBERS, emptySet()) ?: emptySet()
        return set.mapNotNull { SuspiciousNumber.decode(it) }
            .sortedByDescending { it.timestamp }
    }

    fun suspiciousCount(context: Context): Int = suspiciousNumbers(context).sumOf { it.count }

    /**
     * Records a command attempt from an untrusted sender. Repeated attempts from the same number
     * update one row instead of appending duplicates.
     */
    fun recordSuspiciousCommand(
        context: Context,
        phoneNumber: String,
        command: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val merged = SuspiciousNumber.merge(
            existing = suspiciousNumbers(context),
            phoneNumber = phoneNumber,
            command = command,
            timestamp = timestamp
        )
        sp(context).edit()
            .putStringSet(KEY_SUSPICIOUS_NUMBERS, merged.map { SuspiciousNumber.encode(it) }.toSet())
            .apply()
    }

    fun removeSuspiciousNumber(context: Context, phoneNumber: String) {
        val remaining = suspiciousNumbers(context)
            .filterNot { PhoneNumbers.matches(it.phoneNumber, phoneNumber) }
        sp(context).edit()
            .putStringSet(KEY_SUSPICIOUS_NUMBERS, remaining.map { SuspiciousNumber.encode(it) }.toSet())
            .apply()
    }

    fun clearSuspiciousNumbers(context: Context) {
        sp(context).edit().remove(KEY_SUSPICIOUS_NUMBERS).apply()
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

    fun incrementCommandCount(context: Context, commandName: String, sender: String = "") {
        val current = commandCount(context)
        val time = System.currentTimeMillis()
        val safeSender = sender.replace("|", " ").trim()
        sp(context).edit()
            .putInt(KEY_COMMAND_COUNT, current + 1)
            .putLong(KEY_LAST_COMMAND_TIME, time)
            .putString(KEY_LAST_COMMAND_NAME, commandName)
            .apply()

        addToHistory(context, "$commandName|$time|$safeSender")
    }

    private fun addToHistory(context: Context, entry: String) {
        val history = commandHistory(context).toMutableList()
        history.add(0, entry)
        val limited = history.take(50)
        sp(context).edit().putStringSet(KEY_COMMAND_HISTORY, limited.toSet()).apply()
    }

    fun commandHistory(context: Context): List<String> {
        val set = sp(context).getStringSet(KEY_COMMAND_HISTORY, emptySet()) ?: emptySet()
        return set.toList().sortedByDescending { CommandRecord.parse(it).timestamp }
    }

    fun incrementAlertCount(context: Context) {
        val current = alertCount(context)
        sp(context).edit().putInt(KEY_ALERT_COUNT, current + 1).apply()
    }

    /**
     * One-time cleanup for the alert counter that a SIM-monitor bug inflated by treating a
     * transient empty subscription read as a brand-new SIM. Resets the counter so it reflects
     * only alerts raised after the fix, then never runs again.
     */
    fun applySimAlertCountFix(context: Context) {
        val prefs = sp(context)
        if (prefs.getBoolean(KEY_SIM_ALERT_FIX, false)) return
        prefs.edit()
            .putInt(KEY_ALERT_COUNT, 0)
            .putBoolean(KEY_SIM_ALERT_FIX, true)
            .apply()
    }

    fun lastCommandTime(context: Context): Long = sp(context).getLong(KEY_LAST_COMMAND_TIME, 0L)

    fun lastCommandName(context: Context): String = sp(context).getString(KEY_LAST_COMMAND_NAME, "None") ?: "None"

    // Protection stays off until the user explicitly turns it on (or finishes onboarding), so a
    // fresh install no longer appears to be running protection the user never asked for.
    fun serviceEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_SERVICE_ENABLED, false)

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

    /**
     * True while [SirenService] is sounding, so the UI can offer an in-app "stop" instead of
     * waiting for the user to text the #STOP#PIN command back to the phone.
     */
    fun sirenActive(context: Context): Boolean = sp(context).getBoolean(KEY_SIREN_ACTIVE, false)

    fun setSirenActive(context: Context, active: Boolean) {
        sp(context).edit().putBoolean(KEY_SIREN_ACTIVE, active).apply()
    }

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

    /**
     * Keeps the `#LOCATE#PIN` command aligned with the system location switch.
     *
     * When location is off the command cannot work, so it is forced back to disabled on launch
     * instead of staying on from a previous session. Returns true when location is available.
     */
    fun syncLocateCommandWithLocation(context: Context): Boolean {
        val locationEnabled = LocationHelper.isLocationEnabled(context)
        if (!locationEnabled && isCommandEnabled(context, SmsGuardCommand.LOCATE)) {
            setCommandEnabled(context, SmsGuardCommand.LOCATE, false)
        }
        return locationEnabled
    }

    fun themeMode(context: Context): Int = sp(context).getInt(KEY_THEME_MODE, 0) // 0: System, 1: Light, 2: Dark

    fun setThemeMode(context: Context, mode: Int) {
        sp(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    /** Last observed active-SIM state. Null means monitoring has not been initialized yet. */
    fun simSnapshot(context: Context): String? = sp(context).getString(KEY_SIM_SNAPSHOT, null)

    fun setSimSnapshot(context: Context, snapshot: String) {
        sp(context).edit().putString(KEY_SIM_SNAPSHOT, snapshot).apply()
    }

    /** Dashboard server used for QR pairing. Never localhost; defaults to the emulator host alias. */
    fun pairingBaseUrl(context: Context): String =
        sp(context).getString(KEY_PAIRING_BASE_URL, PairingEndpoint.DEFAULT_BASE_URL)
            ?: PairingEndpoint.DEFAULT_BASE_URL

    fun setPairingBaseUrl(context: Context, value: String) {
        sp(context).edit().putString(KEY_PAIRING_BASE_URL, value).apply()
    }

    fun pairedDeviceName(context: Context): String? =
        sp(context).getString(KEY_PAIRED_DEVICE_NAME, null)

    fun setPairedDeviceName(context: Context, value: String) {
        sp(context).edit().putString(KEY_PAIRED_DEVICE_NAME, value).apply()
    }
}
