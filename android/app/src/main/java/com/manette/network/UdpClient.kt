package com.manette.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated UDP client. Credentials are deliberately supplied by the caller and
 * are kept only in this instance; they are not persisted or written to logs.
 */
class UdpClient(
    private var serverIp: String,
    private val port: Int,
    private val deviceId: String?,
    private val tokenId: String?,
    private val tokenSecret: String?,
    private val onLatencyUpdated: ((Int) -> Unit)? = null,
    private val onVibrationReceived: ((Float, Float, Float) -> Unit)? = null
) : NetworkClient {
    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private val gson = Gson()
    private var receiveJob: Job? = null
    private var pingJob: Job? = null
    private val pendingPings = ConcurrentHashMap<Long, Long>()
    private var sessionId: String? = null
    private var sessionSecret: ByteArray? = null
    private val sequence = AtomicLong(0)

    companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 3000
        private const val SESSION_PREFIX = "phantom-udp-session-v1\u0000"
        private val secureRandom = SecureRandom()

        fun canonicalJson(value: Any?): String = canonicalElement(toJsonElement(value))

        fun computePairingProof(
            tokenSecret: String, tokenId: String, deviceId: String, clientNonce: String,
            challengeId: String, challenge: String
        ): String {
            require(listOf(tokenSecret, tokenId, deviceId, clientNonce, challengeId, challenge)
                .all { it.isNotEmpty() })
            val transcript = linkedMapOf<String, Any>(
                "type" to "pair_proof", "token_id" to tokenId, "device_id" to deviceId,
                "client_nonce" to clientNonce, "challenge_id" to challengeId, "challenge" to challenge
            )
            return hmacHex(tokenSecret, canonicalJson(transcript).toByteArray(StandardCharsets.UTF_8))
        }

        fun computeReconnectProof(
            tokenSecret: String, deviceId: String, clientNonce: String,
            challengeId: String, challenge: String
        ): String {
            require(listOf(tokenSecret, deviceId, clientNonce, challengeId, challenge).all { it.isNotEmpty() })
            val transcript = linkedMapOf<String, Any>(
                "type" to "reconnect_proof", "device_id" to deviceId,
                "client_nonce" to clientNonce, "challenge_id" to challengeId, "challenge" to challenge
            )
            return hmacHex(tokenSecret, canonicalJson(transcript).toByteArray(StandardCharsets.UTF_8))
        }

        fun deriveSessionSecret(
            tokenSecret: String, clientNonce: String, serverChallenge: String, sessionId: String
        ): ByteArray {
            require(listOf(tokenSecret, clientNonce, serverChallenge, sessionId).all { it.isNotEmpty() })
            val context = canonicalJson(linkedMapOf(
                "client_nonce" to clientNonce,
                "server_challenge" to serverChallenge,
                "session_id" to sessionId
            ))
            return hmac(tokenSecret, (SESSION_PREFIX + context).toByteArray(StandardCharsets.UTF_8))
        }

        fun canonicalMessage(sequence: Long, message: Map<String, Any?>): String {
            require(sequence >= 0)
            return canonicalJson(linkedMapOf("message" to message, "sequence" to sequence))
        }

        fun computeSessionMac(secret: ByteArray, sequence: Long, message: Map<String, Any?>): String =
            hmac(secret, canonicalMessage(sequence, message).toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        private fun hmacHex(secret: String, data: ByteArray): String =
            hmac(secret, data).joinToString("") { "%02x".format(it) }

        private fun hmac(secret: String, data: ByteArray): ByteArray =
            hmac(secret.toByteArray(StandardCharsets.UTF_8), data)

        private fun hmac(secret: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret, "HmacSHA256"))
            return mac.doFinal(data)
        }

        private fun toJsonElement(value: Any?): JsonElement =
            when (value) {
                is JsonElement -> value
                else -> JsonParser.parseString(Gson().toJson(value))
            }

        private fun canonicalElement(value: JsonElement): String {
            if (value.isJsonNull) return "null"
            if (value.isJsonPrimitive) {
                val p = value.asJsonPrimitive
                if (p.isString) return quote(p.asString)
                return p.toString()
            }
            if (value.isJsonArray) return value.asJsonArray.joinToString(",", "[", "]") { canonicalElement(it) }
            val obj = value.asJsonObject
            return obj.entrySet().sortedBy { it.key }.joinToString(",", "{", "}") {
                quote(it.key) + ":" + canonicalElement(it.value)
            }
        }

        private fun quote(value: String): String {
            val out = StringBuilder("\"")
            value.forEach { c ->
                when (c) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\b' -> out.append("\\b")
                    '\u000c' -> out.append("\\f")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    in '\u0000'..'\u001f' -> out.append("\\u%04x".format(c.code))
                    else -> out.append(c)
                }
            }
            return out.append('"').toString()
        }

        suspend fun autoDiscoverServer(port: Int = 8888, timeoutMs: Int = 2000): String? = withContext(Dispatchers.IO) {
            var discSocket: DatagramSocket? = null
            try {
                discSocket = DatagramSocket().apply { broadcast = true; soTimeout = timeoutMs }
                val probeMsg = "{\"type\":\"discover\"}".toByteArray(StandardCharsets.UTF_8)
                val targets = mutableSetOf<InetAddress>()
                runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
                runCatching { targets.add(InetAddress.getByName("127.0.0.1")) }
                runCatching {
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (!iface.isLoopback && iface.isUp) iface.interfaceAddresses.forEach { it.broadcast?.let(targets::add) }
                    }
                }
                targets.forEach { target -> runCatching { discSocket!!.send(DatagramPacket(probeMsg, probeMsg.size, target, port)) } }
                val packet = DatagramPacket(ByteArray(1024), 1024)
                discSocket.receive(packet)
                if (String(packet.data, 0, packet.length, StandardCharsets.UTF_8).contains("discover_ack"))
                    packet.address.hostAddress
                else null
            } catch (_: Exception) { null } finally { discSocket?.close() }
        }
    }

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (deviceId.isNullOrEmpty() || tokenSecret.isNullOrEmpty()) {
            Log.e("UdpClient", "UDP credentials (deviceId, tokenSecret) are required")
            return@withContext false
        }
        try {
            socket = DatagramSocket().apply { soTimeout = HANDSHAKE_TIMEOUT_MS }
            serverAddress = InetAddress.getByName(serverIp)
            val nonce = randomNonce()

            var isConnected = false
            // Try reconnect_begin first
            try {
                sendRaw(linkedMapOf("type" to "reconnect_begin", "device_id" to deviceId, "client_nonce" to nonce))
                val response = receiveJson()
                if (response["type"] == "reconnect_challenge") {
                    val challengeId = response["challenge_id"] as? String ?: error("challenge_id missing")
                    val challengeValue = response["challenge"] as? String ?: error("challenge missing")
                    val proof = computeReconnectProof(tokenSecret, deviceId, nonce, challengeId, challengeValue)
                    sendRaw(linkedMapOf(
                        "type" to "reconnect_proof", "device_id" to deviceId,
                        "client_nonce" to nonce, "challenge_id" to challengeId,
                        "challenge" to challengeValue, "proof" to proof
                    ))
                    val ack = receiveJson()
                    if (ack["type"] == "connected" || ack["type"] == "pair_ack") {
                        val sid = ack["session_id"] as? String ?: error("session_id missing")
                        val serverChallenge = ack["server_challenge"] as? String ?: error("server_challenge missing")
                        sessionId = sid
                        sessionSecret = deriveSessionSecret(tokenSecret, nonce, serverChallenge, sid)
                        sequence.set(0)
                        isConnected = true
                        Log.d("UdpClient", "UDP reconnection successful")
                    }
                }
            } catch (e: Exception) {
                Log.d("UdpClient", "UDP reconnection attempt failed, trying initial pairing if token available: ${e.message}")
            }

            // Fallback to initial pairing if reconnection did not succeed and tokenId is available
            if (!isConnected && !tokenId.isNullOrEmpty()) {
                sendRaw(linkedMapOf("type" to "pair_begin", "device_id" to deviceId, "token_id" to tokenId, "client_nonce" to nonce))
                val challenge = receiveJson()
                if (challenge["type"] == "pair_challenge") {
                    val challengeId = challenge["challenge_id"] as? String ?: error("challenge_id missing")
                    val challengeValue = challenge["challenge"] as? String ?: error("challenge missing")
                    val proof = computePairingProof(tokenSecret, tokenId, deviceId, nonce, challengeId, challengeValue)
                    sendRaw(linkedMapOf(
                        "type" to "pair_proof", "device_id" to deviceId,
                        "token_id" to tokenId, "client_nonce" to nonce, "challenge_id" to challengeId,
                        "challenge" to challengeValue, "proof" to proof
                    ))
                    val ack = receiveJson()
                    if (ack["type"] == "pair_ack" && ack["confirmation"] == "paired") {
                        val sid = ack["session_id"] as? String ?: error("session_id missing")
                        val serverChallenge = ack["server_challenge"] as? String ?: error("server_challenge missing")
                        sessionId = sid
                        sessionSecret = deriveSessionSecret(tokenSecret, nonce, serverChallenge, sid)
                        sequence.set(0)
                        isConnected = true
                        Log.d("UdpClient", "UDP initial QR pairing successful")
                    }
                }
            }

            if (!isConnected) {
                disconnect()
                return@withContext false
            }

            socket!!.soTimeout = HANDSHAKE_TIMEOUT_MS
            startReceiver()
            pingJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) { sendPing(); delay(2000) }
            }
            true
        } catch (e: Exception) {
            Log.e("UdpClient", "UDP handshake failed: ${e.message}")
            disconnect()
            false
        }
    }

    private fun startReceiver() {
        receiveJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            while (isActive && socket?.isClosed == false) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handleIncomingMessage(String(packet.data, 0, packet.length, StandardCharsets.UTF_8))
                } catch (_: Exception) { }
            }
        }
    }

    private fun receiveJson(): Map<String, Any?> {
        val packet = DatagramPacket(ByteArray(4096), 4096)
        socket!!.receive(packet)
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(String(packet.data, 0, packet.length, StandardCharsets.UTF_8), Map::class.java) as Map<String, Any?>
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val data = gson.fromJson(jsonStr, Map::class.java)
            when (data["type"] as? String) {
                "pong" -> {
                    val pongSequence = (data["sequence"] as? Double)?.toLong()
                    val sentAt = pongSequence?.let { pendingPings.remove(it) }
                    if (sentAt != null) {
                        onLatencyUpdated?.invoke(
                            (System.currentTimeMillis() - sentAt).toInt().coerceAtLeast(1)
                        )
                    }
                }
                "vibration" -> onVibrationReceived?.invoke(
                    (data["left_motor"] as? Double)?.toFloat() ?: 0f,
                    (data["right_motor"] as? Double)?.toFloat() ?: 0f,
                    (data["duration"] as? Double)?.toFloat() ?: 0.2f)
            }
        } catch (_: Exception) { }
    }

    private fun sendPing() {
        val sentAt = System.currentTimeMillis()
        sendControl("ping", emptyMap()) { sequenceNumber ->
            pendingPings[sequenceNumber] = sentAt
            if (pendingPings.size > 8) {
                pendingPings.keys.minOrNull()?.let(pendingPings::remove)
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        pingJob?.cancel(); receiveJob?.cancel(); socket?.close(); socket = null
        sessionId = null; sessionSecret = null; sequence.set(0); pendingPings.clear()
    }

    override suspend fun sendInput(inputData: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        sendControl("input", inputData)
    }

    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        sendControl("heartbeat", emptyMap())
    }

    private fun sendControl(
        type: String,
        payload: Map<String, Any?>,
        onSequenceAllocated: ((Long) -> Unit)? = null
    ): Boolean {
        val sid = sessionId ?: return false
        val secret = sessionSecret ?: return false
        val client = deviceId ?: return false
        val next = sequence.getAndIncrement()
        onSequenceAllocated?.invoke(next)
        val authenticated = linkedMapOf<String, Any?>(
            "type" to type, "client_id" to client, "session_id" to sid, "payload" to payload)
        val mac = hmac(secret, canonicalMessage(next, authenticated).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return runCatching {
            sendRaw(linkedMapOf("type" to type, "client_id" to client, "session_id" to sid,
                "sequence" to next, "data" to payload, "mac" to mac))
            true
        }.getOrDefault(false)
    }

    private fun sendRaw(message: Map<String, Any?>) {
        val bytes = canonicalJson(message).toByteArray(StandardCharsets.UTF_8)
        socket?.send(DatagramPacket(bytes, bytes.size, serverAddress, port))
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE)
    }
}
