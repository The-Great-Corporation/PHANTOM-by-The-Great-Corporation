package com.manette.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionState(
    val connected: Boolean = false,
    val connectionType: String = "none",
    val latency: Int = 0,
    val serverIp: String = ""
)

class ConnectionManager(private val context: Context) {
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentClient: NetworkClient? = null

    /** Appelé par le ViewModel pour recevoir les événements de rumble du serveur PC. */
    var onVibrationReceived: ((Float, Float, Float) -> Unit)? = null

    suspend fun connect(
        connectionType: String,
        serverIp: String,
        port: Int,
        deviceId: String? = null,
        tokenId: String? = null,
        tokenSecret: String? = null
    ): Boolean {
        disconnect()

        currentClient = when (connectionType) {
            "udp" -> UdpClient(
                serverIp = serverIp,
                port = port,
                deviceId = deviceId,
                tokenId = tokenId,
                tokenSecret = tokenSecret,
                onLatencyUpdated = { lat -> updateLatency(lat) },
                onVibrationReceived = onVibrationReceived
            )
            "websocket" -> WebSocketClient(serverIp, port, deviceId, tokenId, tokenSecret)
            "bluetooth" -> BluetoothClient(port, deviceId, tokenId, tokenSecret)
            "usb" -> UsbClient(port, deviceId, tokenId, tokenSecret)
            else -> return false
        }

        val success = currentClient?.connect() ?: false

        if (success) {
            _connectionState.value = ConnectionState(
                connected = true,
                connectionType = connectionType,
                serverIp = serverIp
            )
            Log.d("ConnectionManager", "Connected via $connectionType")
        } else {
            _connectionState.value = ConnectionState()
            Log.e("ConnectionManager", "Failed to connect via $connectionType")
        }

        return success
    }

    suspend fun disconnect() {
        currentClient?.disconnect()
        currentClient = null
        _connectionState.value = ConnectionState()
        Log.d("ConnectionManager", "Disconnected")
    }

    suspend fun sendInput(inputData: Map<String, Any>): Boolean {
        return currentClient?.sendInput(inputData) ?: false
    }

    suspend fun sendHeartbeat(): Boolean {
        return currentClient?.sendHeartbeat() ?: false
    }

    fun updateLatency(latency: Int) {
        _connectionState.value = _connectionState.value.copy(latency = latency)
    }

    fun isConnected(): Boolean {
        return _connectionState.value.connected
    }
}
