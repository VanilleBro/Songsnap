package com.songsnap.game.data

import java.text.Normalizer
import java.util.Locale

const val LIKED_ID = "__liked__"

data class Playlist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val owner: String,
    val isLiked: Boolean = false,
    /** Spotify gibt im Entwicklungsmodus nur Songs eigener/kollaborativer Playlists heraus. */
    val accessible: Boolean = true,
)

data class Song(
    val id: String,
    val uri: String,
    val name: String,
    val artists: List<String>,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
) {
    val artistText: String get() = artists.joinToString(", ")
    val firstArtist: String get() = artists.firstOrNull() ?: ""
    val searchKey: String = norm(name + " " + artists.joinToString(" "))
    val matchKey: String = norm(coreTitle(name)) + "|" + norm(artists.firstOrNull() ?: "")
}

class ApiException(val code: Int, body: String) : Exception("HTTP $code ${body.take(300)}")
class AuthRequiredException : Exception("Login erforderlich")
class UserMessageException(message: String) : Exception(message)

private val MARKS = Regex("\\p{Mn}+")
private val NON_ALNUM = Regex("[^a-z0-9ß ]")
private val SPACES = Regex("\\s+")
private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")

/** Kleinbuchstaben, ohne Akzente/Sonderzeichen – für Suche & Vergleich. */
fun norm(s: String): String {
    val lower = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "")
    return lower.replace(NON_ALNUM, " ").replace(SPACES, " ").trim()
}

/** "Song (feat. X) - Remastered 2011" -> "Song" */
fun coreTitle(s: String): String = s.substringBefore(" - ").replace(BRACKETS, "").trim()
