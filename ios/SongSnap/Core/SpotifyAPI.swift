import Foundation

/// Minimaler Spotify-Web-API-Client (Stand der API-Änderungen von Februar 2026:
/// Playlist-Songs kommen über /playlists/{id}/items, Feld "item" statt "track").
@MainActor
final class SpotifyAPI {
    typealias TokenProvider = @MainActor (_ forceRefresh: Bool) async throws -> String?
    private let token: TokenProvider

    init(token: @escaping TokenProvider) {
        self.token = token
    }

    private func get(_ path: String) async throws -> [String: Any] {
        let urlString = path.hasPrefix("http") ? path : "https://api.spotify.com/v1" + path
        guard let url = URL(string: urlString) else { throw AppError.message("Ungültige Adresse") }
        var attempts = 0
        var force = false
        while true {
            guard let t = try await token(force) else { throw AppError.authRequired }
            var req = URLRequest(url: url)
            req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
            req.timeoutInterval = 20
            let (data, resp) = try await URLSession.shared.data(for: req)
            let http = resp as? HTTPURLResponse
            let code = http?.statusCode ?? 0
            if (200...299).contains(code) {
                return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
            } else if code == 401 && !force {
                force = true
            } else if code == 401 {
                throw AppError.authRequired
            } else if code == 429 && attempts < 3 {
                attempts += 1
                let wait = Double(http?.value(forHTTPHeaderField: "Retry-After") ?? "2") ?? 2
                try await Task.sleep(nanoseconds: UInt64(min(max(wait, 1), 10) * 1_000_000_000))
            } else if code >= 500 && attempts < 2 {
                attempts += 1
                try await Task.sleep(nanoseconds: 800_000_000)
            } else {
                throw AppError.api(code, String(data: data, encoding: .utf8) ?? "")
            }
        }
    }

    private func str(_ d: [String: Any]?, _ key: String) -> String? {
        guard let s = d?[key] as? String, !s.isEmpty else { return nil }
        return s
    }

    private func firstImage(_ any: Any?) -> URL? {
        guard let arr = any as? [[String: Any]], let s = arr.first?["url"] as? String else { return nil }
        return URL(string: s)
    }

    func loadPlaylists() async throws -> [Playlist] {
        let me = try await get("/me")
        let myID = str(me, "id") ?? ""
        var result: [Playlist] = []

        if let liked = try? await get("/me/tracks?limit=1") {
            result.append(Playlist(
                id: likedPlaylistID, name: "Lieblingssongs", imageURL: nil,
                trackCount: liked["total"] as? Int ?? 0, owner: "Deine Bibliothek", isLiked: true
            ))
        }

        var next: String? = "/me/playlists?limit=50"
        while let n = next, result.count < 600 {
            let page = try await get(n)
            for p in page["items"] as? [[String: Any]] ?? [] {
                guard let id = str(p, "id") else { continue }
                let owner = p["owner"] as? [String: Any]
                let ownerID = str(owner, "id") ?? ""
                let countObj = (p["items"] as? [String: Any]) ?? (p["tracks"] as? [String: Any])
                result.append(Playlist(
                    id: id,
                    name: str(p, "name") ?? "Ohne Namen",
                    imageURL: firstImage(p["images"]),
                    trackCount: countObj?["total"] as? Int ?? -1,
                    owner: str(owner, "display_name") ?? ownerID,
                    accessible: ownerID == myID || (p["collaborative"] as? Bool ?? false)
                ))
            }
            next = str(page, "next")
        }
        // Lieblingssongs zuerst, dann spielbare, dann gesperrte
        return result.enumerated().sorted { a, b in
            let ka = (a.element.isLiked ? 0 : 1, a.element.accessible ? 0 : 1, a.offset)
            let kb = (b.element.isLiked ? 0 : 1, b.element.accessible ? 0 : 1, b.offset)
            return ka < kb
        }.map(\.element)
    }

    func loadSongs(_ playlist: Playlist, progress: (Int) -> Void) async throws -> [Song] {
        var songs: [Song] = []
        var seen = Set<String>()
        var next: String? = playlist.isLiked
            ? "/me/tracks?limit=50"
            : "/playlists/\(playlist.id)/items?limit=50&additional_types=track"
        while let n = next, songs.count < 2500 {
            let page: [String: Any]
            do {
                page = try await get(n)
            } catch AppError.api(let code, _) where code == 403 || code == 404 {
                throw AppError.message(
                    "Spotify gibt die Songs dieser Playlist nicht frei. Im Entwicklungsmodus funktionieren nur eigene oder kollaborative Playlists – kopiere sie notfalls in eine eigene Playlist."
                )
            }
            for entry in page["items"] as? [[String: Any]] ?? [] {
                guard let t = (entry["item"] as? [String: Any]) ?? (entry["track"] as? [String: Any]) else { continue }
                if (entry["is_local"] as? Bool ?? false) || (t["is_local"] as? Bool ?? false) { continue }
                guard let uri = str(t, "uri"), uri.hasPrefix("spotify:track:"), !seen.contains(uri) else { continue }
                let duration = t["duration_ms"] as? Int ?? 0
                guard duration > 0 else { continue }
                let artists = (t["artists"] as? [[String: Any]] ?? []).compactMap { $0["name"] as? String }
                let album = t["album"] as? [String: Any]
                seen.insert(uri)
                songs.append(Song(
                    id: str(t, "id") ?? String(uri.split(separator: ":").last ?? ""),
                    uri: uri,
                    name: str(t, "name") ?? "Unbekannt",
                    artists: artists,
                    album: str(album, "name") ?? "",
                    imageURL: firstImage(album?["images"]),
                    durationMs: duration
                ))
            }
            progress(songs.count)
            next = str(page, "next")
        }
        return songs
    }
}
