package com.example.smsguard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Result of the optional pre-flight GET that checks a pairing code before sending identifiers. */
sealed interface PairingPreflight {
    data object Ready : PairingPreflight
    data object AlreadyUsed : PairingPreflight
    data object Expired : PairingPreflight
    data class Unavailable(val message: String) : PairingPreflight
}

/** Result of `POST /api/devices/pairings/:token/claim`. */
sealed interface PairingClaimResult {
    data class Success(val deviceName: String?) : PairingClaimResult
    data class AlreadyUsed(val message: String) : PairingClaimResult
    data class Expired(val message: String) : PairingClaimResult
    data class Rejected(val message: String) : PairingClaimResult
    data class Failed(val message: String) : PairingClaimResult
}

/**
 * Small `HttpURLConnection` client for the pairing endpoints.
 *
 * The dashboard is a local/self-hosted development service, so pulling in a full HTTP stack is not
 * worth the dependency. Redirects are not followed because the token must only be sent to the
 * configured origin.
 */
object PairingClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    suspend fun preflight(baseUrl: String, token: String): PairingPreflight =
        withContext(Dispatchers.IO) {
            try {
                val response = execute(
                    url = URL(PairingEndpoint.pairingUrl(baseUrl, token)),
                    method = "GET",
                    body = null
                )
                when (response.code) {
                    HttpURLConnection.HTTP_OK,
                    HttpURLConnection.HTTP_CREATED,
                    HttpURLConnection.HTTP_ACCEPTED -> {
                        when (pairingStatus(response.body)) {
                            "claimed", "used", "paired", "completed" -> PairingPreflight.AlreadyUsed
                            "expired", "revoked", "cancelled", "canceled" -> PairingPreflight.Expired
                            else -> PairingPreflight.Ready
                        }
                    }
                    HttpURLConnection.HTTP_NO_CONTENT -> PairingPreflight.Ready
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        // A 404 with a JSON body is a real "unknown token". A 404 with an HTML
                        // body usually means the optional GET route is not implemented, in which
                        // case the claim below is still authoritative.
                        if (parseBody(response.body) != null) {
                            PairingPreflight.Expired
                        } else {
                            PairingPreflight.Ready
                        }
                    }
                    HttpURLConnection.HTTP_GONE -> PairingPreflight.Expired
                    HttpURLConnection.HTTP_CONFLICT -> PairingPreflight.AlreadyUsed
                    HttpURLConnection.HTTP_BAD_REQUEST -> PairingPreflight.Unavailable(
                        errorMessage(response.body)
                            ?: "The server rejected this pairing code."
                    )
                    // The GET is advisory: a server that does not implement it must not block the
                    // claim, which is the operation that actually matters.
                    else -> PairingPreflight.Ready
                }
            } catch (error: Exception) {
                // A failed pre-flight is not conclusive either; let the claim produce the
                // authoritative error message for an unreachable or misconfigured server.
                PairingPreflight.Ready
            }
        }

    suspend fun claim(baseUrl: String, token: String, payload: JSONObject): PairingClaimResult =
        withContext(Dispatchers.IO) {
            try {
                val response = execute(
                    url = URL(PairingEndpoint.claimUrl(baseUrl, token)),
                    method = "POST",
                    body = payload
                )
                when (response.code) {
                    HttpURLConnection.HTTP_OK,
                    HttpURLConnection.HTTP_CREATED -> PairingClaimResult.Success(
                        deviceName(response.body)
                    )
                    HttpURLConnection.HTTP_CONFLICT -> PairingClaimResult.AlreadyUsed(
                        errorMessage(response.body)
                            ?: "This pairing code has already been used. Scan a new QR code."
                    )
                    HttpURLConnection.HTTP_GONE,
                    HttpURLConnection.HTTP_NOT_FOUND -> PairingClaimResult.Expired(
                        errorMessage(response.body)
                            ?: "This pairing code has expired. Scan a new QR code."
                    )
                    HttpURLConnection.HTTP_BAD_REQUEST -> PairingClaimResult.Rejected(
                        errorMessage(response.body)
                            ?: "The server rejected the device details."
                    )
                    else -> PairingClaimResult.Failed(
                        errorMessage(response.body)
                            ?: "Pairing failed (HTTP ${response.code})."
                    )
                }
            } catch (error: Exception) {
                PairingClaimResult.Failed(
                    "Could not reach ${baseUrl.trimEnd('/')}. " +
                        "Check that the dashboard is running and the address is reachable from this phone."
                )
            }
        }

    private data class Response(val code: Int, val body: String)

    private fun execute(url: URL, method: String, body: JSONObject?): Response {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBody(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()

    private fun pairingStatus(body: String): String? {
        val json = parseBody(body) ?: return null
        val direct = json.optString("status")
        if (direct.isNotBlank()) return direct.lowercase()
        val pairing = json.optJSONObject("pairing") ?: return null
        return pairing.optString("status").takeIf { it.isNotBlank() }?.lowercase()
    }

    private fun deviceName(body: String): String? {
        val json = parseBody(body) ?: return null
        val device = json.optJSONObject("device")
        return sequenceOf(
            device?.optString("name"),
            json.optString("deviceName"),
            json.optString("name")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun errorMessage(body: String): String? {
        val json = parseBody(body)
        if (json != null) {
            when (val error = json.opt("error")) {
                is String -> error.takeIf { it.isNotBlank() }?.let { return it.trim() }
                is JSONObject -> {
                    sequenceOf(error.optString("message"), error.optString("detail"))
                        .firstOrNull { it.isNotBlank() }
                        ?.let { return it.trim() }
                }
            }
            sequenceOf(json.optString("message"), json.optString("detail"))
                .firstOrNull { it.isNotBlank() }
                ?.let { return it.trim() }
            when (val data = json.opt("data")) {
                is JSONObject -> {
                    sequenceOf(data.optString("message"), data.optString("error"))
                        .firstOrNull { it.isNotBlank() }
                        ?.let { return it.trim() }
                }
            }
        }
        return body.trim().takeIf { text ->
            text.isNotEmpty() && text.length <= 300 && !text.startsWith("<")
        }
    }
}
