package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The suspicious log is persisted as flat strings, so encoding and the repeat-attempt merge have
 * to round-trip exactly and never grow into duplicate rows for the same sender.
 */
class SuspiciousNumberTest {

    @Test
    fun encodeDecode_roundTrips() {
        val entry = SuspiciousNumber("+15550109999", "LOCATE", 1_700_000_000_000L, 3)
        assertEquals(entry, SuspiciousNumber.decode(SuspiciousNumber.encode(entry)))
    }

    @Test
    fun decode_rejectsMalformedInput() {
        assertNull(SuspiciousNumber.decode("garbage"))
        assertNull(SuspiciousNumber.decode("15550109999|not-a-time|LOCATE|1"))
    }

    @Test
    fun merge_addsNewSender() {
        val merged = SuspiciousNumber.merge(emptyList(), "15550109999", "ALARM", 100L)
        assertEquals(1, merged.size)
        assertEquals("ALARM", merged[0].command)
        assertEquals(100L, merged[0].timestamp)
        assertEquals(1, merged[0].count)
    }

    @Test
    fun merge_sameSenderBumpsCountInsteadOfAppending() {
        val first = SuspiciousNumber("15550109999", "ALARM", 100L, 1)
        val merged = SuspiciousNumber.merge(listOf(first), "+1 (555) 010-9999", "LOCATE", 200L)

        assertEquals(1, merged.size)
        assertEquals(2, merged[0].count)
        assertEquals("LOCATE", merged[0].command)
        assertEquals(200L, merged[0].timestamp)
    }

    @Test
    fun merge_keepsNewestFirst() {
        val existing = SuspiciousNumber("1111111111", "INFO", 500L)
        val merged = SuspiciousNumber.merge(listOf(existing), "2222222222", "WIFI", 900L)

        assertEquals(listOf("2222222222", "1111111111"), merged.map { it.phoneNumber })
    }

    @Test
    fun merge_capsTheLogSize() {
        var entries = emptyList<SuspiciousNumber>()
        repeat(SuspiciousNumber.MAX_ENTRIES + 20) { index ->
            entries = SuspiciousNumber.merge(entries, "1555010%04d".format(index), "INFO", index.toLong())
        }
        assertEquals(SuspiciousNumber.MAX_ENTRIES, entries.size)
    }
}
