package com.songsnap.game.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.songsnap.game.data.Prefs
import com.songsnap.game.data.Song
import com.songsnap.game.data.SpotifyAuth
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PremiumRequiredException : Exception("Spotify Premium wird benötigt")
class TrackUnavailableException : Exception("Song kann nicht abgespielt werden")
class NotConnectedException : Exception("Nicht mit Spotify verbunden")
class PlaybackException(msg: String) : Exception(msg)

/**
 * Spielt exakte Ausschnitte über die Spotify-App ab.
 *
 * Trick für präzise Längen (auch 0,1 s): Die Wiedergabe startet stumm (Medien-Stream gemutet),
 * und nur für genau die Ausschnitt-Dauer wird der Ton freigegeben. Start/Stopp hängen dadurch
 * nicht von der Reaktionszeit der Spotify-App ab.
 */
class SnippetPlayer(context: Context, private val prefs: Prefs) {

    private class Snap(val state: PlayerState, val at: Long, val seq: Long)

    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val mutex = Mutex()

    private var remote: SpotifyAppRemote? = null
    private var subscription: Subscription<PlayerState>? = null

    @Volatile private var lastEvent: Snap? = null
    @Volatile private var eventSeq = 0L
    @Volatile private var muted = false

    val isConnected: Boolean get() = remote?.isConnected == true

    // ------------------------------------------------------------------ Verbindung

    suspend fun connect(activityContext: Context, clientId: String) {
        if (isConnected) return
        remote?.let { runCatching { SpotifyAppRemote.disconnect(it) } }
        remote = null
        val r = withTimeoutOrNull(90_000) {
            suspendCancellableCoroutine<SpotifyAppRemote> { cont ->
                val params = ConnectionParams.Builder(clientId)
                    .setRedirectUri(SpotifyAuth.REDIRECT_URI)
                    .showAuthView(true)
                    .build()
                SpotifyAppRemote.connect(activityContext, params, object : Connector.ConnectionListener {
                    override fun onConnected(appRemote: SpotifyAppRemote) {
                        if (cont.isActive) cont.resume(appRemote)
                    }

                    override fun onFailure(error: Throwable) {
                        if (cont.isActive) cont.resumeWithException(error)
                    }
                })
            }
        } ?: throw PlaybackException("Die Spotify-App reagiert nicht. Öffne sie einmal und versuche es erneut.")
        remote = r
        subscription?.let { runCatching { it.cancel() } }
        subscription = r.playerApi.subscribeToPlayerState()
            .setEventCallback(Subscription.EventCallback<PlayerState> { s ->
                if (s != null) {
                    eventSeq += 1
                    lastEvent = Snap(s, SystemClock.elapsedRealtime(), eventSeq)
                }
            })
    }

    fun release() {
        restoreVolume()
        subscription?.let { runCatching { it.cancel() } }
        subscription = null
        remote?.let { runCatching { SpotifyAppRemote.disconnect(it) } }
        remote = null
    }

