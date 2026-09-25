package com.manette.pairing

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

data class PairingCredentials(
    val server: String,
    val port: Int,
    val deviceId: String,
    val tokenId: String,
    val tokenSecret: String,
    val expiresAtEpochSeconds: Long
) {
    fun isValid(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        server.isNotBlank() && port in 1..65535 && deviceId.isNotBlank() &&
            tokenId.isNotBlank() && tokenSecret.isNotBlank() && expiresAtEpochSeconds > nowEpochSeconds

    fun isUsableForReconnect(): Boolean =
        server.isNotBlank() && port in 1..65535 && deviceId.isNotBlank() &&
            tokenId.isNotBlank() && tokenSecret.isNotBlank()
}

data class PairingPayloadDocument(
    val scheme: String? = null,
    val version: Int? = null,
    val server: String? = null,
    val port: Int? = null,
    val device_id: String? = null,
    val token_id: String? = null,
    val token_secret: String? = null,
    val expires_at: Long? = null
)

object PairingPayloadParser {
    private const val SCHEME = "phantom-pairing"
    private const val VERSION = 1
    private val serverPattern = Regex("^[A-Za-z0-9.-]+$")

    fun parse(
        raw: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        ignoreExpiry: Boolean = false
    ): PairingCredentials {
        if (raw.length > 4096) throw IllegalArgumentException("Pairing payload is too large")
        val document = try {
            Gson().fromJson(raw.trim(), PairingPayloadDocument::class.java)
        } catch (_: JsonSyntaxException) {
            throw IllegalArgumentException("Pairing payload must be valid JSON")
        } ?: throw IllegalArgumentException("Pairing payload is empty")

        val server = document.server?.trim()
            ?: throw IllegalArgumentException("Pairing server is missing")
        if (!serverPattern.matches(server) || server.contains("..") || server.contains("://")) {
            throw IllegalArgumentException("Pairing server is invalid")
        }
        val port = document.port ?: throw IllegalArgumentException("Pairing port is missing")
        val deviceId = document.device_id?.trim().orEmpty()
        val tokenId = document.token_id?.trim().orEmpty()
        val tokenSecret = document.token_secret?.trim().orEmpty()
        val expiresAt = document.expires_at ?: throw IllegalArgumentException("Pairing expiry is missing")
        if (document.scheme != SCHEME || document.version != VERSION) {
            throw IllegalArgumentException("Unsupported pairing payload schema")
        }
        if (port !in 1..65535 || deviceId.isEmpty() || tokenId.isEmpty() || tokenSecret.isEmpty()) {
            throw IllegalArgumentException("Pairing credentials are incomplete")
        }
        val credentials = PairingCredentials(server, port, deviceId, tokenId, tokenSecret, expiresAt)
        if (!ignoreExpiry && !credentials.isValid(nowEpochSeconds)) {
            throw IllegalArgumentException("Pairing payload is expired")
        }
        if (!credentials.isUsableForReconnect()) {
            throw IllegalArgumentException("Pairing credentials are incomplete")
        }
        return credentials
    }
}
