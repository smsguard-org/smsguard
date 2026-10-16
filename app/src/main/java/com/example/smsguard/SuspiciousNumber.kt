package com.example.smsguard

/**
 * One untrusted sender that texted a command, together with the most recent attempt.
 *
 * Repeated attempts from the same number are folded into a single row with a rising [count] so a
 * spammer cannot flood the review list.
 */
data class SuspiciousNumber(
    val phoneNumber: String,
    val command: String,
    val timestamp: Long,
    val count: Int = 1
) {
    companion object {
        /** Guard rail so a hostile sender cannot grow the persisted log without bound. */
        const val MAX_ENTRIES = 100

        private const val SEPARATOR = "|"

        fun encode(entry: SuspiciousNumber): String = listOf(
            entry.phoneNumber,
            entry.timestamp.toString(),
            entry.command,
            entry.count.toString()
        ).joinToString(SEPARATOR)

        fun decode(raw: String): SuspiciousNumber? {
            val parts = raw.split(SEPARATOR)
            if (parts.size < 4) return null
            val number = parts[0]
            if (number.isBlank()) return null
            val timestamp = parts[1].toLongOrNull() ?: return null
            val count = parts[3].toIntOrNull() ?: 1
            return SuspiciousNumber(number, parts[2], timestamp, count)
        }

        /**
         * Adds [phoneNumber] to [existing], bumping the counter when that sender is already there,
         * then returns the list newest-first and capped at [MAX_ENTRIES].
         */
        fun merge(
            existing: List<SuspiciousNumber>,
            phoneNumber: String,
            command: String,
            timestamp: Long
        ): List<SuspiciousNumber> {
            val existingMatch = existing.firstOrNull {
                PhoneNumbers.matches(it.phoneNumber, phoneNumber)
            }
            val updated = if (existingMatch != null) {
                existing.map { entry ->
                    if (entry === existingMatch) {
                        entry.copy(command = command, timestamp = timestamp, count = entry.count + 1)
                    } else {
                        entry
                    }
                }
            } else {
                existing + SuspiciousNumber(phoneNumber, command, timestamp)
            }
            return updated.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
        }
    }
}
