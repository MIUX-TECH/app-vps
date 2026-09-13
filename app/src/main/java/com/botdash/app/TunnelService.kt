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
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one SSH local port-forward for the dashboard. */
class TunnelService : Service() {
    companion object {
        const val ACTION_CONNECT = "com.botdash.app.CONNECT"
        const val ACTION_DISCONNECT = "com.botdash.app.DISCONNECT"
        const val CHANNEL_ID = "tunnel_channel"
        const val NOTIF_ID = 1001

        @Volatile var isRunning = false
            private set
        @Volatile var isConnecting = false
            private set
        @Volatile var lastError: String? = null
        @Volatile var lastFingerprint: String? = null
            private set
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val stopping = AtomicBoolean(false)
    private var session: Session? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("Menyambungkan ke VPS..."))
        if (isConnecting || isRunning) return START_NOT_STICKY

        stopping.set(false)
        lastError = null
        isConnecting = true
        executor.execute { connectTunnel() }
        return START_NOT_STICKY
    }

    private fun connectTunnel() {
        val config = ConfigStore(this)
        val keyBytes = KeyStore(this).readKey()
        var connectedSession: Session? = null
        try {
            val host = config.getHost()
            val username = config.getUsername()
            val sshPort = config.getSshPort()
            val localPort = config.getLocalPort()
            val remotePort = config.getRemotePort()
            val passphrase = config.getKeyPassphrase()

            validatePort(sshPort, "SSH")
            validatePort(localPort, "local")
            validatePort(remotePort, "remote")
            require(host.isNotBlank()) { "Host VPS belum diisi" }
            require(username.isNotBlank()) { "Username SSH belum diisi" }
            require(keyBytes != null) { "Key .pem belum dipilih" }

            val jsch = JSch()
            val knownHosts = File(filesDir, "known_hosts")
            if (knownHosts.exists()) {
                jsch.setKnownHosts(knownHosts.absolutePath)
            }

            if (passphrase.isNotBlank()) {
                jsch.addIdentity("vps-key", keyBytes, null, passphrase.toByteArray(Charsets.UTF_8))
            } else {
                jsch.addIdentity("vps-key", keyBytes, null, null)
            }

            val newSession = jsch.getSession(username, host, sshPort)
            val hostAlias = if (sshPort == 22) host else "[$host]:$sshPort"
            newSession.setHostKeyAlias(hostAlias)
            newSession.timeout = 15_000
            newSession.serverAliveInterval = 10_000
            newSession.serverAliveCountMax = 3

            val knownForAlias = jsch.hostKeyRepository.getHostKey(hostAlias, null)
            val hasKnownHost = !knownForAlias.isNullOrEmpty()
            if (!hasKnownHost) {
                newSession.setConfig("StrictHostKeyChecking", "no")
            } else {
                newSession.setConfig("StrictHostKeyChecking", "yes")
            }

            newSession.connect(15_000)
            val hostKey = newSession.hostKey ?: error("SSH server tidak mengirim host key")
            val fingerprint = sha256Fingerprint(hostKey.key)
            lastFingerprint = fingerprint

            val configuredFingerprint = config.getHostFingerprint()
            if (configuredFingerprint.isNotBlank() &&
                !fingerprint.equals(normalizeFingerprint(configuredFingerprint), ignoreCase = true)
            ) {
                newSession.disconnect()
                error("Fingerprint SSH tidak cocok. Diterima: $fingerprint")
            }

            if (!hasKnownHost) {
                newSession.hostKeyRepository.add(hostKey, null)
                config.setHostFingerprint(fingerprint)
            }

            newSession.setPortForwardingL(localPort, "127.0.0.1", remotePort)
            connectedSession = newSession
            session = newSession
            isRunning = true
            isConnecting = false
            lastError = null
            updateNotification("Terhubung — tunnel localhost:$localPort aktif")

            while (!stopping.get() && newSession.isConnected) {
                newSession.sendKeepAliveMsg()
                Thread.sleep(8_000)
            }
        } catch (e: Exception) {
            isConnecting = false
            isRunning = false
            if (!stopping.get()) {
                lastError = cleanError(e)
                updateNotification("Gagal: ${lastError}")
            }
        } finally {
            try { connectedSession?.disconnect() } catch (_: Exception) { }
            if (session === connectedSession) session = null
            isRunning = false
            isConnecting = false
            if (!stopping.get()) {
                updateNotification("Tunnel berhenti")
                stopSelf()
            }
        }
    }

    private fun stopTunnel() {
        stopping.set(true)
        try { session?.disconnect() } catch (_: Exception) { }
        session = null
        isRunning = false
        isConnecting = false
    }

    override fun onDestroy() {
        stopTunnel()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun validatePort(port: Int, name: String) {
        require(port in 1..65535) { "Port $name tidak valid: $port" }
    }

    private fun normalizeFingerprint(value: String): String =
        value.trim().removePrefix("SHA256:")

    private fun sha256Fingerprint(base64Key: String): String {
        val raw = Base64.getDecoder().decode(base64Key)
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    private fun cleanError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return when {
            message.isNotEmpty() -> message.take(220)
            error is JSchException -> "SSH error"
            else -> error.javaClass.simpleName
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SSH Tunnel", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Status koneksi SSH Bot Dashboard" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bot Dashboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }
}
