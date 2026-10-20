package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pairing only works when the QR token is extracted exactly, so these cases are locked in. */
class PairingQrTest {

    @Test
    fun parse_httpLinkWithPort() {
        val qr = PairingQrParser.parse("http://192.168.1.20:3000/link/abc-123")
        assertEquals("abc-123", qr?.token)
        assertEquals("http://192.168.1.20:3000", qr?.origin)
    }

    @Test
    fun parse_localhostLinkKeepsLoopbackOriginSoItCanBeUsedDirectly() {
        val qr = PairingQrParser.parse("http://localhost:3000/link/token-1")
        assertEquals("token-1", qr?.token)
        assertEquals("http://localhost:3000", qr?.origin)
        assertEquals("http://localhost:3000", PairingEndpoint.normalizeBaseUrl(qr?.origin))
    }

    @Test
    fun parse_linkIgnoresQueryAndFragment() {
        val qr = PairingQrParser.parse("https://guard.example.com/link/tok_9?source=qr#top")
        assertEquals("tok_9", qr?.token)
        assertEquals("https://guard.example.com", qr?.origin)
    }

    @Test
    fun parse_linkToleratesTrailingSlash() {
        assertEquals("tok", PairingQrParser.parse("http://10.0.2.2:3000/link/tok/")?.token)
    }

    @Test
    fun parse_deepLinkToken() {
        val qr = PairingQrParser.parse("smsguard://pair?token=abc-123")
        assertEquals("abc-123", qr?.token)
        assertNull(qr?.origin)
    }

    @Test
    fun parse_deepLinkTokenAmongOtherParams() {
        val qr = PairingQrParser.parse("smsguard://pair?source=dashboard&token=token%2Fwith%2Fslash")
        assertEquals("token/with/slash", qr?.token)
    }

    @Test
    fun parse_deepLinkIsCaseInsensitive() {
        assertEquals("abc", PairingQrParser.parse("SMSGUARD://PAIR?TOKEN=abc")?.token)
    }

    @Test
    fun parse_rejectsUnrelatedContent() {
        assertNull(PairingQrParser.parse("https://example.com/other/abc"))
        assertNull(PairingQrParser.parse("just some text"))
        assertNull(PairingQrParser.parse(""))
        assertNull(PairingQrParser.parse(null))
    }

    @Test
    fun parse_rejectsMissingToken() {
        assertNull(PairingQrParser.parse("http://10.0.2.2:3000/link/"))
        assertNull(PairingQrParser.parse("smsguard://pair?token="))
        assertNull(PairingQrParser.parse("smsguard://pair?other=1"))
    }

    @Test
    fun parse_rejectsTokenWithWhitespace() {
        assertNull(PairingQrParser.parse("smsguard://pair?token=abc%20def"))
    }

    @Test
    fun normalizeBaseUrl_allowsEmulatorAlias() {
        assertEquals(
            "http://10.0.2.2:3000",
            PairingEndpoint.normalizeBaseUrl("  http://10.0.2.2:3000/  ")
        )
    }

    @Test
    fun normalizeBaseUrl_preservesPathPrefix() {
        assertEquals(
            "https://guard.example.com/dashboard",
            PairingEndpoint.normalizeBaseUrl("https://guard.example.com/dashboard/")
        )
    }

    @Test
    fun normalizeBaseUrl_acceptsLoopbackAndRejectsBadSchemes() {
        assertEquals(
            "http://localhost:3000",
            PairingEndpoint.normalizeBaseUrl("http://localhost:3000")
        )
        assertEquals(
            "http://127.0.0.1:3000",
            PairingEndpoint.normalizeBaseUrl("http://127.0.0.1:3000")
        )
        assertEquals(
            "http://[::1]:3000",
            PairingEndpoint.normalizeBaseUrl("http://[::1]:3000")
        )
        assertNull(PairingEndpoint.normalizeBaseUrl("ftp://10.0.2.2:3000"))
        assertNull(PairingEndpoint.normalizeBaseUrl(""))
    }

    @Test
    fun normalizeBaseUrl_rejectsQueryStrings() {
        assertNull(PairingEndpoint.normalizeBaseUrl("http://10.0.2.2:3000?debug=1"))
    }

    @Test
    fun endpointUrls_encodeTheToken() {
        val base = "http://10.0.2.2:3000"
        assertEquals(
            "http://10.0.2.2:3000/api/devices/pairings/token%2Fwith%2Fslash",
            PairingEndpoint.pairingUrl(base, "token/with/slash")
        )
        assertEquals(
            "http://10.0.2.2:3000/api/devices/pairings/abc/claim",
            PairingEndpoint.claimUrl(base, "abc")
        )
    }

    @Test
    fun imeiValidation_acceptsFourteenToSeventeenDigits() {
        assertTrue(PairingDeviceInfo.isValidImei("12345678901234"))
        assertTrue(PairingDeviceInfo.isValidImei("123456789012345"))
        assertTrue(PairingDeviceInfo.isValidImei("1234567890123456"))
        assertTrue(PairingDeviceInfo.isValidImei("12345678901234567"))
    }

    @Test
    fun imeiValidation_rejectsShortLongAndNonDigits() {
        assertFalse(PairingDeviceInfo.isValidImei(null))
        assertFalse(PairingDeviceInfo.isValidImei(""))
        assertFalse(PairingDeviceInfo.isValidImei("1234567890123"))
        assertFalse(PairingDeviceInfo.isValidImei("123456789012345678"))
        assertFalse(PairingDeviceInfo.isValidImei("12345abc901234"))
    }
}
