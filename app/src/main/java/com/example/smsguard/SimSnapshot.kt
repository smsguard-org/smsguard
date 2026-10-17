package com.example.smsguard

/**
 * A single active SIM's local identity.
 *
 * Kept free of Android types so the change-detection logic can be unit tested. The fingerprint is
 * a one-way hash; SIM identifiers are never persisted in the clear or sent by SMS.
 */
internal data class SimSnapshot(
    val slotIndex: Int,
    val fingerprint: String,
    val description: String
) {
    fun encode(): String = listOf(slotIndex, fingerprint, description.replace("|", " ")).joinToString("~")

    companion object {
        fun decode(value: String): SimSnapshot? {
            val parts = value.split("~", limit = 3)
            return if (parts.size == 3) {
                SimSnapshot(parts[0].toIntOrNull() ?: return null, parts[1], parts[2])
            } else {
                null
            }
        }

        fun encodeAll(snapshots: List<SimSnapshot>): String =
            snapshots.joinToString("|") { it.encode() }

        fun decodeAll(encoded: String): List<SimSnapshot> =
            encoded.split('|').mapNotNull(::decode)

        /**
         * SIMs whose fingerprint differs from the last known state for their slot.
         *
         * An inserted, swapped or replaced card shows up here; a plain removal does not, because
         * the remaining slots still match. Empty [current] readings are handled by the caller and
         * never reach this comparison.
         */
        fun changed(previous: List<SimSnapshot>, current: List<SimSnapshot>): List<SimSnapshot> {
            val previousBySlot = previous.associateBy { it.slotIndex }
            return current.filter { previousBySlot[it.slotIndex]?.fingerprint != it.fingerprint }
        }
    }
}
