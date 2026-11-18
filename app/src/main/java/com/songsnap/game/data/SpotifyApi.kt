package com.songsnap.game.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimaler Spotify-Web-API-Client (Stand der API-Änderungen von Februar 2026:
 * Playlist-Songs kommen über /playlists/{id}/items, Feld "item" statt "track").
 */
class SpotifyApi(private val token: suspend (forceRefresh: Boolean) -> String?) {

    private suspend fun get(path: String): JSONObject {
        val url = if (path.startsWith("http")) path else "https://api.spotify.com/v1$path"
        var attempts = 0
        var force = false
        while (true) {
            val t = token(force) ?: throw AuthRequiredException()
            val (code, body, retryAfter) = withContext(Dispatchers.IO) { Http.get(url, t) }
            when {
                code in 200..299 -> return JSONObject(body.ifBlank { "{}" })
                code == 401 && !force -> force = true
                code == 401 -> throw AuthRequiredException()
                code == 429 && attempts < 3 -> {
                    attempts++
                    delay(((retryAfter ?: 2L).coerceIn(1L, 10L)) * 1000L)
                }
                code >= 500 && attempts < 2 -> {
                    attempts++
                    delay(800)
                }
                else -> throw ApiException(code, body)
            }
        }
    }

    private fun JSONObject.str(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun firstImage(arr: JSONArray?): String? =
        arr?.optJSONObject(0)?.str("url")

    suspend fun loadPlaylists(): List<Playlist> {
        val me = get("/me")
        val myId = me.str("id") ?: ""
        val result = mutableListOf<Playlist>()

        try {
            val liked = get("/me/tracks?limit=1")
            result += Playlist(
                id = LIKED_ID,
                name = "Lieblingssongs",
                imageUrl = null,
                trackCount = liked.optInt("total", 0),
                owner = "Deine Bibliothek",
                isLiked = true,
            )
        } catch (e: ApiException) {
            // ohne Lieblingssongs weitermachen
        }

        var next: String? = "/me/playlists?limit=50"
        while (next != null && result.size < 600) {
            val page = get(next)
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val p = items.optJSONObject(i) ?: continue
                val id = p.str("id") ?: continue
                val ownerObj = p.optJSONObject("owner")
                val ownerId = ownerObj?.str("id") ?: ""
                val ownerName = ownerObj?.str("display_name") ?: ownerId
                val countObj = p.optJSONObject("items") ?: p.optJSONObject("tracks")
                result += Playlist(
                    id = id,
                    name = p.str("name") ?: "Ohne Namen",
                    imageUrl = firstImage(p.optJSONArray("images")),
                    trackCount = countObj?.optInt("total", -1) ?: -1,
                    owner = ownerName,
                    accessible = ownerId == myId || p.optBoolean("collaborative", false),
                )
            }
            next = page.str("next")
        }
        // Lieblingssongs zuerst, dann spielbare, dann gesperrte
        return result.sortedWith(compareBy<Playlist>({ !it.isLiked }, { !it.accessible }))
    }

    suspend fun loadSongs(playlist: Playlist, onProgress: (Int) -> Unit = {}): List<Song> {
        val songs = LinkedHashMap<String, Song>()
        var next: String? = if (playlist.isLiked) {
            "/me/tracks?limit=50"
        } else {
            "/playlists/${playlist.id}/items?limit=50&additional_types=track"
        }
        while (next != null && songs.size < 2500) {
            val page = try {
                get(next)
            } catch (e: ApiException) {
                if (e.code == 403 || e.code == 404) {
                    throw UserMessageException(
                        "Spotify gibt die Songs dieser Playlist nicht frei. Im Entwicklungsmodus " +
                            "funktionieren nur eigene oder kollaborative Playlists – kopiere sie notfalls " +
                            "in eine eigene Playlist.",
                    )
                }
                throw e
            }
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val t = entry.optJSONObject("item") ?: entry.optJSONObject("track") ?: continue
                if (entry.optBoolean("is_local", false) || t.optBoolean("is_local", false)) continue
                val uri = t.str("uri") ?: continue
                if (!uri.startsWith("spotify:track:")) continue
                val duration = t.optLong("duration_ms", 0L)
                if (duration <= 0L) continue
                val artistsArr = t.optJSONArray("artists")
                val artists = mutableListOf<String>()
                if (artistsArr != null) {
                    for (a in 0 until artistsArr.length()) {
                        artistsArr.optJSONObject(a)?.str("name")?.let { artists += it }
                    }
                }
                val album = t.optJSONObject("album")
                val song = Song(
                    id = t.str("id") ?: uri.substringAfterLast(':'),
                    uri = uri,
                    name = t.str("name") ?: "Unbekannt",
                    artists = artists,
                    album = album?.str("name") ?: "",
                    imageUrl = firstImage(album?.optJSONArray("images")),
                    durationMs = duration,
                )
                if (!songs.containsKey(uri)) songs[uri] = song
            }
            onProgress(songs.size)
            next = page.str("next")
        }
        return songs.values.toList()
    }
}