    fun isMediaVolumeZero(): Boolean =
        runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 }.getOrDefault(false)

    // ------------------------------------------------------------------ Öffentliche Aktionen

    /** Song laden und (stumm) an die Startposition springen. */
    suspend fun prepare(song: Song, startMs: Long) {
        mutex.withLock { prepareLocked(requireRemote(), song, startMs) }
    }

    /** Spielt [durationMs] ab [startMs]. [onAudible] meldet, wann der Ton hörbar ist. */
    suspend fun playSnippet(song: Song, startMs: Long, durationMs: Long, onAudible: (Boolean) -> Unit) {
        mutex.withLock {
            val r = requireRemote()
            var completed = false
            try {
                val current = currentState(r)
                if (current?.track?.uri != song.uri) prepareLocked(r, song, startMs)

                val useLeadIn = startMs >= LEAD_IN_MS
                val seekPos = if (useLeadIn) startMs - LEAD_IN_MS else startMs

                setMuted(true)
                r.playerApi.seekTo(seekPos).awaitResult()
                delay(60)
                // Ohne Vorlauf (Songanfang) gibt es nichts zu verstecken -> Ton schon vor dem Start an
                if (!useLeadIn) setMuted(false)

                val seq0 = eventSeq
                r.playerApi.resume().awaitResult()
                val snap = pollState(r, 5_000, seq0) { !it.isPaused }
                    ?: throw PlaybackException("Spotify startet die Wiedergabe nicht.")

                fun positionNow(): Long = snap.state.playbackPosition + (SystemClock.elapsedRealtime() - snap.at)

                // Das hörbare Fenster läuft auf einem Hintergrund-Thread, damit UI-Animationen
                // das Timing nicht verzögern. (Die SDK-Aufrufe bleiben auf dem Haupt-Thread.)
                withContext(Dispatchers.Default) {
                    if (useLeadIn) {
                        val wait = startMs - positionNow()
                        if (wait > 0) delay(wait)
                        setMuted(false)
                        post { onAudible(true) }
                        delay(durationMs)
                    } else {
                        post { onAudible(true) }
                        val alreadyPlayed = (positionNow() - startMs).coerceAtLeast(0L)
                        delay((durationMs - alreadyPlayed).coerceAtLeast(0L))
                    }
                    setMuted(true)
                }
                post { onAudible(false) }
                r.playerApi.pause().awaitResult()
                delay(220)
                completed = true
            } finally {
                withContext(NonCancellable) {
                    if (!completed) {
                        setMuted(true)
                        post { onAudible(false) }
                        try {
                            withTimeoutOrNull(1_500) { r.playerApi.pause().awaitResult() }
                        } catch (e: Exception) {
                            // ignorieren
                        }
                        delay(200)
                    }
                    setMuted(false)
                }
            }
        }
    }

    /** Nach der Runde: Song hörbar ab der Startposition weiterspielen. */
    suspend fun reveal(song: Song, startMs: Long) {
        mutex.withLock {
            val r = requireRemote()
            setMuted(false)
            val current = currentState(r)
            if (current?.track?.uri != song.uri) {
                val seq0 = eventSeq
                r.playerApi.play(song.uri).awaitResult()
                pollState(r, 6_000, seq0) { it.track?.uri == song.uri }
            }
            r.playerApi.seekTo(startMs).awaitResult()
            r.playerApi.resume().awaitResult()
        }
    }

    suspend fun stop() {
        mutex.withLock {
            val r = remote
            if (r != null && r.isConnected) {
                try {
                    withTimeoutOrNull(1_500) { r.playerApi.pause().awaitResult() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // ignorieren
                }
            }
            setMuted(false)
        }
    }

    // ------------------------------------------------------------------ Intern

    private suspend fun prepareLocked(r: SpotifyAppRemote, song: Song, startMs: Long) {
        setMuted(true)
        try {
            try {
                r.playerApi.setShuffle(false).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // nicht kritisch
            }
            val seq0 = eventSeq
            r.playerApi.play(song.uri).awaitResult()
            val loaded = pollState(r, 8_000, seq0) { it.track?.uri == song.uri }
            if (loaded == null) {
                val cur = currentState(r)
                val canSeek = cur?.playbackRestrictions?.canSeek ?: true
                if (cur?.track != null && !canSeek) throw PremiumRequiredException()
                throw TrackUnavailableException()
            }
            r.playerApi.pause().awaitResult()
            pollState(r, 2_500, -1) { it.isPaused }
            val seekPos = if (startMs >= LEAD_IN_MS) startMs - LEAD_IN_MS else startMs
            r.playerApi.seekTo(seekPos).awaitResult()
            delay(150)
        } finally {
            withContext(NonCancellable) {
                try {
                    r.playerApi.pause()
                } catch (e: Exception) {
                    // ignorieren
                }
                delay(250)
                setMuted(false)
            }
        }
    }

    private fun requireRemote(): SpotifyAppRemote {
        val r = remote
        if (r == null || !r.isConnected) throw NotConnectedException()
        return r
    }

    private suspend fun currentState(r: SpotifyAppRemote): PlayerState? =
        try {
            withTimeoutOrNull(2_000) { r.playerApi.playerState.awaitResult() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Wartet auf einen Player-Zustand (Push-Events + Abfragen). */
    private suspend fun pollState(
        r: SpotifyAppRemote,
        timeoutMs: Long,
        minSeq: Long,
        predicate: (PlayerState) -> Boolean,
    ): Snap? = withTimeoutOrNull(timeoutMs) {
        var found: Snap? = null
        while (found == null) {
            val ev = lastEvent
            if (ev != null && ev.seq > minSeq && predicate(ev.state)) {
                found = ev
            } else {
                val s = try {
                    r.playerApi.playerState.awaitResult()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (s != null && predicate(s)) {
                    found = Snap(s, SystemClock.elapsedRealtime(), -1)
                } else {
                    delay(25)
                }
            }
        }
        found
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    private fun setMuted(mute: Boolean) {
        if (muted == mute) return
        try {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0,
            )
            muted = mute
            prefs.mutedByApp = mute
        } catch (e: Exception) {
            // Manche Geräte erlauben das Stummschalten nicht – dann eben ohne
        }
    }

    /** Falls die App beim letzten Mal mitten im Ausschnitt beendet wurde. */
    fun restoreVolume() {
        if (muted || prefs.mutedByApp) {
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: Exception) {
                // ignorieren
            }
            muted = false
            prefs.mutedByApp = false
        }
    }

    companion object {
        /** So viel früher wird (stumm) gestartet, damit der Ton exakt an der Startposition einsetzt. */
        const val LEAD_IN_MS = 450L
    }
}

private suspend fun <T> CallResult<T>.awaitRaw(): T = suspendCancellableCoroutine { cont ->
    setResultCallback(CallResult.ResultCallback<T> { data -> if (cont.isActive) cont.resume(data) })
    setErrorCallback { t -> if (cont.isActive) cont.resumeWithException(t) }
}

/** Wartet auf das Ergebnis eines App-Remote-Aufrufs; hängt nie länger als [timeoutMs]. */
private suspend fun <T> CallResult<T>.awaitResult(timeoutMs: Long = 6_000): T {
    var done = false
    var value: T? = null
    withTimeoutOrNull(timeoutMs) {
        value = awaitRaw()
        done = true
    }
    if (!done) throw PlaybackException("Die Spotify-App antwortet nicht. Ist sie geöffnet und eingeloggt?")
    @Suppress("UNCHECKED_CAST")
    return value as T
}
