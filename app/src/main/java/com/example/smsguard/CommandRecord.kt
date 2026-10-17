package com.example.smsguard

/**
 * One executed command from the on-device history: what ran, when, and who sent it.
 *
 * Stored as a flat `command|timestamp|sender` string. Entries written before the sender was
 * recorded only have two fields, so parsing must tolerate a missing third field.
 */
data class CommandRecord(
    val command: String,
    val timestamp: Long,
    val sender: String
) {
    companion object {
        fun parse(entry: String): CommandRecord {
            val parts = entry.split("|")
            return CommandRecord(
                command = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Unknown",
                timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                sender = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "Unknown sender"
            )
        }
    }
}
