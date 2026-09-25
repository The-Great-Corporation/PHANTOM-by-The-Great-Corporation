package com.manette.pairing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun save(credentials: PairingCredentials)
    fun load(): PairingCredentials?
    fun clear()
}

class AndroidKeystoreCredentialStore(private val context: Context) : CredentialStore {
    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "phantom-pairing-v1"
        const val FILE_NAME = "phantom_pairing.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    override fun save(credentials: PairingCredentials) {
        require(credentials.isUsableForReconnect()) { "Cannot persist invalid pairing credentials" }
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.iv + cipher.doFinal(credentials.toWire().toByteArray(Charsets.UTF_8))
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { it.write(encrypted) }
    }

    override fun load(): PairingCredentials? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            val encrypted = file.readBytes()
            if (encrypted.size <= 12) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            PairingPayloadParser.parse(
                String(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)), Charsets.UTF_8),
                ignoreExpiry = true
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun clear() {
        context.deleteFile(FILE_NAME)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }

    private fun PairingCredentials.toWire(): String = com.google.gson.Gson().toJson(
        PairingPayloadDocument("phantom-pairing", 1, server, port, deviceId, tokenId, tokenSecret, expiresAtEpochSeconds)
    )
}

class InMemoryCredentialStore : CredentialStore {
    private var value: PairingCredentials? = null
    override fun save(credentials: PairingCredentials) { value = credentials }
    override fun load(): PairingCredentials? = value
    override fun clear() { value = null }
}
