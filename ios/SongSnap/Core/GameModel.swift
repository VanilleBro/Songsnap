import Foundation
import SwiftUI
import UIKit

enum GameMode: String, CaseIterable {
    case beginning
    case random
}

enum Phase: Equatable {
    case preparing, ready, playing, revealWin, revealLoss
}

/// Ausschnittlängen: 0,1 s · 0,5 s · 1 s · 2 s · 4 s · 8 s · 16 s
let stagesMs = [100, 500, 1_000, 2_000, 4_000, 8_000, 16_000]
let stagePoints = [100, 80, 60, 45, 30, 20, 10]
let maxSnippetMs = 16_000

func formatSeconds(_ ms: Int) -> String {
    if ms % 1000 == 0 { return "\(ms / 1000)s" }
    return String(format: "%.1fs", Double(ms) / 1000).replacingOccurrences(of: ".", with: ",")
}

let stageLabels = stagesMs.map(formatSeconds)

enum Screen: Equatable {
    case setup, login, playlists, loading(String), game, results

    var key: String {
        switch self {
        case .setup: return "setup"
        case .login: return "login"
        case .playlists: return "playlists"
        case .loading: return "loading"
        case .game: return "game"
        case .results: return "results"
        }
    }
}

enum GuessKind { case wrong, skipped, correct }

struct GuessEntry: Identifiable, Equatable {
    let id = UUID()
    let text: String
    let kind: GuessKind
    let stage: Int
}

struct RoundResult: Identifiable {
    let id = UUID()
    let song: Song
    let solvedStage: Int?
    let points: Int
}

struct GameState {
    let playlist: Playlist
    let mode: GameMode
    let requestedRounds: Int
    var songs: [Song]
    let allSongs: [Song]
    var round = 0
    var stage = 0
    var startMs = 0
    var guesses: [GuessEntry] = []
    var phase: Phase = .preparing
    var score = 0
    var lastPoints = 0
    var results: [RoundResult] = []
    var wrongTick = 0

    var current: Song { songs[round] }
    var isLastRound: Bool { round >= songs.count - 1 }
    var canGuess: Bool { phase == .ready || phase == .playing }
}

@MainActor
final class GameModel: ObservableObject {
    @Published private(set) var screen: Screen = .loading("Starte …")
    @Published private(set) var toastText = ""
    @Published var toastVisible = false
    @Published private(set) var toastTick = 0

    @Published private(set) var playlists: [Playlist] = []
    @Published private(set) var playlistsLoading = false
    @Published private(set) var mode: GameMode = .beginning
    @Published private(set) var rounds = 10

    @Published private(set) var game: GameState?
    @Published private(set) var playTick = 0
    @Published private(set) var audible = false
    @Published private(set) var confettiTick = 0
    @Published private(set) var newHighscore = false
    @Published private(set) var previousBest = 0

    let prefs = Prefs()
    let player: SnippetPlayer
    private var api: SpotifyAPI!
    private let webAuth = WebAuth()
    private var playTask: Task<Void, Never>?
    private var playSeq = 0
    private var startTask: Task<Void, Never>?
    private var refreshTask: Task<String?, Error>?

    init() {
        player = SnippetPlayer()
        api = SpotifyAPI { [weak self] force in
            try await self?.validToken(force: force)
        }
        mode = GameMode(rawValue: prefs.mode) ?? .beginning
        rounds = prefs.rounds
        player.onUnexpectedDisconnect = { [weak self] in self?.handleDisconnect() }
        bootstrap()
    }

    // MARK: Allgemein

    func toast(_ text: String) {
        toastText = text
        toastVisible = true
        toastTick += 1
    }

    private func bootstrap() {
        if prefs.clientID.isEmpty {
            screen = .setup
        } else if (prefs.refreshToken ?? "").isEmpty && !hasValidAccessToken {
            screen = .login
        } else {
            screen = .playlists
            loadPlaylists()
        }
    }

    private var hasValidAccessToken: Bool {
        guard let t = prefs.accessToken, !t.isEmpty else { return false }
        return Date() < prefs.expiresAt.addingTimeInterval(-60)
    }

