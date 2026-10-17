package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA coverage for the `#CALLME#PIN` command: that the body is recognized by the parser, that the
 * command is registered, and that the acknowledgement reply is well formed.
 */
class CallMeCommandTest {

    @Test
    fun parse_recognizesCallMeCommand() {
        val match = SmsGuardCommand.parse("#CALLME#1234")
        assertNotNull(match)
        assertEquals("CALLME", match!!.command)
        assertEquals("1234", match.pin)
    }

    @Test
    fun parse_findsCommandInsideLongerMessage() {
        val match = SmsGuardCommand.parse("PLEASE #CALLME#4321 NOW")
        assertEquals("CALLME", match?.command)
        assertEquals("4321", match?.pin)
    }

    @Test
    fun parse_allowsOptionalTrailingHash() {
        assertEquals("CALLME", SmsGuardCommand.parse("#CALLME#1234#")?.command)
    }

    @Test
    fun parse_rejectsPinOutsideFourToEightDigits() {
        assertNull(SmsGuardCommand.parse("#CALLME#123"))
        assertNull(SmsGuardCommand.parse("#CALLME#123456789"))
    }

    @Test
    fun parse_rejectsBodyWithNoCommand() {
        assertNull(SmsGuardCommand.parse("HELLO THERE"))
    }

    @Test
    fun callMe_isARegisteredCommand() {
        assertTrue(SmsGuardCommand.ALL_COMMANDS.contains(SmsGuardCommand.CALLME))
    }

    @Test
    fun callMe_replyAcknowledgesSenderAndListsTrustedContact() {
        val reply = CommandReplies.callMe("+15550101111", "+15550109999")
        assertEquals(
            "SMSGuard Alert:\n" +
                "Callback request acknowledged for +15550101111.\n" +
                "Trusted Contact: +15550109999",
            reply
        )
    }

    @Test
    fun callMe_replyReportsMissingContactAsNotSet() {
        assertTrue(CommandReplies.callMe("+15550101111", "").contains("Trusted Contact: Not set"))
        assertTrue(CommandReplies.callMe("+15550101111", "   ").contains("Trusted Contact: Not set"))
    }
}
