package com.songsnap.game.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class TokenResult(val accessToken: String, val refreshToken: String?, val expiresIn: Int)

/** Authorization Code Flow mit PKCE – kein Client-Secret nötig. */
object SpotifyAuth {
    const val REDIRECT_URI = "songsnap://callback"
    const val LOOPBACK_PORT = 43821
    const val LOOPBACK_REDIRECT_URI = "http://127.0.0.1:43821/callback"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"

    val SCOPES = listOf(
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
        "user-read-private",
        "app-remote-control",
        "user-read-playback-state",
        "user-modify-playback-state",
    )

    private val random = SecureRandom()

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun newVerifier(): String = ByteArray(64).also { random.nextBytes(it) }.let(::b64url)
    fun newState(): String = ByteArray(16).also { random.nextBytes(it) }.let(::b64url)

    fun challenge(verifier: String): String =
        b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun buildAuthUrl(clientId: String, redirect: String, challenge: String, state: String): Uri =
        Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirect)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()

    suspend fun exchangeCode(clientId: String, code: String, redirect: String, verifier: String): TokenResult =
        withContext(Dispatchers.IO) {
            val (status, body) = Http.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirect,
                    "client_id" to clientId,
                    "code_verifier" to verifier,
                ),
            )
            if (status !in 200..299) throw ApiException(status, body)
            parseToken(body)
        }

    suspend fun refresh(clientId: String, refreshToken: String): TokenResult =
        withContext(Dispatchers.IO) {
            val (status, body) = Http.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to clientId,
                ),
            )
            if (status !in 200..299) throw ApiException(status, body)
            parseToken(body)
        }

    private fun parseToken(body: String): TokenResult {
        val j = JSONObject(body)
        val rt = j.optString("refresh_token")
        return TokenResult(
            accessToken = j.getString("access_token"),
            refreshToken = rt.takeIf { it.isNotBlank() && it != "null" },
            expiresIn = j.optInt("expires_in", 3600),
        )
    }
}

/**
 * Alternativer Login: Ein winziger HTTP-Server auf 127.0.0.1 nimmt die Spotify-Weiterleitung
 * entgegen (Spotify erlaubt Loopback-Adressen ausdrücklich als Redirect-URI).
 */
object LoopbackReceiver {
    suspend fun awaitRedirect(port: Int, timeoutMs: Int): Uri = withContext(Dispatchers.IO) {
        ServerSocket().use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
            server.soTimeout = timeoutMs
            var result: Uri? = null
            while (result == null) {
                server.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLine = reader.readLine() ?: ""
                    val path = requestLine.split(" ").getOrNull(1) ?: "/"
                    val isCallback = path.startsWith("/callback")
                    val html = if (isCallback) SUCCESS_HTML else "<html><body></body></html>"
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val out = socket.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.UTF_8),
                    )
                    out.write(bytes)
                    out.flush()
                    if (isCallback) result = Uri.parse("http://127.0.0.1:$port$path")
                }
            }
            result!!
        }
    }

    private const val BACK_LINK = "intent://callback?done=1#Intent;scheme=songsnap;package=com.songsnap.game;end"

    private val SUCCESS_HTML = """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
        body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
        background:#07070D;color:#fff;font-family:sans-serif;text-align:center}
        a{margin-top:28px;padding:16px 34px;border-radius:30px;background:#1ED760;color:#000;
        font-weight:800;text-decoration:none;font-size:18px}
        </style></head><body>
        <div style="font-size:64px">&#9989;</div><h2>Login erfolgreich!</h2>
        <a href="$BACK_LINK">Zur&uuml;ck zu SongSnap</a>
        <script>setTimeout(function(){location.href="$BACK_LINK";},400);</script>
        </body></html>
    """.trimIndent()
}

object Http {
    fun get(url: String, token: String): Triple<Int, String, Long?> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        return try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Triple(code, body, c.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
        } finally {
            c.disconnect()
        }
    }

    fun postForm(url: String, params: Map<String, String>): Pair<Int, String> {
        val form = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        return try {
            c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to body
        } finally {
            c.disconnect()
        }
    }
}
