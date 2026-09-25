package com.manette.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {
    private val valid = """{"scheme":"phantom-pairing","version":1,"server":"192.168.1.20","port":8888,"device_id":"phone-a","token_id":"token","token_secret":"secret","expires_at":2000}"""

    @Test
    fun parsesVersionedPayload() {
        val credentials = PairingPayloadParser.parse(valid, nowEpochSeconds = 1000)
        assertEquals("192.168.1.20", credentials.server)
        assertEquals(8888, credentials.port)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongSchemaVersion() {
        PairingPayloadParser.parse(valid.replace("\"version\":1", "\"version\":2"), 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExpiredPayload() {
        PairingPayloadParser.parse(valid, nowEpochSeconds = 2000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUriAndInvalidPort() {
        PairingPayloadParser.parse(valid.replace("192.168.1.20", "udp://server"), 1000)
    }

    @Test
    fun credentialStoreApiCanBeTestedWithoutFiles() {
        val store = InMemoryCredentialStore()
        val credentials = PairingPayloadParser.parse(valid, 1000)
        store.save(credentials)
        assertNotNull(store.load())
        store.clear()
        assertNull(store.load())
    }
}
