package com.manette.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

class WebSocketClient(
    private val serverIp: String,
    private val port: Int,
    private val deviceId: String? = null,
    private val tokenId: String? = null,
    private val tokenSecret: String? = null
) : NetworkClient {

    private var webSocketClient: WebSocketClient? = null
    private val gson = Gson()
    private val clientId = "android_${System.currentTimeMillis()}"
    private var connected = false
    private var authenticated = false
    private var connectFailure: String? = null
    private var connectMessageNonce = ""
    private var sessionId: String? = null
    private var sessionSecret: ByteArray? = null
    private val sequence = AtomicLong(0)

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (listOf(deviceId, tokenId, tokenSecret).any { it.isNullOrEmpty() }) {
            Log.e("WebSocketClient", "WebSocket credentials (deviceId, tokenId, tokenSecret) are required")
            return@withContext false
        }
        try {
            connectFailure = null
            authenticated = false
            sessionId = null
            sessionSecret = null
            sequence.set(0)
            connected = false
            connectMessageNonce = UUID.randomUUID().toString()
            val uri = URI("ws://$serverIp:$port")

            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    connected = true
                    Log.d("WebSocketClient", "Connected to server")
                    val clientIdentifier = deviceId ?: clientId
                    send(gson.toJson(mapOf(
                        "type" to "connect",
                        "client_id" to clientIdentifier,
                        "device_id" to clientIdentifier,
                        "token_id" to tokenId,
                        "client_nonce" to connectMessageNonce
                    )))
                }

                override fun onMessage(message: String?) {
                    val payload = message ?: return
                    Log.d("WebSocketClient", "Received: $payload")
                    val json = JsonParser.parseString(payload).asJsonObject
                    when (json.get("type")?.asString) {
                        "pair_challenge" -> {
                            if (tokenSecret.isNullOrEmpty() || tokenId.isNullOrEmpty() || deviceId.isNullOrEmpty()) {
                                connectFailure = "Pairing credentials are required"
                                close()
                                return
                            }
                            val proof = UdpClient.computePairingProof(
                                tokenSecret,
                                tokenId,
                                deviceId,
                                connectMessageNonce,
                                json.get("challenge_id").asString,
                                json.get("challenge").asString,
                            )
                            send(gson.toJson(mapOf(
                                "type" to "pair_proof",
                                "client_id" to (deviceId ?: clientId),
                                "device_id" to (deviceId ?: clientId),
                                "token_id" to tokenId,
                                "client_nonce" to connectMessageNonce,
                                "challenge_id" to json.get("challenge_id").asString,
                                "challenge" to json.get("challenge").asString,
                                "proof" to proof
                            )))
                        }
                        "connected" -> {
                            val sid = json.get("session_id")?.asString
                            val serverChallenge = json.get("server_challenge")?.asString
                            if (sid.isNullOrEmpty() || serverChallenge.isNullOrEmpty() ||
                                tokenSecret.isNullOrEmpty() || tokenId.isNullOrEmpty() ||
                                deviceId.isNullOrEmpty()) {
                                connectFailure = "Authenticated session material is missing"
                                close()
                                return
                            }
                            sessionId = sid
                            sessionSecret = UdpClient.deriveSessionSecret(
                                tokenSecret, connectMessageNonce, serverChallenge, sid
                            )
                            sequence.set(0)
                            authenticated = true
                            Log.d("WebSocketClient", "Server acknowledged authenticated session")
                        }
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    connected = false
                    authenticated = false
                    sessionId = null
                    sessionSecret = null
                    sequence.set(0)
                    Log.d("WebSocketClient", "Connection closed: $reason")
                }

                override fun onError(ex: Exception?) {
                    connected = false
                    authenticated = false
                    sessionId = null
                    sessionSecret = null
                    sequence.set(0)
                    Log.e("WebSocketClient", "Error: ${ex?.message}")
                }
            }

            webSocketClient?.connect()

            var attempts = 0
            while (!authenticated && attempts < 50) {
                delay(100)
                attempts++
            }

            authenticated && connectFailure == null
        } catch (e: Exception) {
            Log.e("WebSocketClient", "Connection failed: ${e.message}")
            false
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                webSocketClient?.close()
                webSocketClient = null
                connected = false
                authenticated = false
                sessionId = null
                sessionSecret = null
                sequence.set(0)
                connectFailure = null
                Log.d("WebSocketClient", "Disconnected")
            } catch (e: Exception) {
                Log.e("WebSocketClient", "Disconnect error: ${e.message}")
            }
        }
    }

    override suspend fun sendInput(inputData: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!authenticated) return@withContext false

            val sid = sessionId ?: return@withContext false
            val secret = sessionSecret ?: return@withContext false
            val next = sequence.getAndIncrement()
            val payload = inputData
            val authenticatedMessage = linkedMapOf<String, Any?>(
                "client_id" to (deviceId ?: clientId), "session_id" to sid, "payload" to payload
            )
            val message = mapOf(
                "type" to "input",
                "client_id" to (deviceId ?: clientId),
                "device_id" to (deviceId ?: clientId),
                "session_id" to sid, "sequence" to next, "data" to payload,
                "mac" to UdpClient.computeSessionMac(secret, next, authenticatedMessage)
            )
            webSocketClient?.send(gson.toJson(message))
            true
        } catch (e: Exception) {
            Log.e("WebSocketClient", "Send input error: ${e.message}")
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
                "client_id" to (deviceId ?: clientId), "session_id" to sid, "payload" to emptyMap<String, Any?>()
            )
            val message = mapOf(
                "type" to "heartbeat",
                "client_id" to (deviceId ?: clientId),
                "device_id" to (deviceId ?: clientId),
                "session_id" to sid, "sequence" to next, "data" to emptyMap<String, Any?>(),
                "mac" to UdpClient.computeSessionMac(secret, next, authenticatedMessage)
            )
            webSocketClient?.send(gson.toJson(message))
            true
        } catch (e: Exception) {
            Log.e("WebSocketClient", "Send heartbeat error: ${e.message}")
            false
        }
    }
}