    func selectMode(_ m: GameMode) {
        mode = m
        prefs.mode = m.rawValue
    }

    func selectRounds(_ n: Int) {
        rounds = n
        prefs.rounds = n
    }

    private func highscoreKey(_ playlistID: String, _ m: GameMode, _ r: Int) -> String {
        "\(playlistID)_\(m.rawValue)_\(r)"
    }

    func best(for p: Playlist) -> Int {
        prefs.highscore(highscoreKey(p.id, mode, rounds))
    }

    // MARK: Setup & Login

    func saveClientID(_ id: String) {
        let clean = id.trimmingCharacters(in: .whitespacesAndNewlines)
        guard clean.count >= 20 else {
            toast("Das sieht nicht nach einer Spotify Client-ID aus (32 Zeichen).")
            return
        }
        if clean != prefs.clientID { prefs.clearAuth() }
        prefs.clientID = clean
        screen = .login
    }

    func openSetup() { screen = .setup }

    func leaveSetup() {
        if !prefs.clientID.isEmpty { bootstrap() }
    }

    func login() {
        let clientID = prefs.clientID
        guard !clientID.isEmpty else {
            screen = .setup
            return
        }
        let verifier = SpotifyAuth.randomString(bytes: 64)
        let state = SpotifyAuth.randomString(bytes: 16)
        let url = SpotifyAuth.authorizeURL(clientID: clientID, challenge: SpotifyAuth.challenge(for: verifier), state: state)
        Task {
            do {
                let callback = try await webAuth.start(url: url)
                let items = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []
                if let err = items.first(where: { $0.name == "error" })?.value {
                    throw AppError.message(err == "access_denied" ? "Login abgebrochen." : "Spotify-Fehler: \(err)")
                }
                guard items.first(where: { $0.name == "state" })?.value == state,
                      let code = items.first(where: { $0.name == "code" })?.value
                else { throw AppError.message("Login-Antwort passt nicht. Bitte nochmal versuchen.") }
                screen = .loading("Verbinde mit Spotify …")
                let token = try await SpotifyAuth.exchange(code: code, clientID: clientID, verifier: verifier)
                store(token)
                screen = .playlists
                loadPlaylists()
            } catch {
                screen = .login
                toast(friendly(error))
            }
        }
    }

    private func store(_ t: TokenResult) {
        prefs.accessToken = t.accessToken
        if let rt = t.refreshToken { prefs.refreshToken = rt }
        prefs.expiresAt = Date().addingTimeInterval(TimeInterval(t.expiresIn))
    }

    private func validToken(force: Bool) async throws -> String? {
        if !force && hasValidAccessToken { return prefs.accessToken }
        if let running = refreshTask { return try await running.value }
        guard let rt = prefs.refreshToken, !rt.isEmpty else { return nil }
        let clientID = prefs.clientID
        let task = Task<String?, Error> { @MainActor in
            do {
                let t = try await SpotifyAuth.refresh(refreshToken: rt, clientID: clientID)
                self.store(t)
                return t.accessToken
            } catch AppError.api {
                self.prefs.clearAuth()
                return nil
            }
        }
        refreshTask = task
        defer { refreshTask = nil }
        return try await task.value
    }

    func logout() {
        cancelPlay()
        Task { await player.stop() }
        player.disconnect()
        prefs.clearAuth()
        playlists = []
        game = nil
        screen = .login
    }

    // MARK: Playlists

    func loadPlaylists() {
        guard !playlistsLoading else { return }
        playlistsLoading = true
        Task {
            defer { playlistsLoading = false }
            do {
                playlists = try await api.loadPlaylists()
            } catch AppError.authRequired {
                screen = .login
            } catch {
                toast(friendly(error))
            }
        }
    }

    // MARK: Spiel

