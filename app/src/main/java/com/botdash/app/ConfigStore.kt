package com.botdash.app

import android.content.Context

/**
 * Pengaturan koneksi biasa (bukan rahasia) -- disimpan di SharedPreferences
 * normal. Token dashboard dan passphrase key TETAP dianggap sensitif secara
 * ringan, tapi karena keduanya cuma berguna kalau penyerang SUDAH punya akses
 * ke HP/app ini juga, risikonya jauh lebih rendah dibanding key .pem itu
 * sendiri (yang disimpan terenkripsi terpisah lewat KeyStore.kt).
 */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("bot_dashboard_config", Context.MODE_PRIVATE)

    fun getHost(): String = prefs.getString("host", "") ?: ""
    fun setHost(v: String) = prefs.edit().putString("host", v).apply()

    fun getUsername(): String = prefs.getString("username", "admin") ?: "admin"
    fun setUsername(v: String) = prefs.edit().putString("username", v).apply()

    fun getSshPort(): Int = prefs.getInt("ssh_port", 22)
    fun setSshPort(v: Int) = prefs.edit().putInt("ssh_port", v).apply()

    fun getRemotePort(): Int = prefs.getInt("remote_port", 8080)
    fun setRemotePort(v: Int) = prefs.edit().putInt("remote_port", v).apply()

    fun getLocalPort(): Int = prefs.getInt("local_port", 8080)
    fun setLocalPort(v: Int) = prefs.edit().putInt("local_port", v).apply()

    fun getDashboardToken(): String = prefs.getString("dashboard_token", "") ?: ""
    fun setDashboardToken(v: String) = prefs.edit().putString("dashboard_token", v).apply()

    fun getKeyPassphrase(): String = prefs.getString("key_passphrase", "") ?: ""
    fun setKeyPassphrase(v: String) = prefs.edit().putString("key_passphrase", v).apply()

    fun isConfigured(): Boolean = getHost().isNotBlank() && getUsername().isNotBlank()
}
