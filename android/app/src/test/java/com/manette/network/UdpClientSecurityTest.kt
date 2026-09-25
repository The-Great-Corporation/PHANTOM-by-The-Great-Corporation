package com.manette.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UdpClientSecurityTest {
    @Test fun canonicalJsonSortsKeysWithoutSpacesAndKeepsUtf8() {
        assertEquals("""{"a":"é","nested":{"a":1,"z":true},"z":2}""",
            UdpClient.canonicalJson(mapOf("z" to 2, "nested" to mapOf("z" to true, "a" to 1), "a" to "é")))
    }

    @Test fun pairingProofMatchesServerTranscript() {
        assertEquals(
            "d64d77662a07e80eb923ca28f81290c7d80bd29a6eb8d75c16bafee2bbd74a5a",
            UdpClient.computePairingProof("secret", "token", "phone", "nonce", "cid", "challenge")
        )
    }

    @Test fun sessionDerivationIsDeterministicAndBoundToContext() {
        val first = UdpClient.deriveSessionSecret("secret", "nonce", "challenge", "session")
        assertArrayEquals(first, UdpClient.deriveSessionSecret("secret", "nonce", "challenge", "session"))
        assertNotEquals(first.toList(), UdpClient.deriveSessionSecret("secret", "other", "challenge", "session").toList())
    }

    @Test fun controlCanonicalizationIncludesSequenceAndEnvelope() {
        val message = mapOf<String, Any?>(
            "type" to "input", "client_id" to "phone", "session_id" to "sid",
            "payload" to mapOf("a" to true)
        )
        assertEquals(
            """{"message":{"client_id":"phone","payload":{"a":true},"session_id":"sid","type":"input"},"sequence":7}""",
            UdpClient.canonicalMessage(7, message)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeSequenceIsRejected() {
        UdpClient.canonicalMessage(-1, emptyMap())
    }
}
