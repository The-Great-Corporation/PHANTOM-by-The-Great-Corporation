package com.manette.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpClient(
    private var serverIp: String,
    private val port: Int,
    private val onLatencyUpdated: ((Int) -> Unit)? = null,
    private val onVibrationReceived: ((Float, Float, Float) -> Unit)? = null
) : NetworkClient {
    
    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private val gson = Gson()
    private val clientId = "android_" + System.currentTimeMillis()
    private var receiveJob: Job? = null
    private var pingJob: Job? = null
    private var lastPingSentTime: Long = 0L
    
    companion object {
        suspend fun autoDiscoverServer(port: Int = 8888, timeoutMs: Int = 2000): String? = withContext(Dispatchers.IO) {
            var discSocket: DatagramSocket? = null
            try {
                discSocket = DatagramSocket()
                discSocket.broadcast = true
                discSocket.soTimeout = timeoutMs

                val probeMsg = "{\"type\":\"discover\"}".toByteArray()

                // Collect all possible broadcast destinations: universal, subnet-directed, and localhost (USB)
                val targetAddresses = mutableSetOf<InetAddress>()
                try {
                    targetAddresses.add(InetAddress.getByName("255.255.255.255"))
                } catch (_: Exception) {}
                try {
                    targetAddresses.add(InetAddress.getByName("127.0.0.1"))
                } catch (_: Exception) {}

                try {
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (iface.isLoopback || !iface.isUp) continue
                        for (addr in iface.interfaceAddresses) {
                            addr.broadcast?.let { targetAddresses.add(it) }
                        }
                    }
                } catch (_: Exception) {}

                // Send discovery probe to all target addresses
                for (target in targetAddresses) {
                    try {
                        val sendPacket = DatagramPacket(probeMsg, probeMsg.size, target, port)
                        discSocket.send(sendPacket)
                    } catch (_: Exception) {}
                }

                val buffer = ByteArray(1024)
                val recvPacket = DatagramPacket(buffer, buffer.size)
                discSocket.receive(recvPacket)

                val response = String(recvPacket.data, 0, recvPacket.length)
                if (response.contains("discover_ack")) {
                    val serverIp = recvPacket.address.hostAddress
                    Log.i("UdpClient", "Zero-Friction: Successfully auto-discovered PHANTOM server at: $serverIp")
                    return@withContext serverIp
                }
                null
            } catch (e: Exception) {
                Log.d("UdpClient", "Auto-discovery timeout or no server responding")
                null
            } finally {
                discSocket?.close()
            }
        }
    }
    
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = DatagramSocket()
            socket?.soTimeout = 3000
            serverAddress = InetAddress.getByName(serverIp)
            
            // Start listener job
            receiveJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                while (isActive && socket != null && !socket!!.isClosed) {
                    try {
                        socket?.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        handleIncomingMessage(message)
                    } catch (e: Exception) {
                        // Timeout is normal in UDP receive loop
                    }
                }
            }
            
            // Send connection handshake
            val connectMessage = mapOf(
                "type" to "connect",
                "client_id" to clientId
            )
            sendMessage(connectMessage)
            
            // Periodic ping to measure latency
            pingJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    sendPing()
                    delay(2000)
                }
            }
            
            Log.d("UdpClient", "Connected to " + serverIp + ":" + port)
            true
        } catch (e: Exception) {
            Log.e("UdpClient", "Connection failed: " + e.message)
            false
        }
    }
    
    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val data = gson.fromJson(jsonStr, Map::class.java)
            when (data["type"] as? String) {
                "pong" -> {
                    val rtt = (System.currentTimeMillis() - lastPingSentTime).toInt().coerceAtLeast(1)
                    onLatencyUpdated?.invoke(rtt)
                }
                "vibration" -> {
                    // Retour haptique du jeu PC → vibration native du téléphone
                    val left = (data["left_motor"] as? Double)?.toFloat() ?: 0f
                    val right = (data["right_motor"] as? Double)?.toFloat() ?: 0f
                    val durationSec = (data["duration"] as? Double)?.toFloat() ?: 0.2f
                    onVibrationReceived?.invoke(left, right, durationSec)
                }
            }
        } catch (e: Exception) {
            Log.e("UdpClient", "Error parsing message: " + e.message)
        }
    }
    
    private fun sendPing() {
        lastPingSentTime = System.currentTimeMillis()
        val pingMessage = mapOf(
            "type" to "ping",
            "client_id" to clientId
        )
        sendMessage(pingMessage)
    }
    
    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                pingJob?.cancel()
                receiveJob?.cancel()
                socket?.close()
                socket = null
                Log.d("UdpClient", "Disconnected")
            } catch (e: Exception) {
                Log.e("UdpClient", "Disconnect error: " + e.message)
            }
        }
    }
    
    override suspend fun sendInput(inputData: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            val message = mapOf(
                "type" to "input",
                "client_id" to clientId,
                "data" to inputData
            )
            sendMessage(message)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        try {
            val message = mapOf(
                "type" to "heartbeat",
                "client_id" to clientId
            )
            sendMessage(message)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun sendMessage(message: Map<String, Any>) {
        try {
            val json = gson.toJson(message)
            val data = json.toByteArray()
            val packet = DatagramPacket(data, data.size, serverAddress, port)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e("UdpClient", "Send error: " + e.message)
        }
    }
}
