package com.manette.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ConnectionState(
    val connected: Boolean = false,
    val connectionType: String = "none",
    val latency: Int = 0,
    val serverIp: String = "",
    val isReconnecting: Boolean = false,
    val error: String? = null
)

class ConnectionManager(private val context: Context) {
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentClient: NetworkClient? = null
    private val connectionMutex = Mutex()
    private var heartbeatJob: Job? = null
    private val heartbeatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        return connectionMutex.withLock {
            val existing = _connectionState.value
            if (existing.connected &&
                existing.connectionType == connectionType &&
                existing.serverIp == serverIp
            ) {
                return@withLock true
            }
            disconnect()
            _connectionState.value = ConnectionState(
                connectionType = connectionType,
                serverIp = serverIp,
                isReconnecting = true
            )

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
                else -> return@withLock false
            }

            val success = currentClient?.connect() ?: false

            if (success) {
                _connectionState.value = ConnectionState(
                    connected = true,
                    connectionType = connectionType,
                    serverIp = serverIp,
                    isReconnecting = false
                )
                heartbeatJob?.cancel()
                heartbeatJob = heartbeatScope.launch {
                    while (isActive) {
                        delay(5_000)
                        val sent = currentClient?.sendHeartbeat() == true
                        if (!sent && isActive) {
                            Log.w(
                                "ConnectionManager",
                                "Authenticated heartbeat failed via $connectionType"
                            )
                            currentClient?.disconnect()
                            currentClient = null
                            _connectionState.value = ConnectionState(
                                connectionType = connectionType,
                                serverIp = serverIp,
                                isReconnecting = false,
                                error = "Connexion perdue : le serveur ne répond plus."
                            )
                            heartbeatJob?.cancel()
                        }
                    }
                }
                Log.d("ConnectionManager", "Connected via $connectionType")
            } else {
                currentClient = null
                _connectionState.value = ConnectionState(
                    connectionType = connectionType,
                    serverIp = serverIp,
                    isReconnecting = false,
                    error = "Connexion impossible au serveur."
                )
                Log.e("ConnectionManager", "Failed to connect via $connectionType")
            }

            success
        }
    }

    suspend fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
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
