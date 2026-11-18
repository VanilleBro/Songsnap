package com.songsnap.game

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.songsnap.game.data.ApiException
import com.songsnap.game.data.AuthRequiredException
import com.songsnap.game.data.LoopbackReceiver
import com.songsnap.game.data.Playlist
import com.songsnap.game.data.Prefs
import com.songsnap.game.data.Song
import com.songsnap.game.data.SpotifyApi
import com.songsnap.game.data.SpotifyAuth
import com.songsnap.game.data.TokenResult
import com.songsnap.game.data.UserMessageException
import com.songsnap.game.data.norm
import com.songsnap.game.player.NotConnectedException
import com.songsnap.game.player.PlaybackException
import com.songsnap.game.player.PremiumRequiredException
import com.songsnap.game.player.SnippetPlayer
import com.songsnap.game.player.TrackUnavailableException
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.Locale
import kotlin.random.Random

enum class Mode { BEGINNING, RANDOM }

enum class Phase { PREPARING, READY, PLAYING, REVEAL_WIN, REVEAL_LOSS }

/** Ausschnittlängen: 0,1 s · 0,5 s · 1 s · 2 s · 4 s · 8 s · 16 s */
val STAGES_MS = longArrayOf(100, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
val STAGE_POINTS = intArrayOf(100, 80, 60, 45, 30, 20, 10)
const val MAX_SNIPPET_MS = 16_000L

fun formatSeconds(ms: Long): String =
    if (ms % 1000L == 0L) "${ms / 1000L}s" else String.format(Locale.GERMANY, "%.1fs", ms / 1000.0)

val STAGE_LABELS: List<String> = STAGES_MS.map { formatSeconds(it) }

sealed interface Screen {
    data object Setup : Screen
    data object Login : Screen
    data object Playlists : Screen
    data class Loading(val message: String) : Screen
    data object Game : Screen
    data object Results : Screen
}

enum class GuessKind { WRONG, SKIPPED, CORRECT }

data class GuessEntry(val text: String, val kind: GuessKind, val stage: Int)

data class RoundResult(val song: Song, val solvedStage: Int?, val points: Int)

data class GameState(
    val playlist: Playlist,
    val mode: Mode,
    val requestedRounds: Int,
    val songs: List<Song>,
    val allSongs: List<Song>,
    val round: Int = 0,
    val stage: Int = 0,
    val startMs: Long = 0,
    val guesses: List<GuessEntry> = emptyList(),
    val phase: Phase = Phase.PREPARING,
    val score: Int = 0,
    val lastPoints: Int = 0,
    val results: List<RoundResult> = emptyList(),
    val wrongTick: Int = 0,
) {
    val current: Song get() = songs[round]
    val isLastRound: Boolean get() = round >= songs.size - 1
    val canGuess: Boolean get() = phase == Phase.READY || phase == Phase.PLAYING
    val solvedStage: Int? get() = if (phase == Phase.REVEAL_WIN) stage else null
}

class GameViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val player = SnippetPlayer(app, prefs)
    private val api = SpotifyApi { force -> validToken(force) }
    private val tokenMutex = Mutex()

    var screen by mutableStateOf<Screen>(Screen.Loading("Starte …"))
        private set

    var toastText by mutableStateOf("")
        private set
    var toastVisible by mutableStateOf(false)
        private set
    var toastTick by mutableIntStateOf(0)
        private set

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var playlistsLoading by mutableStateOf(false)
        private set

    var mode by mutableStateOf(Mode.BEGINNING)
        private set
    var rounds by mutableIntStateOf(10)
        private set

    var game by mutableStateOf<GameState?>(null)
        private set

    /** Wird jedes Mal erhöht, wenn ein Ausschnitt hörbar startet (für die Ring-Animation). */
    var playTick by mutableIntStateOf(0)
        private set
    var audible by mutableStateOf(false)
        private set
    var confettiTick by mutableIntStateOf(0)
        private set

    var newHighscore by mutableStateOf(false)
        private set
    var previousBest by mutableIntStateOf(0)
        private set

    private var playJob: Job? = null
    private var playSeq = 0
    private var startJob: Job? = null
    private var loopbackJob: Job? = null

    init {
        player.restoreVolume()
        mode = runCatching { Mode.valueOf(prefs.mode) }.getOrDefault(Mode.BEGINNING)
        rounds = prefs.rounds
        bootstrap()
    }

    // ================================================================== Allgemein

    fun toast(text: String) {
        toastText = text
        toastVisible = true
        toastTick++
    }

    fun dismissToast() {
        toastVisible = false
    }

    private fun bootstrap() {
        when {
            prefs.clientId.isBlank() -> screen = Screen.Setup
            prefs.refreshToken.isNullOrBlank() && !hasValidAccessToken() -> screen = Screen.Login
            else -> {
                screen = Screen.Playlists
                loadPlaylists()
            }
        }
    }

    private fun hasValidAccessToken(): Boolean =
        !prefs.accessToken.isNullOrBlank() && System.currentTimeMillis() < prefs.expiresAt - 60_000

    fun selectMode(m: Mode) {
        mode = m
        prefs.mode = m.name
    }

    fun selectRounds(n: Int) {
        rounds = n
        prefs.rounds = n
    }

    fun bestFor(p: Playlist): Int = prefs.highscore(highscoreKey(p.id, mode, rounds))

    private fun highscoreKey(playlistId: String, m: Mode, r: Int) = "${playlistId}_${m.name}_$r"

    // ================================================================== Setup & Login

    fun saveClientId(id: String) {
        val clean = id.trim()
        if (clean.length < 20) {
            toast("Das sieht nicht nach einer Spotify Client-ID aus (32 Zeichen).")
            return
        }
        if (clean != prefs.clientId) prefs.clearAuth()
        prefs.clientId = clean
        screen = Screen.Login
    }

    fun openSetup() {
        screen = Screen.Setup
    }

    fun leaveSetup() {
        if (prefs.clientId.isNotBlank()) bootstrap()
    }

    fun startLogin(activity: Activity, loopback: Boolean) {
        val clientId = prefs.clientId
        if (clientId.isBlank()) {
            screen = Screen.Setup
            return
        }
        val verifier = SpotifyAuth.newVerifier()
        val state = SpotifyAuth.newState()
        val redirect = if (loopback) SpotifyAuth.LOOPBACK_REDIRECT_URI else SpotifyAuth.REDIRECT_URI
        prefs.codeVerifier = verifier
        prefs.authState = state
        prefs.authRedirect = redirect
        val url = SpotifyAuth.buildAuthUrl(clientId, redirect, SpotifyAuth.challenge(verifier), state)

        loopbackJob?.cancel()
        if (loopback) {
            loopbackJob = viewModelScope.launch {
                try {
                    val uri = LoopbackReceiver.awaitRedirect(SpotifyAuth.LOOPBACK_PORT, 5 * 60_000)
                    handleRedirect(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast("Alternativer Login fehlgeschlagen: ${e.message}")
                }
            }
        }
        openUrl(activity, url)
    }

    private fun openUrl(activity: Activity, url: Uri) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, url)
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, url))
            } catch (e2: ActivityNotFoundException) {
                toast("Kein Browser gefunden.")
            }
        }
    }

    fun handleRedirect(uri: Uri) {
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        if (error != null) {
            toast(if (error == "access_denied") "Login abgebrochen." else "Spotify-Fehler: $error")
            if (screen !is Screen.Playlists) screen = Screen.Login
            return
        }
        if (code == null) return // z. B. "Zurück zur App"-Link vom alternativen Login
        if (uri.getQueryParameter("state") != prefs.authState) {
            toast("Login-Antwort passt nicht (state). Bitte nochmal versuchen.")
            return
        }
        prefs.authState = "" // nur einmal verwenden
        loopbackJob?.cancel()
        val redirect = prefs.authRedirect.ifBlank { SpotifyAuth.REDIRECT_URI }
        viewModelScope.launch {
            screen = Screen.Loading("Verbinde mit Spotify …")
            try {
                val token = SpotifyAuth.exchangeCode(prefs.clientId, code, redirect, prefs.codeVerifier)
                storeToken(token)
                screen = Screen.Playlists
                loadPlaylists()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screen = Screen.Login
                toast("Login fehlgeschlagen: ${friendly(e)}")
            }
        }
    }

    private fun storeToken(t: TokenResult) {
        prefs.accessToken = t.accessToken
        t.refreshToken?.let { prefs.refreshToken = it }
        prefs.expiresAt = System.currentTimeMillis() + t.expiresIn * 1000L
    }

    private suspend fun validToken(force: Boolean): String? = tokenMutex.withLock {
        if (!force && hasValidAccessToken()) return@withLock prefs.accessToken
        val rt = prefs.refreshToken
        if (rt.isNullOrBlank()) return@withLock null
        try {
            val t = SpotifyAuth.refresh(prefs.clientId, rt)
            storeToken(t)
            t.accessToken
        } catch (e: ApiException) {
            // Refresh-Token ungültig -> neu einloggen
            prefs.clearAuth()
            null
        }
    }

    fun logout() {
        cancelPlay()
        prefs.clearAuth()
        player.release()
        playlists = emptyList()
        game = null
        screen = Screen.Login
    }

    // ================================================================== Playlists

    fun loadPlaylists() {
        if (playlistsLoading) return
        viewModelScope.launch {
            playlistsLoading = true
            try {
                playlists = api.loadPlaylists()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthRequiredException) {
                screen = Screen.Login
            } catch (e: Exception) {
                toast(friendly(e))
            } finally {
                playlistsLoading = false
            }
        }
    }

    // ================================================================== Spiel

    fun startGame(activity: Activity, playlist: Playlist) {
        if (startJob?.isActive == true) return
        if (!SpotifyAppRemote.isSpotifyInstalled(activity)) {
            toast("Die Spotify-App muss auf dem Handy installiert sein.")
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")),
                )
            } catch (e: Exception) {
                // ignorieren
            }
            return
        }
        val chosenMode = mode
        val chosenRounds = rounds
        startJob = viewModelScope.launch {
            cancelPlay()
            screen = Screen.Loading("Lade Songs aus „${playlist.name}“ …")
            try {
                val all = api.loadSongs(playlist) { n ->
                    screen = Screen.Loading("Lade Songs aus „${playlist.name}“ … $n")
                }
                if (all.isEmpty()) throw UserMessageException("In dieser Playlist sind keine abspielbaren Songs.")
                if (!player.isConnected) {
                    screen = Screen.Loading("Verbinde mit der Spotify-App …")
                    player.connect(activity, prefs.clientId)
                }
                if (player.isMediaVolumeZero()) toast("Deine Medienlautstärke steht auf 0 – dreh sie auf! 🔊")
                val picked = all.shuffled().take(chosenRounds)
                game = GameState(
                    playlist = playlist,
                    mode = chosenMode,
                    requestedRounds = chosenRounds,
                    songs = picked,
                    allSongs = all,
                )
                newHighscore = false
                screen = Screen.Game
                startRound(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthRequiredException) {
                screen = Screen.Login
            } catch (e: Exception) {
                screen = Screen.Playlists
                toast(friendly(e))
            }
        }
    }

    private fun randomStart(durationMs: Long, m: Mode): Long {
        if (m == Mode.BEGINNING) return 0L
        val max = durationMs - MAX_SNIPPET_MS
        return if (max <= 0L) 0L else Random.nextLong(0L, max + 1L)
    }

    private fun startRound(index: Int, attempt: Int = 0) {
        val g = game ?: return
        val song = g.songs[index]
        val start = randomStart(song.durationMs, g.mode)
        game = g.copy(
            round = index,
            stage = 0,
            startMs = start,
            guesses = emptyList(),
            phase = Phase.PREPARING,
            lastPoints = 0,
        )
        playJob?.cancel()
        val mySeq = ++playSeq
        playJob = viewModelScope.launch {
            try {
                player.prepare(song, start)
                if (playSeq == mySeq) updateGame { it.copy(phase = Phase.READY) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TrackUnavailableException) {
                // Song nicht verfügbar -> durch einen anderen ersetzen
                val cur = game ?: return@launch
                val used = cur.songs.map { it.uri }.toSet()
                val replacement = cur.allSongs.filter { it.uri !in used }.randomOrNull()
                if (replacement != null && attempt < 4) {
                    game = cur.copy(songs = cur.songs.toMutableList().also { it[index] = replacement })
                    startRound(index, attempt + 1)
                } else {
                    toast("Dieser Song lässt sich nicht abspielen.")
                    updateGame { it.copy(phase = Phase.READY) }
                }
            } catch (e: Exception) {
                handlePlayerError(e)
                updateGame { it.copy(phase = Phase.READY) }
            }
        }
    }

    fun play() {
        val g = game ?: return
        if (g.phase != Phase.READY) return
        val duration = STAGES_MS[g.stage]
        val song = g.current
        updateGame { it.copy(phase = Phase.PLAYING) }
        val mySeq = ++playSeq
        playJob = viewModelScope.launch {
            try {
                player.playSnippet(song, g.startMs, duration) { on ->
                    audible = on
                    if (on) playTick++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handlePlayerError(e)
            } finally {
                if (playSeq == mySeq) {
                    audible = false
                    if (game?.phase == Phase.PLAYING) updateGame { it.copy(phase = Phase.READY) }
                }
            }
        }
    }

    fun skip() {
        val g = game ?: return
        if (!g.canGuess) return
        cancelPlay()
        val label = if (g.stage >= STAGES_MS.size - 1) "Aufgegeben" else "Übersprungen"
        advance(g.copy(guesses = g.guesses + GuessEntry(label, GuessKind.SKIPPED, g.stage), phase = Phase.READY))
    }

    fun guess(s: Song) {
        val g = game ?: return
        if (!g.canGuess) return
        val target = g.current
        val correct = s.uri == target.uri || s.id == target.id || s.matchKey == target.matchKey
        cancelPlay()
        if (correct) {
            finishRound(g.copy(guesses = g.guesses + GuessEntry(s.name, GuessKind.CORRECT, g.stage)), solved = true)
        } else {
            advance(
                g.copy(
                    guesses = g.guesses + GuessEntry("${s.name} – ${s.artistText}", GuessKind.WRONG, g.stage),
                    wrongTick = g.wrongTick + 1,
                    phase = Phase.READY,
                ),
            )
        }
    }

    private fun advance(g: GameState) {
        if (g.stage >= STAGES_MS.size - 1) {
            finishRound(g, solved = false)
        } else {
            game = g.copy(stage = g.stage + 1, phase = Phase.READY)
            play() // längeren Ausschnitt direkt abspielen
        }
    }

    private fun finishRound(g: GameState, solved: Boolean) {
        val points = if (solved) STAGE_POINTS[g.stage] else 0
        val song = g.current
        game = g.copy(
            phase = if (solved) Phase.REVEAL_WIN else Phase.REVEAL_LOSS,
            score = g.score + points,
            lastPoints = points,
            results = g.results + RoundResult(song, if (solved) g.stage else null, points),
        )
        if (solved) confettiTick++
        val mySeq = ++playSeq
        playJob = viewModelScope.launch {
            try {
                if (playSeq == mySeq) player.reveal(song, g.startMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reveal-Wiedergabe ist optional
            }
        }
    }

    fun nextRound() {
        val g = game ?: return
        if (g.phase != Phase.REVEAL_WIN && g.phase != Phase.REVEAL_LOSS) return
        if (g.isLastRound) {
            cancelPlay()
            viewModelScope.launch { runCatching { player.stop() } }
            val key = highscoreKey(g.playlist.id, g.mode, g.requestedRounds)
            previousBest = prefs.highscore(key)
            newHighscore = g.score > previousBest && g.score > 0
            if (newHighscore) prefs.setHighscore(key, g.score)
            screen = Screen.Results
        } else {
            startRound(g.round + 1)
        }
    }

    fun playAgain(activity: Activity) {
        val g = game ?: return
        startGame(activity, g.playlist)
    }

    fun quitGame() {
        cancelPlay()
        viewModelScope.launch { runCatching { player.stop() } }
        game = null
        screen = Screen.Playlists
        if (playlists.isEmpty()) loadPlaylists()
    }

    fun backToPlaylists() {
        game = null
        screen = Screen.Playlists
    }

    fun search(query: String): List<Song> {
        val g = game ?: return emptyList()
        val tokens = norm(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val first = tokens.first()
        return g.allSongs.asSequence()
            .filter { s -> tokens.all { s.searchKey.contains(it) } }
            .distinctBy { it.matchKey }
            .sortedBy { s -> if (norm(s.name).startsWith(first)) 0 else 1 }
            .take(8)
            .toList()
    }

    // ================================================================== Lebenszyklus

    fun onForeground(activity: Activity) {
        val g = game ?: return
        if (screen == Screen.Game && !player.isConnected && startJob?.isActive != true) {
            startJob = viewModelScope.launch {
                try {
                    player.connect(activity, prefs.clientId)
                    if (g.phase == Phase.PREPARING) startRound(g.round)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(friendly(e))
                }
            }
        }
    }

    fun onBackground() {
        if (game?.phase == Phase.PLAYING) cancelPlay()
    }

    private fun cancelPlay() {
        playSeq++
        playJob?.cancel()
        playJob = null
        audible = false
    }

    private inline fun updateGame(f: (GameState) -> GameState) {
        val g = game ?: return
        game = f(g)
    }

    private fun handlePlayerError(e: Exception) {
        toast(friendly(e))
        if (e is NotConnectedException || e.javaClass.simpleName.contains("Disconnected") ||
            e.javaClass.simpleName.contains("Terminated")
        ) {
            quitGame()
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    // ================================================================== Fehlertexte

    fun friendly(e: Throwable): String {
        val name = e.javaClass.simpleName
        return when {
            e is UserMessageException -> e.message ?: "Fehler"
            e is PremiumRequiredException ->
                "Spotify Premium wird benötigt, um bestimmte Songs abzuspielen."
            e is NotConnectedException -> "Die Verbindung zur Spotify-App ist weg. Starte das Spiel neu."
            e is PlaybackException -> e.message ?: "Wiedergabe-Fehler"
            e is TimeoutCancellationException -> "Spotify antwortet nicht. Ist die Spotify-App geöffnet und eingeloggt?"
            e is AuthRequiredException -> "Bitte erneut bei Spotify anmelden."
            e is ApiException && e.code == 403 ->
                "Spotify verweigert den Zugriff (403). Ist dein Account im Developer-Dashboard unter „User Management“ eingetragen und hast du Premium?"
            e is ApiException && e.code == 429 -> "Zu viele Anfragen an Spotify – kurz warten."
            e is ApiException && e.code == 400 -> "Spotify meldet: ${e.message}"
            e is IOException -> "Keine Internetverbindung?"
            name == "CouldNotFindSpotifyApp" -> "Die Spotify-App ist nicht installiert."
            name == "NotLoggedInException" -> "Bitte melde dich zuerst in der Spotify-App an."
            name == "UserNotAuthorizedException" || name == "AuthenticationFailedException" ->
                "Spotify hat die App abgelehnt. Prüfe im Developer-Dashboard Paketname (com.songsnap.game) und SHA1-Fingerprint. (${e.message})"
            name == "OfflineModeException" -> "Die Spotify-App ist im Offline-Modus."
            name == "UnsupportedFeatureVersionException" -> "Bitte aktualisiere die Spotify-App."
            name.contains("Disconnected") || name.contains("Terminated") -> "Verbindung zur Spotify-App verloren."
            else -> e.message ?: name
        }
    }
}
