import SwiftUI

struct GameView: View {
    @ObservedObject var model: GameModel
    @State private var query = ""
    @FocusState private var focused: Bool

    var body: some View {
        if let g = model.game {
            ZStack {
                VStack(spacing: 0) {
                    topBar(g)
                    StageBar(stage: g.stage, phase: g.phase).padding(.top, 14)
                    Spacer(minLength: 8)
                    PlayOrb(
                        phase: g.phase,
                        audible: model.audible,
                        playTick: model.playTick,
                        durationMs: stagesMs[g.stage]
                    ) { model.play() }
                    .modifier(ShakeEffect(animatableData: CGFloat(g.wrongTick)))
                    .animation(.linear(duration: 0.45), value: g.wrongTick)
                    Text(hint(g))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Palette.textDim)
                        .padding(.top, 4)
                        .animation(.easeInOut, value: g.phase)
                    Spacer(minLength: 8)
                    history(g.guesses)
                    inputRow(g).padding(.top, 10).padding(.bottom, 10)
                }
                .padding(.horizontal, 18)

                if !query.trimmingCharacters(in: .whitespaces).isEmpty && g.canGuess {
                    VStack {
                        Spacer()
                        suggestionList(model.search(query))
                            .padding(.horizontal, 18)
                            .padding(.bottom, 84)
                    }
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
                }

                if g.phase == .revealWin || g.phase == .revealLoss {
                    RevealOverlay(game: g) { model.nextRound() }
                        .transition(.opacity)
                        .zIndex(2)
                }

                ConfettiView(trigger: model.confettiTick).zIndex(3)
            }
            .animation(.easeInOut(duration: 0.25), value: g.phase)
            .animation(.easeOut(duration: 0.18), value: query.isEmpty)
            .onChange(of: g.round) { _ in query = "" }
            .onChange(of: g.wrongTick) { _ in
                UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            }
            .onChange(of: g.phase) { p in
                if p == .revealWin || p == .revealLoss { focused = false }
            }
        }
    }

    private func hint(_ g: GameState) -> String {
        switch g.phase {
        case .preparing: return "Song wird vorbereitet …"
        case .ready: return "Tippe zum Abspielen · \(stageLabels[g.stage])"
        case .playing: return "Hör genau hin …"
        case .revealWin, .revealLoss: return " "
        }
    }

    private func topBar(_ g: GameState) -> some View {
        HStack {
            Button { model.quitGame() } label: {
                Image(systemName: "xmark").font(.system(size: 18, weight: .bold)).foregroundColor(Palette.textDim)
                    .frame(width: 40, height: 40)
            }
            VStack(alignment: .leading, spacing: 1) {
                Text("Song \(g.round + 1) / \(g.songs.count)")
                    .font(.system(size: 17, weight: .heavy, design: .rounded))
                    .foregroundColor(Palette.text)
                Text(g.playlist.name).font(.system(size: 12)).foregroundColor(Palette.textDim).lineLimit(1)
            }
            Spacer()
            Text("★ \(g.score)")
                .font(.system(size: 16, weight: .black, design: .rounded))
                .foregroundColor(.white)
                .contentTransition(.numericText())
                .animation(.spring(), value: g.score)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(LinearGradient(colors: [Palette.violet, Palette.pink], startPoint: .leading, endPoint: .trailing))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .padding(.top, 6)
    }

    private func history(_ guesses: [GuessEntry]) -> some View {
        VStack(spacing: 6) {
            ForEach(guesses.suffix(4)) { entry in
                let style: (icon: String, color: Color) = {
                    switch entry.kind {
                    case .wrong: return ("✕", Palette.red)
                    case .skipped: return ("»", Palette.textDim)
                    case .correct: return ("✓", Palette.green)
                    }
                }()
                let color = style.color
                HStack(spacing: 10) {
                    Text(style.icon).font(.system(size: 14, weight: .black)).foregroundColor(color)
                    Text(entry.text).font(.system(size: 13)).foregroundColor(Palette.text).lineLimit(1)
                    Spacer()
                    Text(stageLabels[entry.stage]).font(.system(size: 11, weight: .bold)).foregroundColor(Palette.textDim)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(color.opacity(0.13))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(color.opacity(0.35), lineWidth: 1))
                .transition(.asymmetric(insertion: .move(edge: .leading).combined(with: .opacity), removal: .opacity))
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.7), value: guesses.count)
    }

    private func inputRow(_ g: GameState) -> some View {
        let last = g.stage >= stagesMs.count - 1
        return HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundColor(Palette.textDim)
                TextField("Song oder Künstler …", text: $query)
                    .foregroundColor(Palette.text)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .focused($focused)
                    .onSubmit {
                        if let first = model.search(query).first { pick(first) }
                    }
                if !query.isEmpty {
                    Button { query = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundColor(Palette.textDim)
                    }
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 56)
            .background(Palette.surfaceHi.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(focused ? Palette.violet : Palette.stroke, lineWidth: 1))
            .disabled(!g.canGuess)
            .opacity(g.canGuess ? 1 : 0.6)

            Button { model.skip() } label: {
                VStack(spacing: 0) {
                    if !last {
                        Text("Skip").font(.system(size: 10, weight: .bold)).foregroundColor(Palette.textDim)
                    }
                    Text(last ? "Auf-\ngeben" : "+" + formatSeconds(stagesMs[g.stage + 1] - stagesMs[g.stage]))
                        .font(.system(size: last ? 12 : 15, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                .frame(width: 74, height: 56)
                .background(last ? Palette.red.opacity(0.85) : Palette.surfaceHi)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
            }
            .buttonStyle(BouncyButtonStyle())
            .disabled(!g.canGuess)
            .opacity(g.canGuess ? 1 : 0.4)
        }
    }

    private func pick(_ s: Song) {
        model.guess(s)
        query = ""
        focused = false
    }

    private func suggestionList(_ songs: [Song]) -> some View {
        Group {
            if songs.isEmpty {
                Text("Kein Treffer in dieser Playlist")
                    .font(.system(size: 14))
                    .foregroundColor(Palette.textDim)
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(songs) { s in
                            Button { pick(s) } label: {
                                HStack(spacing: 12) {
                                    CoverImage(url: s.imageURL, corner: 8).frame(width: 42, height: 42)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(s.name).font(.system(size: 14, weight: .bold)).foregroundColor(Palette.text).lineLimit(1)
                                        Text(s.artistText).font(.system(size: 12)).foregroundColor(Palette.textDim).lineLimit(1)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .frame(maxHeight: 320)
                .fixedSize(horizontal: false, vertical: songs.count <= 5)
            }
        }
        .background(Color(red: 0.106, green: 0.106, blue: 0.165))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
        .shadow(color: .black.opacity(0.5), radius: 20)
    }
}

struct RevealOverlay: View {
    let game: GameState
    let onNext: () -> Void
    @State private var appeared = false

    var body: some View {
        let song = game.current
        let win = game.phase == .revealWin
        ZStack {
            Color.black.opacity(0.78).ignoresSafeArea()
            VStack(spacing: 0) {
                Text(win ? "Richtig! 🎉" : "Leider nicht 😢")
                    .font(.system(size: 32, weight: .black, design: .rounded))
                    .foregroundColor(win ? Palette.green : Palette.red)
                    .scaleEffect(appeared ? 1 : 0.4)
                    .opacity(appeared ? 1 : 0)

                ZStack {
                    VinylView(imageURL: song.imageURL, period: 2)
                        .frame(width: 205, height: 205)
                        .offset(x: appeared ? 62 : 0)
                        .opacity(appeared ? 1 : 0)
                    CoverImage(url: song.imageURL, corner: 18)
                        .frame(width: 215, height: 215)
                        .shadow(color: .black.opacity(0.6), radius: 24)
                        .offset(x: appeared ? -34 : 0)
                        .rotationEffect(.degrees(appeared ? 0 : 25))
                        .scaleEffect(appeared ? 1 : 0.6)
                }
                .frame(height: 230)
                .padding(.top, 22)

                Text(song.name)
                    .font(.system(size: 24, weight: .heavy, design: .rounded))
                    .foregroundColor(Palette.text)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.top, 24)
                Text(song.artistText)
                    .font(.system(size: 16))
                    .foregroundColor(Palette.textDim)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)

                Group {
                    if win {
                        Text("+\(appeared ? game.lastPoints : 0) Punkte · nach \(stageLabels[game.stage])")
                            .font(.system(size: 17, weight: .black, design: .rounded))
                            .foregroundColor(Palette.green)
                            .contentTransition(.numericText())
                            .padding(.horizontal, 18)
                            .padding(.vertical, 10)
                            .background(Palette.green.opacity(0.16))
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(Palette.green.opacity(0.5), lineWidth: 1))
                    } else {
                        Text("Auch 16 Sekunden haben nicht gereicht.")
                            .font(.system(size: 14))
                            .foregroundColor(Palette.textDim)
                    }
                }
                .padding(.top, 18)
                .scaleEffect(appeared ? 1 : 0.3)
                .opacity(appeared ? 1 : 0)

                GradientButton(title: game.isLastRound ? "Ergebnis ansehen 🏁" : "Nächster Song →", action: onNext)
                    .padding(.top, 30)
            }
            .padding(26)
        }
        .onAppear {
            withAnimation(.spring(response: 0.7, dampingFraction: 0.55)) { appeared = true }
        }
    }
}
