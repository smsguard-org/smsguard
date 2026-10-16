package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trust decisions run on every incoming SMS, so the comparison has to tolerate the formatting
 * differences between what a user saved and what the carrier delivers.
 */
class PhoneNumbersTest {

    @Test
    fun normalize_stripsNonDigits() {
        assertEquals("15550109999", PhoneNumbers.normalize("+1 (555) 010-9999"))
    }

    @Test
    fun normalize_nullBecomesEmpty() {
        assertEquals("", PhoneNumbers.normalize(null))
    }

    @Test
    fun matches_exactDigits() {
        assertTrue(PhoneNumbers.matches("15550109999", "15550109999"))
    }

    @Test
    fun matches_ignoresFormatting() {
        assertTrue(PhoneNumbers.matches("+1 (555) 010-9999", "15550109999"))
    }

    /** A saved local number must still authorize the same sender arriving with a country code. */
    @Test
    fun matches_countryCodePrefix() {
        assertTrue(PhoneNumbers.matches("5550109999", "15550109999"))
        assertTrue(PhoneNumbers.matches("15550109999", "5550109999"))
    }

    @Test
    fun matches_differentNumbersAreRejected() {
        assertFalse(PhoneNumbers.matches("15550109999", "15550101111"))
    }

    /** Short codes and short numbers must not slip through the suffix rule. */
    @Test
    fun matches_shortSuffixIsRejected() {
        assertFalse(PhoneNumbers.matches("9999", "15550109999"))
    }

    @Test
    fun matches_blankIsNeverATrustedMatch() {
        assertFalse(PhoneNumbers.matches("", "15550109999"))
        assertFalse(PhoneNumbers.matches(null, "15550109999"))
        assertFalse(PhoneNumbers.matches("", ""))
    }
}
