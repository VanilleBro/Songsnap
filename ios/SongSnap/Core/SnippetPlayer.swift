import Foundation
import SpotifyiOS
import UIKit

/// Stellt sicher, dass eine Continuation genau einmal fortgesetzt wird.
final class Once<T> {
    var cont: CheckedContinuation<T, Error>?

    func resume(returning value: T) {
        cont?.resume(returning: value)
        cont = nil
    }

    func resume(throwing error: Error) {
        cont?.resume(throwing: error)
        cont = nil
    }
}

/// Leitet die (nicht-isolierten) SDK-Delegate-Aufrufe auf den Main-Actor weiter.
final class RemoteDelegateProxy: NSObject, SPTAppRemoteDelegate, SPTAppRemotePlayerStateDelegate {
    weak var owner: SnippetPlayer?

    func appRemoteDidEstablishConnection(_ appRemote: SPTAppRemote) {
        Task { @MainActor [weak owner] in owner?.didConnect() }
    }

    func appRemote(_ appRemote: SPTAppRemote, didFailConnectionAttemptWithError error: Error?) {
        Task { @MainActor [weak owner] in owner?.didFailConnect(error) }
    }

    func appRemote(_ appRemote: SPTAppRemote, didDisconnectWithError error: Error?) {
        Task { @MainActor [weak owner] in owner?.didDisconnect(error) }
    }

    func playerStateDidChange(_ playerState: SPTAppRemotePlayerState) {
        let at = Date()
        Task { @MainActor [weak owner] in owner?.didReceive(playerState, at: at) }
    }
}

/**
 Spielt exakte Ausschnitte über die Spotify-App ab.

 iOS-Besonderheiten:
 - Die Spotify-App wird von iOS eingefroren, wenn sie nichts abspielt. Deshalb läuft der Song
   während des Spiels stumm weiter (System-Lautstärke 0) – so bleibt die Verbindung bestehen.
 - Hörbar wird es nur für genau die Ausschnittlänge (Lautstärke kurz auf deinen Wert).
 */
@MainActor
final class SnippetPlayer {
    static let leadInMs = 450

    private struct Snap {
        let state: SPTAppRemotePlayerState
        let at: Date
        let seq: Int
    }

    private var appRemote: SPTAppRemote?
    private var configuredClientID = ""
    private let proxy = RemoteDelegateProxy()
    private var connectOnce: Once<Void>?
    private var authOnce: Once<String>?
    private var lastEvent: Snap?
    private var eventSeq = 0
    private let volume: VolumeController
    private let prefs = Prefs()

    var onUnexpectedDisconnect: (() -> Void)?

    var isConnected: Bool { appRemote?.isConnected ?? false }

    init() {
        volume = VolumeController.shared
        proxy.owner = self
    }

    // MARK: Verbindung

    private func configure(clientID: String) -> SPTAppRemote {
        if let r = appRemote, configuredClientID == clientID { return r }
        let config = SPTConfiguration(clientID: clientID, redirectURL: URL(string: SpotifyAuth.redirectURI)!)
        let r = SPTAppRemote(configuration: config, logLevel: .error)
        r.delegate = proxy
        appRemote = r
        configuredClientID = clientID
        return r
    }

    /// Verbindet mit der Spotify-App. Falls nötig, wird kurz zur Spotify-App gewechselt
    /// (dort startet [wakeURI] stumm), danach geht es automatisch zurück.
    func ensureConnected(clientID: String, wakeURI: String) async throws {
        let remote = configure(clientID: clientID)
        if remote.isConnected { return }

        if let token = prefs.remoteToken {
            remote.connectionParameters.accessToken = token
            if (try? await connect(remote, timeout: 4)) != nil { return }
        }

        volume.mute()
        let token = try await authorize(remote, uri: wakeURI)
        prefs.remoteToken = token
        remote.connectionParameters.accessToken = token
        try await connect(remote, timeout: 12)
    }

