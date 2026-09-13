package com.botdash.app

import android.content.Context

/** Non-secret connection settings. Secret values are delegated to SecureStore. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("bot_dashboard_config", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

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

    fun getDashboardToken(): String = secure.get("dashboard_token")
    fun setDashboardToken(v: String) = secure.put("dashboard_token", v)

    fun getKeyPassphrase(): String = secure.get("key_passphrase")
    fun setKeyPassphrase(v: String) = secure.put("key_passphrase", v)

    fun getHostFingerprint(): String = prefs.getString("host_fingerprint", "") ?: ""
    fun setHostFingerprint(v: String) = prefs.edit().putString("host_fingerprint", v).apply()

    fun isConfigured(): Boolean = getHost().isNotBlank() && getUsername().isNotBlank()
}
