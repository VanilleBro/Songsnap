import Foundation

let likedPlaylistID = "__liked__"

struct Playlist: Identifiable, Hashable {
    let id: String
    let name: String
    let imageURL: URL?
    let trackCount: Int
    let owner: String
    var isLiked: Bool = false
    /// Spotify gibt im Entwicklungsmodus nur Songs eigener/kollaborativer Playlists heraus.
    var accessible: Bool = true
}

struct Song: Identifiable, Hashable {
    let id: String
    let uri: String
    let name: String
    let artists: [String]
    let album: String
    let imageURL: URL?
    let durationMs: Int
    let searchKey: String
    let matchKey: String

    init(id: String, uri: String, name: String, artists: [String], album: String, imageURL: URL?, durationMs: Int) {
        self.id = id
        self.uri = uri
        self.name = name
        self.artists = artists
        self.album = album
        self.imageURL = imageURL
        self.durationMs = durationMs
        self.searchKey = normalize(name + " " + artists.joined(separator: " "))
        self.matchKey = normalize(coreTitle(name)) + "|" + normalize(artists.first ?? "")
    }

    var artistText: String { artists.joined(separator: ", ") }
}

enum AppError: LocalizedError {
    case authRequired
    case api(Int, String)
    case message(String)
    case premiumRequired
    case trackUnavailable
    case notConnected

    var errorDescription: String? {
        switch self {
        case .authRequired: return "Bitte erneut bei Spotify anmelden."
        case let .api(code, body):
            switch code {
            case 403: return "Spotify verweigert den Zugriff (403). Ist dein Account im Developer-Dashboard unter „User Management“ eingetragen und hast du Premium?"
            case 429: return "Zu viele Anfragen an Spotify – kurz warten."
            default: return "Spotify meldet Fehler \(code): \(body.prefix(200))"
            }
        case let .message(m): return m
        case .premiumRequired: return "Spotify Premium wird benötigt, um Songs gezielt abzuspielen."
        case .trackUnavailable: return "Dieser Song lässt sich nicht abspielen."
        case .notConnected: return "Die Verbindung zur Spotify-App ist weg. Starte das Spiel neu."
        }
    }
}

/// Kleinbuchstaben, ohne Akzente/Sonderzeichen – für Suche & Vergleich.
func normalize(_ s: String) -> String {
    let folded = s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "de_DE")).lowercased()
    let cleaned = folded.replacingOccurrences(of: "[^a-z0-9ß ]", with: " ", options: .regularExpression)
    return cleaned.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespaces)
}

/// "Song (feat. X) - Remastered 2011" -> "Song"
func coreTitle(_ s: String) -> String {
    let base = s.components(separatedBy: " - ").first ?? s
    return base.replacingOccurrences(of: "\\(.*?\\)|\\[.*?\\]", with: "", options: .regularExpression)
        .trimmingCharacters(in: .whitespaces)
}