    func startGame(_ playlist: Playlist) {
        guard startTask == nil else { return }
        let chosenMode = mode
        let chosenRounds = rounds
        startTask = Task {
            defer { startTask = nil }
            cancelPlay()
            screen = .loading("Lade Songs aus „\(playlist.name)“ …")
            do {
                let all = try await api.loadSongs(playlist) { n in
                    self.screen = .loading("Lade Songs aus „\(playlist.name)“ … \(n)")
                }
                guard !all.isEmpty else { throw AppError.message("In dieser Playlist sind keine abspielbaren Songs.") }
                let picked = Array(all.shuffled().prefix(chosenRounds))
                VolumeController.shared.captureUserVolume()
                screen = .loading("Verbinde mit der Spotify-App …")
                try await player.ensureConnected(clientID: prefs.clientID, wakeURI: picked[0].uri)
                if VolumeController.shared.userVolume < 0.05 {
                    toast("Deine Lautstärke steht auf 0 – dreh sie auf! 🔊")
                }
                game = GameState(
                    playlist: playlist, mode: chosenMode, requestedRounds: chosenRounds,
                    songs: picked, allSongs: all
                )
                newHighscore = false
                screen = .game
                startRound(0)
            } catch is CancellationError {
                // ignorieren
            } catch AppError.authRequired {
                screen = .login
            } catch {
                await player.stop()
                screen = .playlists
                toast(friendly(error))
            }
        }
    }

    private func randomStart(_ durationMs: Int, _ m: GameMode) -> Int {
        guard m == .random else { return 0 }
        let maxStart = durationMs - maxSnippetMs
        return maxStart <= 0 ? 0 : Int.random(in: 0...maxStart)
    }

    private func startRound(_ index: Int, attempt: Int = 0) {
        guard var g = game else { return }
        let song = g.songs[index]
        let start = randomStart(song.durationMs, g.mode)
        g.round = index
        g.stage = 0
        g.startMs = start
        g.guesses = []
        g.phase = .preparing
        g.lastPoints = 0
        game = g
        playTask?.cancel()
        playSeq += 1
        let mySeq = playSeq
        playTask = Task {
            do {
                try await reconnectIfNeeded(song)
                try await player.prepare(song)
                if playSeq == mySeq { setPhase(.ready) }
            } catch is CancellationError {
                // ignorieren
            } catch AppError.trackUnavailable {
                guard var cur = game else { return }
                let used = Set(cur.songs.map(\.uri))
                if let replacement = cur.allSongs.filter({ !used.contains($0.uri) }).randomElement(), attempt < 4 {
                    cur.songs[index] = replacement
                    game = cur
                    startRound(index, attempt: attempt + 1)
                } else {
                    toast("Dieser Song lässt sich nicht abspielen.")
                    setPhase(.ready)
                }
            } catch {
                toast(friendly(error))
                setPhase(.ready)
            }
        }
    }

    private func reconnectIfNeeded(_ song: Song) async throws {
        if !player.isConnected {
            try await player.ensureConnected(clientID: prefs.clientID, wakeURI: song.uri)
        }
    }

    private func setPhase(_ p: Phase) {
        guard var g = game else { return }
        g.phase = p
        game = g
    }

    func play() {
        guard let g = game, g.phase == .ready else { return }
        let duration = stagesMs[g.stage]
        let song = g.current
        let start = g.startMs
        setPhase(.playing)
        playSeq += 1
        let mySeq = playSeq
        playTask = Task {
            do {
                try await reconnectIfNeeded(song)
                try await player.playSnippet(song, startMs: start, durationMs: duration) { on in
                    self.audible = on
                    if on { self.playTick += 1 }
                }
            } catch is CancellationError {
                // ignorieren
            } catch {
                toast(friendly(error))
            }
            if playSeq == mySeq {
                audible = false
                if game?.phase == .playing { setPhase(.ready) }
            }
        }
    }

    func skip() {
        guard var g = game, g.canGuess else { return }
        cancelPlay()
        g.guesses.append(GuessEntry(text: g.stage >= stagesMs.count - 1 ? "Aufgegeben" : "Übersprungen", kind: .skipped, stage: g.stage))
        g.phase = .ready
        advance(g)
    }

