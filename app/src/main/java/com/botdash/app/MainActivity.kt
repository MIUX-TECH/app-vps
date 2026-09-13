package com.botdash.app

import android.content.Intent
import android.net.Uri
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
    private lateinit var connectButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var pollingForConnection = false

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = ConfigStore(this)
        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.btnConnect)

        configureWebView()

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        connectButton.setOnClickListener {
            if (TunnelService.isRunning || TunnelService.isConnecting) disconnect()
            else connectAndLoad()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiFromTunnelState()
        if (TunnelService.isRunning) loadDashboard()
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.clearHistory()
            }
        }
    }

    private fun connectAndLoad() {
        if (!config.isConfigured() || !KeyStore(this).hasKey()) {
            Toast.makeText(this, "Isi host, username, dan key .pem di Pengaturan", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        TunnelService.lastError = null
        statusText.text = "Menyambungkan..."
        connectButton.text = "Putus"
        val intent = Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_CONNECT)
        ContextCompat.startForegroundService(this, intent)
        pollingForConnection = true
        handler.post { pollConnectionStatus() }
    }

    private fun disconnect() {
        pollingForConnection = false
        startService(Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_DISCONNECT))
        statusText.text = "Terputus"
        connectButton.text = "Konek"
        webView.loadUrl("about:blank")
    }

    private fun pollConnectionStatus() {
        if (!pollingForConnection) return
        when {
            TunnelService.isRunning -> {
                statusText.text = "Terhubung"
                connectButton.text = "Putus"
                pollingForConnection = false
                loadDashboard()
            }
            TunnelService.lastError != null -> {
                statusText.text = "Gagal: ${TunnelService.lastError}"
                connectButton.text = "Konek"
                pollingForConnection = false
            }
            TunnelService.isConnecting -> {
                statusText.text = "Menyambungkan..."
                handler.postDelayed({ pollConnectionStatus() }, 500)
            }
            else -> {
                statusText.text = "Terputus"
                connectButton.text = "Konek"
                pollingForConnection = false
            }
        }
    }

    private fun updateUiFromTunnelState() {
        when {
            TunnelService.isRunning -> {
                statusText.text = "Terhubung"
                connectButton.text = "Putus"
            }
            TunnelService.isConnecting -> {
                statusText.text = "Menyambungkan..."
                connectButton.text = "Putus"
            }
            TunnelService.lastError != null -> {
                statusText.text = "Gagal: ${TunnelService.lastError}"
                connectButton.text = "Konek"
            }
            else -> {
                statusText.text = "Belum terhubung"
                connectButton.text = "Konek"
            }
        }
    }

    private fun loadDashboard() {
        val port = config.getLocalPort()
        val token = config.getDashboardToken()
        val builder = Uri.Builder()
            .scheme("http")
            .encodedAuthority("localhost:$port")
            .path("/")
        if (token.isNotEmpty()) builder.appendQueryParameter("token", token)
        webView.loadUrl(builder.build().toString())
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pollingForConnection = false
        super.onDestroy()
    }
}