    private func connect(_ remote: SPTAppRemote, timeout: Double) async throws {
        let box = Once<Void>()
        connectOnce = box
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            box.cont = cont
            remote.connect()
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                box.resume(throwing: AppError.message("Die Spotify-App reagiert nicht. Öffne sie einmal und versuche es erneut."))
            }
        }
    }

    private func authorize(_ remote: SPTAppRemote, uri: String) async throws -> String {
        let box = Once<String>()
        authOnce = box
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<String, Error>) in
            box.cont = cont
            remote.authorizeAndPlayURI(uri) { installed in
                if !installed {
                    box.resume(throwing: AppError.message("Die Spotify-App muss auf dem iPhone installiert sein."))
                }
            }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 180_000_000_000)
                box.resume(throwing: AppError.message("Keine Antwort von der Spotify-App erhalten."))
            }
        }
    }

    /// Rückleitung aus der Spotify-App (songsnap://callback#access_token=…).
    func handleOpenURL(_ url: URL) -> Bool {
        guard let remote = appRemote, let params = remote.authorizationParameters(from: url) else { return false }
        if let token = params[SPTAppRemoteAccessTokenKey] {
            authOnce?.resume(returning: token)
            return true
        }
        if let err = params[SPTAppRemoteErrorDescriptionKey] {
            authOnce?.resume(throwing: AppError.message("Spotify hat die Verbindung abgelehnt: \(err)"))
            return true
        }
        return false
    }

    func didConnect() {
        connectOnce?.resume(returning: ())
        appRemote?.playerAPI?.delegate = proxy
        appRemote?.playerAPI?.subscribe(toPlayerState: { _, _ in })
    }

    func didFailConnect(_ error: Error?) {
        connectOnce?.resume(throwing: error ?? AppError.notConnected)
    }

    func didDisconnect(_ error: Error?) {
        onUnexpectedDisconnect?()
    }

    func didReceive(_ state: SPTAppRemotePlayerState, at: Date) {
        eventSeq += 1
        lastEvent = Snap(state: state, at: at, seq: eventSeq)
    }

    func disconnect() {
        if appRemote?.isConnected == true { appRemote?.disconnect() }
    }

    // MARK: Hilfen

    private func requireAPI() throws -> SPTAppRemotePlayerAPI {
        guard let r = appRemote, r.isConnected, let api = r.playerAPI else { throw AppError.notConnected }
        return api
    }

    private func call(_ timeout: Double = 6, _ f: (@escaping SPTAppRemoteCallback) -> Void) async throws -> Any? {
        let box = Once<Any?>()
        return try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Any?, Error>) in
            box.cont = cont
            f { result, error in
                if let error {
                    box.resume(throwing: error)
                } else {
                    box.resume(returning: result)
                }
            }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                box.resume(throwing: AppError.message("Die Spotify-App antwortet nicht."))
            }
        }
    }

    private func fetchState() async -> Snap? {
        guard let api = try? requireAPI() else { return nil }
        guard let result = try? await call(3, { cb in api.getPlayerState(cb) }),
              let state = result as? SPTAppRemotePlayerState
        else { return nil }
        return Snap(state: state, at: Date(), seq: -1)
    }

    private func waitFor(_ timeout: Double, after minSeq: Int = -1, _ pred: (SPTAppRemotePlayerState) -> Bool) async -> Snap? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline && !Task.isCancelled {
            if let ev = lastEvent, ev.seq > minSeq, pred(ev.state) { return ev }
            if let s = await fetchState(), pred(s.state) { return s }
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
        return nil
    }

    private func sleep(ms: Int) async throws {
        if ms > 0 { try await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000) }
    }

    // MARK: Spiel-Aktionen

    /// Song stumm laden. Er läuft danach stumm weiter (hält die Spotify-App wach).
    func prepare(_ song: Song) async throws {
        let api = try requireAPI()
        volume.mute()
        let seq0 = eventSeq
        _ = try await call { cb in api.play(song.uri, callback: cb) }
        if await waitFor(8, after: seq0, { $0.track.uri == song.uri }) == nil {
            if let s = await fetchState(), !s.state.playbackRestrictions.canSeek {
                throw AppError.premiumRequired
            }
            throw AppError.trackUnavailable
        }
    }

    /// Spielt [durationMs] ab [startMs] hörbar ab.
    func playSnippet(_ song: Song, startMs: Int, durationMs: Int, onAudible: (Bool) -> Void) async throws {
        let api = try requireAPI()
        volume.mute()
        defer {
            volume.mute()
            onAudible(false)
        }
        if let s = await fetchState(), s.state.track.uri != song.uri {
            try await prepare(song)
        }

        if startMs >= Self.leadInMs {
            // Stumm etwas früher starten, Ton exakt an der Startposition freigeben
            let seekPos = startMs - Self.leadInMs
            let seq0 = eventSeq
            _ = try await call { cb in api.seek(toPosition: seekPos, callback: cb) }
            _ = try? await call { cb in api.resume(cb) }
            guard let snap = await waitFor(4, after: seq0, { !$0.isPaused && abs($0.playbackPosition - seekPos) < 2_000 })
            else { throw AppError.message("Spotify startet die Wiedergabe nicht.") }
            let posNow = { snap.state.playbackPosition + Int(Date().timeIntervalSince(snap.at) * 1000) }
            try await sleep(ms: startMs - posNow())
            volume.unmute()
            onAudible(true)
            try await sleep(ms: durationMs)
            volume.mute()
        } else {
            // Songanfang: pausieren, an den Start springen, Ton an, losspielen
            _ = try await call { cb in api.pause(cb) }
            _ = try await call { cb in api.seek(toPosition: startMs, callback: cb) }
            try await sleep(ms: 80)
            volume.unmute()
            let seq0 = eventSeq
            _ = try await call { cb in api.resume(cb) }
            guard let snap = await waitFor(4, after: seq0, { !$0.isPaused })
            else { throw AppError.message("Spotify startet die Wiedergabe nicht.") }
            onAudible(true)
            let posNow = snap.state.playbackPosition + Int(Date().timeIntervalSince(snap.at) * 1000)
            try await sleep(ms: durationMs - max(0, posNow - startMs))
            volume.mute()
        }
    }

    /// Nach der Runde: Song hörbar ab der Startposition weiterspielen.
    func reveal(_ song: Song, startMs: Int) async throws {
        let api = try requireAPI()
        if let s = await fetchState(), s.state.track.uri != song.uri {
            _ = try await call { cb in api.play(song.uri, callback: cb) }
            _ = await waitFor(6, { $0.track.uri == song.uri })
        }
        _ = try await call { cb in api.seek(toPosition: startMs, callback: cb) }
        _ = try? await call { cb in api.resume(cb) }
        volume.unmute()
    }

    /// Spiel beendet / App verlassen: anhalten und Lautstärke zurück.
    func stop() async {
        if let api = try? requireAPI() {
            _ = try? await call(2) { cb in api.pause(cb) }
        }
        volume.unmute()
    }
}
