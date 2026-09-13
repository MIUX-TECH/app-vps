package com.botdash.app

import android.content.Context
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Encrypts the PEM bytes with an AES-256 key held by Android Keystore. */
class KeyStore(private val context: Context) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "bot_dashboard_pem_key"
        private const val FILE_NAME = "vps_key.pem.enc"
        private const val IV_SIZE = 12
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encryptedKeyFile(): File = File(context.filesDir, FILE_NAME)

    fun saveKey(rawPemBytes: ByteArray) {
        require(rawPemBytes.isNotEmpty()) { "Key file is empty" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(rawPemBytes)
        val output = ByteArray(IV_SIZE + encrypted.size)
        System.arraycopy(iv, 0, output, 0, IV_SIZE)
        System.arraycopy(encrypted, 0, output, IV_SIZE, encrypted.size)
        encryptedKeyFile().writeBytes(output)
    }

    fun readKey(): ByteArray? {
        val file = encryptedKeyFile()
        if (!file.exists()) return null
        return try {
            val packed = file.readBytes()
            require(packed.size > IV_SIZE) { "Encrypted key is invalid" }
            val iv = packed.copyOfRange(0, IV_SIZE)
            val encrypted = packed.copyOfRange(IV_SIZE, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    fun hasKey(): Boolean = encryptedKeyFile().exists()
    fun deleteKey(): Boolean = encryptedKeyFile().delete()
}
