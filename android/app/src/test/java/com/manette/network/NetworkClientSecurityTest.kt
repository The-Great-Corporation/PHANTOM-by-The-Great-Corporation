package com.manette.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkClientSecurityTest {

    @Test
    fun webSocketClientRejectsMissingCredentials() = runBlocking {
        val client = WebSocketClient(
            serverIp = "127.0.0.1",
            port = 8765,
            deviceId = null,
            tokenId = null,
            tokenSecret = null
        )
        assertFalse(client.connect())
        assertFalse(client.sendInput(mapOf("a" to true)))
        assertFalse(client.sendHeartbeat())
    }

    @Test
    fun webSocketClientRejectsEmptyCredentials() = runBlocking {
        val client = WebSocketClient(
            serverIp = "127.0.0.1",
            port = 8765,
            deviceId = "",
            tokenId = "tok",
            tokenSecret = "sec"
        )
        assertFalse(client.connect())
        assertFalse(client.sendInput(mapOf("a" to true)))
        assertFalse(client.sendHeartbeat())
    }

    @Test
    fun usbClientRejectsMissingCredentials() = runBlocking {
        val client = UsbClient(
            port = 9999,
            deviceId = null,
            tokenId = null,
            tokenSecret = null
        )
        assertFalse(client.connect())
        assertFalse(client.sendInput(mapOf("a" to true)))
        assertFalse(client.sendHeartbeat())
    }

    @Test
    fun usbClientRejectsEmptyCredentials() = runBlocking {
        val client = UsbClient(
            port = 9999,
            deviceId = "dev",
            tokenId = "",
            tokenSecret = "sec"
        )
        assertFalse(client.connect())
        assertFalse(client.sendInput(mapOf("a" to true)))
        assertFalse(client.sendHeartbeat())
    }

    @Test
    fun udpClientRejectsMissingCredentials() = runBlocking {
        val client = UdpClient(
            serverIp = "127.0.0.1",
            port = 8888,
            deviceId = null,
            tokenId = null,
            tokenSecret = null
        )
        assertFalse(client.connect())
        assertFalse(client.sendInput(mapOf("a" to true)))
        assertFalse(client.sendHeartbeat())
    }

    @Test
    fun bluetoothClientRefusesConnectionCleanly() = runBlocking {
        val client = BluetoothClient(
            port = 1,
            deviceId = "dev",
            tokenId = "tok",
            tokenSecret = "sec"
        )
        assertFalse(client.connect())
        assertFalse(client.sendInput(mapOf("a" to true)))
        assertFalse(client.sendHeartbeat())
        client.disconnect()
    }
}