    func guess(_ s: Song) {
        guard var g = game, g.canGuess else { return }
        let target = g.current
        let correct = s.uri == target.uri || s.id == target.id || s.matchKey == target.matchKey
        cancelPlay()
        if correct {
            g.guesses.append(GuessEntry(text: s.name, kind: .correct, stage: g.stage))
            finishRound(g, solved: true)
        } else {
            g.guesses.append(GuessEntry(text: "\(s.name) – \(s.artistText)", kind: .wrong, stage: g.stage))
            g.wrongTick += 1
            g.phase = .ready
            advance(g)
        }
    }

    private func advance(_ g: GameState) {
        var g = g
        if g.stage >= stagesMs.count - 1 {
            finishRound(g, solved: false)
        } else {
            g.stage += 1
            g.phase = .ready
            game = g
            play() // längeren Ausschnitt direkt abspielen
        }
    }

    private func finishRound(_ g: GameState, solved: Bool) {
        var g = g
        let points = solved ? stagePoints[g.stage] : 0
        let song = g.current
        g.phase = solved ? .revealWin : .revealLoss
        g.score += points
        g.lastPoints = points
        g.results.append(RoundResult(song: song, solvedStage: solved ? g.stage : nil, points: points))
        game = g
        if solved {
            confettiTick += 1
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } else {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
        playSeq += 1
        let start = g.startMs
        playTask = Task {
            try? await player.reveal(song, startMs: start)
        }
    }

    func nextRound() {
        guard let g = game, g.phase == .revealWin || g.phase == .revealLoss else { return }
        if g.isLastRound {
            cancelPlay()
            Task { await player.stop() }
            let key = highscoreKey(g.playlist.id, g.mode, g.requestedRounds)
            previousBest = prefs.highscore(key)
            newHighscore = g.score > previousBest && g.score > 0
            if newHighscore { prefs.setHighscore(key, g.score) }
            screen = .results
        } else {
            startRound(g.round + 1)
        }
    }

    func playAgain() {
        guard let g = game else { return }
        startGame(g.playlist)
    }

    func quitGame() {
        cancelPlay()
        Task { await player.stop() }
        game = nil
        screen = .playlists
        if playlists.isEmpty { loadPlaylists() }
    }

    func backToPlaylists() {
        game = nil
        screen = .playlists
    }

    func search(_ query: String) -> [Song] {
        guard let g = game else { return [] }
        let tokens = normalize(query).split(separator: " ").map(String.init)
        guard let first = tokens.first else { return [] }
        var seen = Set<String>()
        let hits = g.allSongs.filter { s in tokens.allSatisfy { s.searchKey.contains($0) } }
            .filter { seen.insert($0.matchKey).inserted }
        return Array(hits.sorted { a, b in
            let pa = normalize(a.name).hasPrefix(first) ? 0 : 1
            let pb = normalize(b.name).hasPrefix(first) ? 0 : 1
            return pa < pb
        }.prefix(8))
    }

    // MARK: Lebenszyklus

    func handleOpenURL(_ url: URL) {
        _ = player.handleOpenURL(url)
    }

    func onBackground() {
        guard let g = game else { return }
        cancelPlay()
        Task { await player.stop() }
        if g.phase == .playing { setPhase(.ready) }
    }

    func onForeground() {
        VolumeController.shared.restoreIfNeeded()
    }

    private func handleDisconnect() {
        // Wird beim nächsten Abspielen automatisch neu verbunden.
        if screen == .game, game?.phase == .playing {
            cancelPlay()
            setPhase(.ready)
        }
    }

    private func cancelPlay() {
        playSeq += 1
        playTask?.cancel()
        playTask = nil
        audible = false
    }

    // MARK: Fehlertexte

    func friendly(_ error: Error) -> String {
        if let e = error as? AppError { return e.errorDescription ?? "Fehler" }
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain { return "Keine Internetverbindung?" }
        let text = ns.localizedDescription
        if text.lowercased().contains("authoriz") {
            return "Spotify hat die App abgelehnt. Prüfe im Developer-Dashboard die iOS Bundle ID (\(Bundle.main.bundleIdentifier ?? "")). (\(text))"
        }
        return text
    }
}
