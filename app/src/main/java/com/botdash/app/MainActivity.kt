package com.botdash.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var config: ConfigStore
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pollingForConnection = false

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* lanjut apapun hasilnya, notifikasi cuma nice-to-have */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = ConfigStore(this)
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            connectAndLoad()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (TunnelService.isRunning) {
            statusText.text = "Terhubung"
            loadDashboard()
        }
    }

    private fun connectAndLoad() {
        if (!config.isConfigured()) {
            Toast.makeText(this, "Isi dulu Pengaturan (host, username, key)", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        statusText.text = "Menyambungkan..."
        val intent = Intent(this, TunnelService::class.java)
        ContextCompat.startForegroundService(this, intent)

        pollingForConnection = true
        pollConnectionStatus()
    }

    private fun pollConnectionStatus() {
        if (!pollingForConnection) return
        if (TunnelService.isRunning) {
            statusText.text = "Terhubung"
            pollingForConnection = false
            loadDashboard()
        } else if (TunnelService.lastError != null) {
            statusText.text = "Gagal: ${TunnelService.lastError}"
            pollingForConnection = false
        } else {
            handler.postDelayed({ pollConnectionStatus() }, 1000)
        }
    }

    private fun loadDashboard() {
        val port = config.getLocalPort()
        val token = config.getDashboardToken()
        webView.loadUrl("http://127.0.0.1:$port/?token=$token")
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingForConnection = false
    }
}
