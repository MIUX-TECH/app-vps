package com.botdash.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File
import kotlin.concurrent.thread

/**
 * Foreground service yang membuka SSH local port-forward ke VPS:
 *   127.0.0.1:<localPort> (di HP)  ->  127.0.0.1:<remotePort> (di VPS)
 *
 * Ini setara dengan menjalankan:
 *   ssh -L <localPort>:127.0.0.1:<remotePort> -i key.pem user@host -N
 * tapi terintegrasi langsung di dalam app, tidak perlu Termux terpisah.
 *
 * Kenapa harus foreground service (bukan background biasa): Android akan
 * membunuh proses background setelah beberapa saat jika tidak ada
 * foreground service dengan notifikasi -- tunnel akan putus sendiri kalau
 * ini tidak dijalankan sebagai foreground service.
 */
class TunnelService : Service() {

    companion object {
        const val CHANNEL_ID = "tunnel_channel"
        const val NOTIF_ID = 1001
        var isRunning = false
            private set
        var lastError: String? = null
            private set
    }

    private var session: Session? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Menyambungkan ke VPS..."))

        thread {
            try {
                val prefs = ConfigStore(this)
                val host = prefs.getHost()
                val port = prefs.getSshPort()
                val username = prefs.getUsername()
                val localPort = prefs.getLocalPort()
                val remotePort = prefs.getRemotePort()
                val keyBytes = KeyStore(this).readKey()

                if (host.isBlank() || username.isBlank() || keyBytes == null) {
                    lastError = "Konfigurasi belum lengkap. Isi dulu di halaman Pengaturan."
                    updateNotification("Gagal: konfigurasi belum lengkap")
                    stopSelf()
                    return@thread
                }

                val jsch = JSch()
                // JSch butuh key dalam bentuk byte array; kita simpan tanpa passphrase
                // di sini demi kesederhanaan -- kalau key.pem kamu ada passphrase,
                // isi juga di halaman Pengaturan (field passphrase, opsional).
                val passphrase = prefs.getKeyPassphrase()
                if (passphrase.isNotBlank()) {
                    jsch.addIdentity("vps-key", keyBytes, null, passphrase.toByteArray())
                } else {
                    jsch.addIdentity("vps-key", keyBytes, null, null)
                }

                val newSession = jsch.getSession(username, host, port)
                newSession.setConfig("StrictHostKeyChecking", "no")
                newSession.timeout = 15000
                newSession.connect(15000)
                newSession.setPortForwardingL(localPort, "127.0.0.1", remotePort)

                session = newSession
                isRunning = true
                lastError = null
                updateNotification("Terhubung — tunnel aktif di port $localPort")

                // Jaga service tetap hidup selama session konek
                while (newSession.isConnected) {
                    Thread.sleep(2000)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Error tidak diketahui"
                updateNotification("Gagal konek: ${e.message}")
            } finally {
                isRunning = false
                session?.disconnect()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        session?.disconnect()
        session = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Tunnel VPS", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bot Dashboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
