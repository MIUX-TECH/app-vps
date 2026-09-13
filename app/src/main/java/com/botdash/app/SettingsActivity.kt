package com.botdash.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var config: ConfigStore
    private lateinit var keyStore: KeyStore
    private var pickedKeyBytes: ByteArray? = null

    private val pickKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri: Uri = result.data?.data ?: return@registerForActivityResult
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("File tidak bisa dibaca")
            require(bytes.isNotEmpty()) { "File key kosong" }
            pickedKeyBytes = bytes
            findViewById<TextView>(R.id.keyStatus).text =
                "Key .pem: file dipilih (${bytes.size} bytes) — klik Simpan"
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca key: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        config = ConfigStore(this)
        keyStore = KeyStore(this)

        findViewById<EditText>(R.id.inputHost).setText(config.getHost())
        findViewById<EditText>(R.id.inputUsername).setText(config.getUsername())
        findViewById<EditText>(R.id.inputSshPort).setText(config.getSshPort().toString())
        findViewById<EditText>(R.id.inputRemotePort).setText(config.getRemotePort().toString())
        findViewById<EditText>(R.id.inputLocalPort).setText(config.getLocalPort().toString())
        findViewById<EditText>(R.id.inputDashboardToken).setText(config.getDashboardToken())
        findViewById<EditText>(R.id.inputKeyPassphrase).setText(config.getKeyPassphrase())
        findViewById<EditText>(R.id.inputFingerprint).setText(config.getHostFingerprint())

        findViewById<TextView>(R.id.keyStatus).text = if (keyStore.hasKey()) {
            "Key .pem: sudah tersimpan (terenkripsi)"
        } else {
            "Key .pem: belum dipilih"
        }

        findViewById<Button>(R.id.btnPickKey).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "text/plain", "*/*"))
            }
            pickKeyLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
    }

    private fun save() {
        val host = findViewById<EditText>(R.id.inputHost).text.toString().trim()
        val username = findViewById<EditText>(R.id.inputUsername).text.toString().trim()
        val sshPort = findViewById<EditText>(R.id.inputSshPort).text.toString().toIntOrNull() ?: 22
        val remotePort = findViewById<EditText>(R.id.inputRemotePort).text.toString().toIntOrNull() ?: 8080
        val localPort = findViewById<EditText>(R.id.inputLocalPort).text.toString().toIntOrNull() ?: 8080
        val token = findViewById<EditText>(R.id.inputDashboardToken).text.toString().trim()
        val passphrase = findViewById<EditText>(R.id.inputKeyPassphrase).text.toString()
        val fingerprint = findViewById<EditText>(R.id.inputFingerprint).text.toString().trim()

        if (host.isBlank() || username.isBlank()) {
            Toast.makeText(this, "Host dan username wajib diisi", Toast.LENGTH_LONG).show()
            return
        }
        if (sshPort !in 1..65535 || remotePort !in 1..65535 || localPort !in 1..65535) {
            Toast.makeText(this, "Semua port harus 1–65535", Toast.LENGTH_LONG).show()
            return
        }

        try {
            config.setHost(host)
            config.setUsername(username)
            config.setSshPort(sshPort)
            config.setRemotePort(remotePort)
            config.setLocalPort(localPort)
            config.setDashboardToken(token)
            config.setKeyPassphrase(passphrase)
            config.setHostFingerprint(fingerprint)

            pickedKeyBytes?.let { keyStore.saveKey(it) }
            Toast.makeText(this, "Pengaturan disimpan", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
