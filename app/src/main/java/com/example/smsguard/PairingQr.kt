package com.example.smsguard

import java.net.URI
import java.net.URLDecoder

/** A pairing token extracted from a dashboard QR code, with its origin when one is present. */
data class PairingQr(
    val token: String,
    /** `scheme://host[:port]` for HTTP(S) QR codes, so the app can reuse the dashboard address. */
    val origin: String? = null
)

/**
 * Parses the two QR formats produced by the dashboard:
 *
 *  - `http(s)://host:port/link/<token>`
 *  - `smsguard://pair?token=<token>`
 *
 * Parsing is deliberately free of Android types so it can be unit tested on the JVM. The token is
 * taken verbatim (no URL decoding) because it is opaque to the app and is sent back unchanged.
 */
object PairingQrParser {
    private const val LINK_MARKER = "/link/"
    private const val DEEP_LINK_PREFIX = "smsguard://pair"
    private const val MAX_TOKEN_LENGTH = 512

    fun parse(raw: String?): PairingQr? {
        val content = raw?.trim().orEmpty()
        if (content.isEmpty()) return null

        val linkIndex = content.indexOf(LINK_MARKER, ignoreCase = true)
        if (linkIndex >= 0) {
            val token = content
                .substring(linkIndex + LINK_MARKER.length)
                .substringBefore('?')
                .substringBefore('#')
                .trim()
                .trimEnd('/')
            return token.takeIf(::isPlausibleToken)?.let {
                PairingQr(token = it, origin = httpOrigin(content))
            }
        }

        if (content.startsWith("$DEEP_LINK_PREFIX?", ignoreCase = true)) {
            val token = content
                .substringAfter('?', "")
                .split('&')
                .mapNotNull { part ->
                    val key = part.substringBefore('=', "")
                    if (!key.equals("token", ignoreCase = true)) return@mapNotNull null
                    decode(part.substringAfter('=', ""))
                }
                .firstOrNull { it.isNotBlank() }
                ?.trim()
            return token?.takeIf(::isPlausibleToken)?.let { PairingQr(token = it) }
        }

        return null
    }

    /**
     * A token is anything non-empty without whitespace. Keeping this permissive mirrors the
     * server, which owns token generation, while rejecting obviously malformed QR contents.
     */
    fun isPlausibleToken(token: String): Boolean =
        token.isNotBlank() &&
            token.length <= MAX_TOKEN_LENGTH &&
            token.none { it.isWhitespace() }

    /** Returns `scheme://host[:port]` for an HTTP(S) URL, otherwise null. */
    fun httpOrigin(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port in 1..65535) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    private fun decode(value: String): String? =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()
}

/** Builds and validates dashboard URLs. Localhost is always rejected: it points at the phone. */
object PairingEndpoint {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"

    /**
     * Normalizes a user-entered base URL and rejects values that cannot work.
     *
     * `10.0.2.2` is the emulator's alias for the host machine and is intentionally allowed, while
     * `localhost`, loopback addresses and wildcard binds are not, because they resolve to the phone
     * itself rather than to the computer running the dashboard.
     */
    fun normalizeBaseUrl(input: String?): String? {
        val trimmed = input?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        if (isLoopbackHost(host)) return null
        if (uri.rawQuery != null || uri.fragment != null) return null

        val port = if (uri.port in 1..65535) ":${uri.port}" else ""
        val path = uri.path.orEmpty().trimEnd('/')
        return "$scheme://$host$port$path"
    }

    fun isLoopbackHost(host: String): Boolean {
        val normalized = host.lowercase().removeSurrounding("[", "]")
        return normalized == "localhost" ||
            normalized == "0.0.0.0" ||
            normalized == "::1" ||
            normalized == "::" ||
            normalized.startsWith("127.")
    }

    fun pairingUrl(baseUrl: String, token: String): String =
        "${baseUrl.trimEnd('/')}/api/devices/pairings/${encodeSegment(token)}"

    fun claimUrl(baseUrl: String, token: String): String =
        "${pairingUrl(baseUrl, token)}/claim"

    /** Percent-encodes a path segment without turning spaces into `+`. */
    fun encodeSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
