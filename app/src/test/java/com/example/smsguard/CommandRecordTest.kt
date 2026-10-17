package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * History entries gained a sender field, but older two-field entries can still be on a device
 * after an update, so parsing must handle both without losing the command or timestamp.
 */
class CommandRecordTest {

    @Test
    fun parse_readsCommandTimeAndSender() {
        val record = CommandRecord.parse("LOCATE|1700000000000|Eyosafet")
        assertEquals("LOCATE", record.command)
        assertEquals(1_700_000_000_000L, record.timestamp)
        assertEquals("Eyosafet", record.sender)
    }

    @Test
    fun parse_legacyEntryWithoutSender_usesPlaceholder() {
        val record = CommandRecord.parse("ALARM|1700000000000")
        assertEquals("ALARM", record.command)
        assertEquals(1_700_000_000_000L, record.timestamp)
        assertEquals("Unknown sender", record.sender)
    }

    @Test
    fun parse_numberSender_isPreserved() {
        val record = CommandRecord.parse("STATUS|1700000000000|+251 91 206 3835")
        assertEquals("+251 91 206 3835", record.sender)
    }

    @Test
    fun parse_malformed_fallsBackGracefully() {
        val record = CommandRecord.parse("garbage")
        assertEquals("garbage", record.command)
        assertEquals(0L, record.timestamp)
        assertEquals("Unknown sender", record.sender)
    }
}
