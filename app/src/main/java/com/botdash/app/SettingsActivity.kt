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
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                contentResolver.openInputStream(uri)?.use { input ->
                    pickedKeyBytes = input.readBytes()
                    findViewById<TextView>(R.id.keyStatus).text =
                        "Key .pem: file dipilih (${pickedKeyBytes?.size ?: 0} bytes) — klik Simpan untuk enkripsi & simpan"
                }
            }
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
        findViewById<EditText>(R.id.inputDashboardToken).setText(config.getDashboardToken())
        findViewById<EditText>(R.id.inputKeyPassphrase).setText(config.getKeyPassphrase())

        if (keyStore.hasKey()) {
            findViewById<TextView>(R.id.keyStatus).text = "Key .pem: sudah tersimpan (terenkripsi)"
        }

        findViewById<Button>(R.id.btnPickKey).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            pickKeyLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            config.setHost(findViewById<EditText>(R.id.inputHost).text.toString().trim())
            config.setUsername(findViewById<EditText>(R.id.inputUsername).text.toString().trim())
            config.setSshPort(
                findViewById<EditText>(R.id.inputSshPort).text.toString().toIntOrNull() ?: 22
            )
            config.setRemotePort(
                findViewById<EditText>(R.id.inputRemotePort).text.toString().toIntOrNull() ?: 8080
            )
            config.setDashboardToken(findViewById<EditText>(R.id.inputDashboardToken).text.toString().trim())
            config.setKeyPassphrase(findViewById<EditText>(R.id.inputKeyPassphrase).text.toString())

            pickedKeyBytes?.let { bytes ->
                keyStore.saveKey(bytes)
                Toast.makeText(this, "Key berhasil dienkripsi & disimpan", Toast.LENGTH_SHORT).show()
            }

            Toast.makeText(this, "Pengaturan disimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
