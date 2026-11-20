import SwiftUI

struct ResultsView: View {
    @ObservedObject var model: GameModel
    @State private var shownScore = 0
    @State private var appeared = false

    var body: some View {
        if let g = model.game {
            let maxScore = max(1, g.songs.count * stagePoints[0])
            let solved = g.results.filter { $0.solvedStage != nil }.count
            let pct = Double(g.score) / Double(maxScore)
            let rating: (emoji: String, title: String) = {
                if pct >= 0.8 { return ("🧠", "Musik-Genie!") }
                if pct >= 0.6 { return ("🔥", "Richtig stark!") }
                if pct >= 0.4 { return ("🎧", "Solide Ohren!") }
                if pct >= 0.2 { return ("🎶", "Da geht noch was") }
                return ("💪", "Übung macht den Meister")
            }()

            ZStack {
                ScrollView {
                    VStack(spacing: 10) {
                        TimelineView(.animation) { ctx in
                            Text(rating.emoji)
                                .font(.system(size: 76))
                                .offset(y: 8 * sin(ctx.date.timeIntervalSinceReferenceDate * 2 * .pi / 2.4))
                        }
                        .frame(height: 100)
                        Text(rating.title).font(.system(size: 26, weight: .black, design: .rounded)).foregroundColor(Palette.text)
                        GradientText(text: "\(shownScore)", size: 72)
                            .contentTransition(.numericText())
                        Text("von \(maxScore) Punkten").font(.system(size: 14)).foregroundColor(Palette.textDim)

                        if model.newHighscore {
                            TimelineView(.animation) { ctx in
                                Text("🏆 NEUER HIGHSCORE!")
                                    .font(.system(size: 15, weight: .black))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 7)
                                    .background(Palette.amber)
                                    .clipShape(Capsule())
                                    .scaleEffect(1.05 + 0.05 * sin(ctx.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.2))
                            }
                            .padding(.top, 6)
                        } else if model.previousBest > 0 {
                            Text("Bestwert: \(model.previousBest)").font(.system(size: 13)).foregroundColor(Palette.textDim)
                        }

                        HStack(spacing: 10) {
                            statChip("\(solved) / \(g.songs.count)", "erkannt")
                            statChip(g.mode == .beginning ? "⏮" : "🎲", g.mode == .beginning ? "Beginning" : "Random")
                        }
                        .padding(.vertical, 12)

                        ForEach(Array(g.results.enumerated()), id: \.element.id) { index, r in
                            resultRow(r)
                                .opacity(appeared ? 1 : 0)
                                .offset(x: appeared ? 0 : 160)
                                .animation(.spring(response: 0.5, dampingFraction: 0.65).delay(0.6 + Double(index) * 0.09), value: appeared)
                        }

                        GradientButton(title: "Nochmal spielen 🔁") { model.playAgain() }
                            .padding(.top, 16)
                        GradientButton(title: "Andere Playlist", colors: [Palette.surfaceHi, Palette.surfaceHi], textColor: Palette.text) {
                            model.backToPlaylists()
                        }
                        .padding(.top, 4)
                    }
                    .padding(.horizontal, 22)
                    .padding(.vertical, 20)
                }
                if model.newHighscore { ConfettiView(trigger: 1) }
            }
            .onAppear {
                appeared = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                    withAnimation(.easeOut(duration: 1.6)) { shownScore = g.score }
                }
            }
        }
    }

    private func statChip(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 18, weight: .black, design: .rounded)).foregroundColor(Palette.text)
            Text(label).font(.system(size: 11)).foregroundColor(Palette.textDim)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 10)
        .background(Palette.surfaceHi.opacity(0.8))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
    }

    private func resultRow(_ r: RoundResult) -> some View {
        let ok = r.solvedStage != nil
        let accent = ok ? Palette.green : Palette.red
        return HStack(spacing: 12) {
            CoverImage(url: r.song.imageURL, corner: 10).frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(r.song.name).font(.system(size: 14, weight: .bold)).foregroundColor(Palette.text).lineLimit(1)
                Text(r.song.artistText).font(.system(size: 12)).foregroundColor(Palette.textDim).lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(ok ? stageLabels[r.solvedStage ?? 0] : "✕")
                    .font(.system(size: 15, weight: .black, design: .rounded))
                    .foregroundColor(accent)
                Text(ok ? "+\(r.points)" : "0").font(.system(size: 11)).foregroundColor(Palette.textDim)
            }
        }
        .padding(10)
        .background(Palette.surfaceHi.opacity(0.75))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(accent.opacity(0.3), lineWidth: 1))
    }
}
