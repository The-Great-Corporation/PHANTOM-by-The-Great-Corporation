package com.manette.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

class UsbClient(
    private val port: Int,
    private val deviceId: String? = null,
    private val tokenId: String? = null,
    private val tokenSecret: String? = null
) : NetworkClient {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var authenticated = false
    private val gson = Gson()
    private val clientId = "android_${System.currentTimeMillis()}"
    private var sessionId: String? = null
    private var sessionSecret: ByteArray? = null
    private val sequence = AtomicLong(0)

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (listOf(deviceId, tokenId, tokenSecret).any { it.isNullOrEmpty() }) {
            Log.e("UsbClient", "USB credentials (deviceId, tokenId, tokenSecret) are required")
            return@withContext false
        }
        try {
            socket = Socket("127.0.0.1", port)
            outputStream = socket?.getOutputStream()
            inputStream = socket?.getInputStream()

            val clientIdentifier = deviceId ?: clientId
            val clientNonce = UUID.randomUUID().toString()
            sendMessage(mapOf(
                "type" to "connect",
                "client_id" to clientIdentifier,
                "device_id" to clientIdentifier,
                "token_id" to tokenId,
                "client_nonce" to clientNonce
            ))

            val reader = inputStream?.bufferedReader() ?: throw IllegalStateException("USB reader unavailable")
            val challengeLine = reader.readLine() ?: throw IllegalStateException("USB challenge missing")
            val challengeMap = gson.fromJson(challengeLine, Map::class.java) as? Map<*, *> ?: throw IllegalStateException("USB challenge invalid")
            if (challengeMap["type"] != "pair_challenge" || tokenSecret.isNullOrEmpty() || tokenId.isNullOrEmpty() || deviceId.isNullOrEmpty()) {
                throw IllegalStateException("USB authentication challenge missing")
            }

            val challengeId = challengeMap["challenge_id"] as? String ?: throw IllegalStateException("challenge_id missing")
            val challenge = challengeMap["challenge"] as? String ?: throw IllegalStateException("challenge missing")
            val proof = UdpClient.computePairingProof(tokenSecret, tokenId, deviceId, clientNonce, challengeId, challenge)
            sendMessage(mapOf(
                "type" to "pair_proof",
                "client_id" to clientIdentifier,
                "device_id" to clientIdentifier,
                "token_id" to tokenId,
                "client_nonce" to clientNonce,
                "challenge_id" to challengeId,
                "challenge" to challenge,
                "proof" to proof
            ))

            val ackLine = reader.readLine() ?: throw IllegalStateException("USB authentication ack missing")
            val ack = gson.fromJson(ackLine, Map::class.java) as? Map<*, *> ?: throw IllegalStateException("USB ack invalid")
            if (ack["type"] != "connected") {
                throw IllegalStateException("USB authentication acknowledgement missing")
            }
            val sid = ack["session_id"] as? String ?: throw IllegalStateException("session_id missing")
            val serverChallenge = ack["server_challenge"] as? String
                ?: throw IllegalStateException("server_challenge missing")
            sessionId = sid
            sessionSecret = UdpClient.deriveSessionSecret(
                tokenSecret, clientNonce, serverChallenge, sid
            )
            sequence.set(0)
            authenticated = true

            Log.d("UsbClient", "Connected via USB (ADB)")
            true
        } catch (e: Exception) {
            authenticated = false
            sessionId = null
            sessionSecret = null
            sequence.set(0)
            Log.e("UsbClient", "Connection failed: ${e.message}")
            Log.e("UsbClient", "Make sure ADB forwarding is set up: adb forward tcp:$port tcp:$port")
            false
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                outputStream?.close()
                inputStream?.close()
                socket?.close()
                outputStream = null
                inputStream = null
                socket = null
                authenticated = false
                sessionId = null
                sessionSecret = null
                sequence.set(0)
                Log.d("UsbClient", "Disconnected")
            } catch (e: Exception) {
                Log.e("UsbClient", "Disconnect error: ${e.message}")
            }
        }
    }

    override suspend fun sendInput(inputData: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!authenticated) return@withContext false
            val sid = sessionId ?: return@withContext false
            val secret = sessionSecret ?: return@withContext false
            val next = sequence.getAndIncrement()
            val authenticatedMessage = linkedMapOf<String, Any?>(
                "client_id" to (deviceId ?: clientId), "session_id" to sid, "payload" to inputData
            )
            val message = mapOf(
                "type" to "input",
                "client_id" to (deviceId ?: clientId),
                "device_id" to (deviceId ?: clientId),
                "session_id" to sid, "sequence" to next, "data" to inputData,
                "mac" to UdpClient.computeSessionMac(secret, next, authenticatedMessage)
            )
            sendMessage(message)
            true
        } catch (e: Exception) {
            Log.e("UsbClient", "Send input error: ${e.message}")
            false
        }
    }

    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!authenticated) return@withContext false
            val sid = sessionId ?: return@withContext false
            val secret = sessionSecret ?: return@withContext false
            val next = sequence.getAndIncrement()
            val authenticatedMessage = linkedMapOf<String, Any?>(
                "client_id" to (deviceId ?: clientId), "session_id" to sid,
                "payload" to emptyMap<String, Any?>()
            )
            val message = mapOf(
                "type" to "heartbeat",
                "client_id" to (deviceId ?: clientId),
                "device_id" to (deviceId ?: clientId), "session_id" to sid,
                "sequence" to next, "data" to emptyMap<String, Any?>(),
                "mac" to UdpClient.computeSessionMac(secret, next, authenticatedMessage)
            )
            sendMessage(message)
            true
        } catch (e: Exception) {
            Log.e("UsbClient", "Send heartbeat error: ${e.message}")
            false
        }
    }

    private fun sendMessage(message: Map<String, Any?>) {
        val json = gson.toJson(message)
        outputStream?.write((json + "\n").toByteArray())
        outputStream?.flush()
    }
}
