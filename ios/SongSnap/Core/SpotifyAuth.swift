import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

struct TokenResult {
    let accessToken: String
    let refreshToken: String?
    let expiresIn: Int
}

/// Authorization Code Flow mit PKCE – kein Client-Secret nötig.
enum SpotifyAuth {
    static let redirectURI = "songsnap://callback"
    static let callbackScheme = "songsnap"
    static let scopes = [
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
        "user-read-private",
        "app-remote-control",
        "user-read-playback-state",
        "user-modify-playback-state",
    ]
    private static let tokenURL = URL(string: "https://accounts.spotify.com/api/token")!

    static func randomString(bytes: Int) -> String {
        var data = Data(count: bytes)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, bytes, $0.baseAddress!) }
        return base64url(data)
    }

    static func base64url(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func challenge(for verifier: String) -> String {
        base64url(Data(SHA256.hash(data: Data(verifier.utf8))))
    }

    static func authorizeURL(clientID: String, challenge: String, state: String) -> URL {
        var c = URLComponents(string: "https://accounts.spotify.com/authorize")!
        c.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "scope", value: scopes.joined(separator: " ")),
        ]
        return c.url!
    }

    static func exchange(code: String, clientID: String, verifier: String) async throws -> TokenResult {
        try await postToken([
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": redirectURI,
            "client_id": clientID,
            "code_verifier": verifier,
        ])
    }

    static func refresh(refreshToken: String, clientID: String) async throws -> TokenResult {
        try await postToken([
            "grant_type": "refresh_token",
            "refresh_token": refreshToken,
            "client_id": clientID,
        ])
    }

    private static func postToken(_ params: [String: String]) async throws -> TokenResult {
        var req = URLRequest(url: tokenURL)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        req.httpBody = params.map { k, v in
            "\(k)=\(v.addingPercentEncoding(withAllowedCharacters: allowed) ?? v)"
        }.joined(separator: "&").data(using: .utf8)
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let body = String(data: data, encoding: .utf8) ?? ""
        guard (200...299).contains(code) else { throw AppError.api(code, body) }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["access_token"] as? String
        else { throw AppError.message("Ungültige Antwort von Spotify.") }
        return TokenResult(
            accessToken: access,
            refreshToken: json["refresh_token"] as? String,
            expiresIn: json["expires_in"] as? Int ?? 3600
        )
    }
}

/// Öffnet den Spotify-Login in einem sicheren Browserfenster und fängt die Rückleitung ab.
final class WebAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    @MainActor
    func start(url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<URL, Error>) in
            let s = ASWebAuthenticationSession(url: url, callbackURLScheme: SpotifyAuth.callbackScheme) { callbackURL, error in
                if let callbackURL {
                    cont.resume(returning: callbackURL)
                } else if let err = error as? ASWebAuthenticationSessionError, err.code == .canceledLogin {
                    cont.resume(throwing: AppError.message("Login abgebrochen."))
                } else {
                    cont.resume(throwing: error ?? AppError.message("Login fehlgeschlagen."))
                }
            }
            s.presentationContextProvider = self
            s.prefersEphemeralWebBrowserSession = false
            self.session = s
            if !s.start() {
                cont.resume(throwing: AppError.message("Login konnte nicht gestartet werden."))
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        return windows.first { $0.isKeyWindow } ?? windows.first ?? ASPresentationAnchor()
    }
}
