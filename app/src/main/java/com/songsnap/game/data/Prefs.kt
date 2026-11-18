package com.songsnap.game.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("songsnap", Context.MODE_PRIVATE)

    private fun str(key: String): String? = sp.getString(key, null)
    private fun put(key: String, value: String?) = sp.edit().putString(key, value).apply()

    var clientId: String
        get() = str("client_id") ?: ""
        set(v) = put("client_id", v.trim())

    var accessToken: String?
        get() = str("access_token")
        set(v) = put("access_token", v)

    var refreshToken: String?
        get() = str("refresh_token")
        set(v) = put("refresh_token", v)

    var expiresAt: Long
        get() = sp.getLong("expires_at", 0L)
        set(v) = sp.edit().putLong("expires_at", v).apply()

    var codeVerifier: String
        get() = str("code_verifier") ?: ""
        set(v) = put("code_verifier", v)

    var authState: String
        get() = str("auth_state") ?: ""
        set(v) = put("auth_state", v)

    var authRedirect: String
        get() = str("auth_redirect") ?: ""
        set(v) = put("auth_redirect", v)

    var mutedByApp: Boolean
        get() = sp.getBoolean("muted_by_app", false)
        set(v) = sp.edit().putBoolean("muted_by_app", v).apply()

    var mode: String
        get() = str("mode") ?: "BEGINNING"
        set(v) = put("mode", v)

    var rounds: Int
        get() = sp.getInt("rounds", 10)
        set(v) = sp.edit().putInt("rounds", v).apply()

    fun highscore(key: String): Int = sp.getInt("hs_$key", 0)
    fun setHighscore(key: String, value: Int) = sp.edit().putInt("hs_$key", value).apply()

    fun clearAuth() {
        sp.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .apply()
    }
}
