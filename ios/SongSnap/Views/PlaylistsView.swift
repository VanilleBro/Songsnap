import SwiftUI

struct PlaylistsView: View {
    @ObservedObject var model: GameModel
    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                LogoView(size: 32)
                Spacer()
                Button { model.loadPlaylists() } label: {
                    Image(systemName: "arrow.clockwise").font(.system(size: 18, weight: .semibold))
                }
                .padding(8)
                Button { model.logout() } label: {
                    Image(systemName: "rectangle.portrait.and.arrow.right").font(.system(size: 18, weight: .semibold))
                }
                .padding(8)
            }
            .foregroundColor(Palette.textDim)
            .padding(.horizontal, 20)
            .padding(.top, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    settings
                    if model.playlists.isEmpty {
                        Group {
                            if model.playlistsLoading {
                                ProgressView().tint(Palette.green).scaleEffect(1.4)
                            } else {
                                Text("Keine Playlists gefunden.\nTippe oben auf ↻ zum Neuladen.")
                                    .foregroundColor(Palette.textDim)
                                    .multilineTextAlignment(.center)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(40)
                    }
                    LazyVGrid(columns: columns, spacing: 18) {
                        ForEach(Array(model.playlists.enumerated()), id: \.element.id) { index, p in
                            PlaylistCard(playlist: p, index: index, best: model.best(for: p)) {
                                model.startGame(p)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 32)
            }
        }
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Modus").font(.system(size: 13, weight: .bold)).foregroundColor(Palette.textDim)
            ModeToggle(mode: model.mode) { model.selectMode($0) }
            Text(model.mode == .beginning
                 ? "Jeder Ausschnitt startet ganz am Anfang des Songs."
                 : "Jeder Song startet an einer zufälligen Stelle – deutlich schwerer!")
                .font(.system(size: 13))
                .foregroundColor(Palette.textDim)
                .frame(maxWidth: .infinity)
                .multilineTextAlignment(.center)
                .id(model.mode)
                .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity), removal: .opacity))
                .animation(.easeOut(duration: 0.25), value: model.mode)

            Text("Songs pro Runde")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Palette.textDim)
                .padding(.top, 12)
            HStack(spacing: 10) {
                ForEach([5, 10, 15, 20], id: \.self) { n in
                    let selected = model.rounds == n
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { model.selectRounds(n) }
                    } label: {
                        Text("\(n)")
                            .font(.system(size: 16, weight: .heavy, design: .rounded))
                            .foregroundColor(selected ? .black : Palette.text)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(selected ? Palette.green : Palette.surfaceHi)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
                    }
                    .buttonStyle(BouncyButtonStyle())
                }
            }
            Text("Wähle eine Playlist")
                .font(.system(size: 22, weight: .black, design: .rounded))
                .foregroundColor(Palette.text)
                .padding(.top, 16)
                .padding(.bottom, 10)
        }
        .padding(.top, 8)
    }
}

private struct PlaylistCard: View {
    let playlist: Playlist
    let index: Int
    let best: Int
    let action: () -> Void
    @State private var appeared = false

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                ZStack {
                    if playlist.isLiked {
                        LinearGradient(colors: [Color(red: 0.26, green: 0.22, blue: 0.79), Palette.violet, Color(red: 0.77, green: 0.71, blue: 0.99)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                        Image(systemName: "heart.fill").font(.system(size: 54)).foregroundColor(.white)
                    } else if let url = playlist.imageURL {
                        Color.clear.overlay(
                            AsyncImage(url: url) { img in img.resizable().scaledToFill() } placeholder: { Palette.surfaceHi }
                        )
                    } else {
                        LinearGradient(colors: [Palette.surfaceHi, Palette.violet.opacity(0.6)], startPoint: .top, endPoint: .bottom)
                        Text("♪").font(.system(size: 60)).foregroundColor(.white.opacity(0.8))
                    }
                    LinearGradient(colors: [.clear, .clear, .black.opacity(0.75)], startPoint: .top, endPoint: .bottom)
                    VStack {
                        HStack {
                            if best > 0 {
                                Text("🏆 \(best)")
                                    .font(.system(size: 12, weight: .heavy))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(Palette.amber)
                                    .clipShape(Capsule())
                            }
                            Spacer()
                            if !playlist.accessible {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 13))
                                    .foregroundColor(.white)
                                    .frame(width: 28, height: 28)
                                    .background(Circle().fill(.black.opacity(0.6)))
                            }
                        }
                        Spacer()
                        HStack {
                            Text(playlist.trackCount >= 0 ? "\(playlist.trackCount) Songs" : "")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                        }
                    }
                    .padding(9)
                }
                .aspectRatio(1, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                Text(playlist.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Palette.text)
                    .lineLimit(1)
                    .padding(.top, 4)
                Text(playlist.owner)
                    .font(.system(size: 12))
                    .foregroundColor(Palette.textDim)
                    .lineLimit(1)
            }
        }
        .buttonStyle(BouncyButtonStyle())
        .opacity((appeared ? 1 : 0) * (playlist.accessible ? 1 : 0.55))
        .offset(y: appeared ? 0 : 60)
        .scaleEffect(appeared ? 1 : 0.85)
        .onAppear {
            withAnimation(.spring(response: 0.55, dampingFraction: 0.62).delay(Double(index % 10) * 0.055)) {
                appeared = true
            }
        }
    }
}
