package com.botdash.app

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Menyimpan isi file .pem TERENKRIPSI di storage internal app (bukan
 * eksternal/shared storage), pakai Android Keystore lewat Jetpack Security.
 *
 * Ini artinya:
 *  - File cuma bisa dibaca oleh app ini sendiri (sandboxing Android + enkripsi)
 *  - Kunci enkripsinya sendiri disimpan di hardware-backed Android Keystore,
 *    bukan hardcoded di kode atau di file biasa
 *  - Key TIDAK PERNAH dikirim ke server mana pun, termasuk ke pembuat app ini
 */
class KeyStore(private val context: Context) {

    private fun getMasterKey(): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun encryptedKeyFile(): File {
        return File(context.filesDir, "vps_key.pem.enc")
    }

    fun saveKey(rawPemBytes: ByteArray) {
        val target = encryptedKeyFile()
        if (target.exists()) target.delete()

        val encryptedFile = EncryptedFile.Builder(
            context, target, getMasterKey(), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { it.write(rawPemBytes) }
    }

    fun readKey(): ByteArray? {
        val target = encryptedKeyFile()
        if (!target.exists()) return null

        val encryptedFile = EncryptedFile.Builder(
            context, target, getMasterKey(), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return encryptedFile.openFileInput().use { it.readBytes() }
    }

    fun hasKey(): Boolean = encryptedKeyFile().exists()

    fun deleteKey(): Boolean = encryptedKeyFile().delete()
}
