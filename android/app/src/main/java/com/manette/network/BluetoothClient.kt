package com.manette.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class BluetoothClient(
    private val port: Int,
    private val deviceId: String? = null,
    private val tokenId: String? = null,
    private val tokenSecret: String? = null
) : NetworkClient {

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val gson = Gson()
    private val clientId = "android_${System.currentTimeMillis()}"
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Standard SPP UUID
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (bluetoothAdapter == null) {
                Log.w("BluetoothClient", "Bluetooth not supported; RFCOMM path is not implemented")
                return@withContext false
            }
            Log.w("BluetoothClient", "RFCOMM handshake is not implemented; refusing unauthenticated access")
            false
        } catch (e: Exception) {
            Log.e("BluetoothClient", "Connection failed: ${e.message}")
            false
        }
    }

    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.w("BluetoothClient", "RFCOMM device connection is not implemented; refusing unauthenticated access")
            false
        } catch (e: Exception) {
            Log.e("BluetoothClient", "Connection failed: ${e.message}")
            false
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                outputStream?.close()
                socket?.close()
                outputStream = null
                socket = null
                Log.d("BluetoothClient", "Disconnected")
            } catch (e: Exception) {
                Log.e("BluetoothClient", "Disconnect error: ${e.message}")
            }
        }
    }

    override suspend fun sendInput(inputData: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.w("BluetoothClient", "RFCOMM is not implemented; input refused")
            false
        } catch (e: Exception) {
            Log.e("BluetoothClient", "Send input error: ${e.message}")
            false
        }
    }

    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.w("BluetoothClient", "RFCOMM is not implemented; heartbeat refused")
            false
        } catch (e: Exception) {
            Log.e("BluetoothClient", "Send heartbeat error: ${e.message}")
            false
        }
    }

    private fun sendMessage(message: Map<String, Any>) {
        val json = gson.toJson(message)
        outputStream?.write((json + "\n").toByteArray())
        outputStream?.flush()
    }
}
