import SwiftUI
import UIKit

private struct CopyRow: View {
    let label: String
    let value: String
    @State private var copied = false

    var body: some View {
        Button {
            UIPasteboard.general.string = value
            withAnimation { copied = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { withAnimation { copied = false } }
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(label).font(.system(size: 12)).foregroundColor(Palette.textDim)
                    Spacer()
                    Text(copied ? "✓ kopiert" : "kopieren")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(copied ? Palette.green : Palette.violet)
                }
                Text(value)
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundColor(Palette.text)
                    .multilineTextAlignment(.leading)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.black.opacity(0.35))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct StepTitle: View {
    let n: Int
    let text: String

    var body: some View {
        HStack(spacing: 10) {
            Text("\(n)")
                .font(.system(size: 13, weight: .black))
                .foregroundColor(.white)
                .frame(width: 26, height: 26)
                .background(Circle().fill(Palette.violet))
            Text(text).font(.system(size: 16, weight: .bold)).foregroundColor(Palette.text)
        }
    }
}

struct SetupView: View {
    @ObservedObject var model: GameModel
    @State private var clientID = ""
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                LogoView(size: 38)
                Text("Einmalige Einrichtung (ca. 3 Minuten). Du brauchst Spotify Premium und die Spotify-App auf diesem iPhone. Hast du schon eine Spotify-Entwickler-App (z. B. für die Android-Version)? Dann nimm einfach dieselbe.")
                    .font(.system(size: 14))
                    .foregroundColor(Palette.textDim)

                GlassCard {
                    VStack(alignment: .leading, spacing: 8) {
                        StepTitle(n: 1, text: "Spotify-App öffnen oder anlegen")
                        Text("Im Developer-Dashboard deine App öffnen (oder „Create app“). Unter „Which API/SDKs“ zusätzlich iOS anhaken.")
                            .font(.system(size: 13)).foregroundColor(Palette.textDim)
                        GradientButton(title: "Dashboard öffnen ↗", colors: [Palette.violet, Palette.pink], textColor: .white, height: 48) {
                            openURL(URL(string: "https://developer.spotify.com/dashboard")!)
                        }
                    }
                }

                GlassCard {
                    VStack(alignment: .leading, spacing: 6) {
                        StepTitle(n: 2, text: "Diese Werte eintragen")
                        Text("Antippen zum Kopieren, dann Save.").font(.system(size: 13)).foregroundColor(Palette.textDim)
                        CopyRow(label: "Redirect URI", value: SpotifyAuth.redirectURI)
                        CopyRow(label: "iOS Bundle ID", value: Bundle.main.bundleIdentifier ?? "com.songsnap.game")
                    }
                }

                GlassCard {
                    VStack(alignment: .leading, spacing: 10) {
                        StepTitle(n: 3, text: "Client ID einfügen")
                        TextField("z. B. 1a2b3c4d5e6f…", text: $clientID)
                            .font(.system(size: 15, design: .monospaced))
                            .foregroundColor(Palette.text)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(14)
                            .background(Color.black.opacity(0.3))
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
                        Text("Tipp: Unter „User Management“ deinen Spotify-Account eintragen, falls der Login mit „403“ scheitert.")
                            .font(.system(size: 12)).foregroundColor(Palette.textDim)
                    }
                }

                GradientButton(title: "Speichern & weiter", enabled: !clientID.trimmingCharacters(in: .whitespaces).isEmpty) {
                    model.saveClientID(clientID)
                }
                if !model.prefs.clientID.isEmpty {
                    Button("Zurück") { model.leaveSetup() }
                        .foregroundColor(Palette.textDim)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 18)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear { clientID = model.prefs.clientID }
    }
}

struct LoginView: View {
    @ObservedObject var model: GameModel
    @State private var appeared = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VinylView(period: 3.2)
                .frame(width: 230, height: 230)
                .scaleEffect(appeared ? 1 : 0.5)
                .rotationEffect(.degrees(appeared ? 0 : -120))
                .opacity(appeared ? 1 : 0)
            LogoView(size: 46).padding(.top, 34)
            Text("Erkennst du deine Songs in 0,1 Sekunden?")
                .font(.system(size: 16))
                .foregroundColor(Palette.textDim)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            Spacer()
            GradientButton(title: "Mit Spotify verbinden") { model.login() }
            Button("Einrichtung / Client-ID ändern") { model.openSetup() }
                .font(.system(size: 13))
                .foregroundColor(Palette.violet)
                .padding(.top, 14)
        }
        .padding(28)
        .onAppear {
            withAnimation(.spring(response: 0.9, dampingFraction: 0.6)) { appeared = true }
        }
    }
}

struct LoadingView: View {
    let message: String

    var body: some View {
        VStack(spacing: 30) {
            VinylView(period: 1.4).frame(width: 170, height: 170)
            Text(message)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Palette.text)
                .multilineTextAlignment(.center)
                .animation(.easeInOut, value: message)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
