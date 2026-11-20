import Foundation

final class Prefs {
    private let d = UserDefaults.standard

    var clientID: String {
        get { d.string(forKey: "client_id") ?? "" }
        set { d.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "client_id") }
    }

    var accessToken: String? {
        get { d.string(forKey: "access_token") }
        set { d.set(newValue, forKey: "access_token") }
    }

    var refreshToken: String? {
        get { d.string(forKey: "refresh_token") }
        set { d.set(newValue, forKey: "refresh_token") }
    }

    var expiresAt: Date {
        get { Date(timeIntervalSince1970: d.double(forKey: "expires_at")) }
        set { d.set(newValue.timeIntervalSince1970, forKey: "expires_at") }
    }

    var remoteToken: String? {
        get { d.string(forKey: "remote_token") }
        set { d.set(newValue, forKey: "remote_token") }
    }

    var mode: String {
        get { d.string(forKey: "mode") ?? "beginning" }
        set { d.set(newValue, forKey: "mode") }
    }

    var rounds: Int {
        get { let v = d.integer(forKey: "rounds"); return v == 0 ? 10 : v }
        set { d.set(newValue, forKey: "rounds") }
    }

    func highscore(_ key: String) -> Int { d.integer(forKey: "hs_" + key) }
    func setHighscore(_ key: String, _ value: Int) { d.set(value, forKey: "hs_" + key) }

    func clearAuth() {
        d.removeObject(forKey: "access_token")
        d.removeObject(forKey: "refresh_token")
        d.removeObject(forKey: "expires_at")
        d.removeObject(forKey: "remote_token")
    }
}
