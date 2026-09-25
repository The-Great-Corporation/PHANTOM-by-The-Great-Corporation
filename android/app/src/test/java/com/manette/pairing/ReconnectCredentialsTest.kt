package com.manette.pairing

import com.manette.network.UdpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectCredentialsTest {

    @Test
    fun credentialsPersistAndAreUsableAfterQrExpiry() {
        val now = 2000L
        val expiredAt = 1000L // Expiry was 1000 seconds ago

        val credentials = PairingCredentials(
            server = "192.168.1.50",
            port = 8888,
            deviceId = "test_device_id",
            tokenId = "old_token_id",
            tokenSecret = "token_secret_value",
            expiresAtEpochSeconds = expiredAt
        )

        // Initial QR payload is expired for initial scan
        assertFalse(credentials.isValid(now))

        // But credentials remain usable for remembered reconnection!
        assertTrue(credentials.isUsableForReconnect())
    }

    @Test
    fun parserCanLoadExpiredSavedCredentials() {
        val json = """
            {
              "scheme": "phantom-pairing",
              "version": 1,
              "server": "192.168.1.100",
              "port": 8888,
              "device_id": "my_phone",
              "token_id": "token123",
              "token_secret": "secret123",
              "expires_at": 1000
            }
        """.trimIndent()

        // With ignoreExpiry = true (used when loading saved credentials from Keystore)
        val loaded = PairingPayloadParser.parse(json, nowEpochSeconds = 2000L, ignoreExpiry = true)
        assertEquals("my_phone", loaded.deviceId)
        assertEquals("secret123", loaded.tokenSecret)
        assertTrue(loaded.isUsableForReconnect())
    }

    @Test
    fun reconnectProofComputationMatchesExpectedFormat() {
        val proof = UdpClient.computeReconnectProof(
            tokenSecret = "secret",
            deviceId = "phone",
            clientNonce = "nonce",
            challengeId = "cid",
            challenge = "challenge"
        )
        assertTrue(proof.length == 64) // SHA-256 hex string length
    }
}
