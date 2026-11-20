import SwiftUI

/// Großer Play-Knopf mit rotierendem Leuchtring und Fortschrittsring.
struct PlayOrb: View {
    let phase: Phase
    let audible: Bool
    let playTick: Int
    let durationMs: Int
    let action: () -> Void

    @State private var progress: CGFloat = 0

    private var ready: Bool { phase == .ready }

    var body: some View {
        TimelineView(.animation) { ctx in
            let t = ctx.date.timeIntervalSinceReferenceDate
            let pulse = ready ? 1 + 0.035 * (1 + sin(t * 2 * .pi / 1.7)) : 1
            let spin = t.truncatingRemainder(dividingBy: 5) / 5 * 360
            ZStack {
                Circle()
                    .fill(RadialGradient(
                        colors: [Palette.green.opacity(0.55), Palette.violet.opacity(0.25), .clear],
                        center: .center, startRadius: 0, endRadius: 125
                    ))
                    .opacity(audible ? 1 : (ready ? 0.55 : 0.25))
                    .animation(.easeOut(duration: 0.25), value: audible)

                Circle()
                    .stroke(
                        AngularGradient(colors: [Palette.green, Palette.cyan, Palette.violet, Palette.pink, Palette.green], center: .center),
                        lineWidth: 4
                    )
                    .frame(width: 206, height: 206)
                    .rotationEffect(.degrees(spin))
                    .opacity(audible ? 1 : 0.6)

                Circle()
                    .stroke(Color.white.opacity(0.1), lineWidth: 7)
                    .frame(width: 184, height: 184)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .frame(width: 184, height: 184)

                Button(action: action) {
                    ZStack {
                        Circle().fill(LinearGradient(colors: [Palette.green, Palette.teal], startPoint: .topLeading, endPoint: .bottomTrailing))
                        switch phase {
                        case .playing:
                            EqualizerView(active: audible).frame(height: 52)
                        case .preparing:
                            ProgressView().tint(.black).scaleEffect(1.6)
                        default:
                            Image(systemName: "play.fill")
                                .font(.system(size: 60, weight: .black))
                                .foregroundColor(.black)
                                .offset(x: 5)
                        }
                    }
                    .frame(width: 150, height: 150)
                    .shadow(color: Palette.green.opacity(0.6), radius: 24)
                }
                .buttonStyle(BouncyButtonStyle())
                .disabled(!ready)
                .scaleEffect(pulse * (audible ? 1.1 : 1))
                .animation(.spring(response: 0.3, dampingFraction: 0.45), value: audible)
            }
            .frame(width: 250, height: 250)
        }
        .onChange(of: playTick) { _ in
            progress = 0
            withAnimation(.linear(duration: Double(durationMs) / 1000)) { progress = 1 }
        }
        .onChange(of: phase) { p in
            if p == .preparing { progress = 0 }
        }
    }
}

/// Die 7 Stufen 0,1 s … 16 s.
struct StageBar: View {
    let stage: Int
    let phase: Phase

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { ctx in
            let glow = 0.8 + 0.2 * sin(ctx.date.timeIntervalSinceReferenceDate * 2 * .pi / 1.3)
            HStack(spacing: 5) {
                ForEach(stageLabels.indices, id: \.self) { i in
                    let current = i == stage
                    let won = phase == .revealWin
                    let lost = phase == .revealLoss
                    let color: Color = {
                        if won && current { return Palette.green }
                        if lost && current { return Palette.red }
                        if i < stage { return Palette.red.opacity(0.55) }
                        if current { return Palette.violet }
                        return Palette.surfaceHi
                    }()
                    Text(stageLabels[i])
                        .font(.system(size: 11, weight: .heavy, design: .rounded))
                        .foregroundColor(i <= stage ? .white : Palette.textDim)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(color)
                        .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                .stroke(current ? Color.white.opacity(0.35) : .clear, lineWidth: 1)
                        )
                        .opacity(current && !won && !lost ? glow : 1)
                        .scaleEffect(current ? 1.08 : 1)
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.5), value: stage)
            .animation(.easeInOut(duration: 0.4), value: phase)
        }
    }
}

/// Schalter Beginning / Random mit gleitender Pille.
struct ModeToggle: View {
    let mode: GameMode
    let onChange: (GameMode) -> Void
    @Namespace private var ns

    var body: some View {
        HStack(spacing: 0) {
            ForEach(GameMode.allCases, id: \.self) { m in
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) { onChange(m) }
                } label: {
                    Text(m == .beginning ? "⏮  Beginning" : "🎲  Random")
                        .font(.system(size: 15, weight: .heavy, design: .rounded))
                        .foregroundColor(mode == m ? .white : Palette.textDim)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background {
                            if mode == m {
                                Capsule()
                                    .fill(LinearGradient(colors: [Palette.violet, Palette.pink], startPoint: .leading, endPoint: .trailing))
                                    .matchedGeometryEffect(id: "pill", in: ns)
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Palette.surfaceHi)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(Palette.stroke, lineWidth: 1))
    }
}

/// Kleine Benachrichtigung oben am Bildschirm.
struct ToastView: View {
    @ObservedObject var model: GameModel

    var body: some View {
        VStack {
            if model.toastVisible {
                Text(model.toastText)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Palette.text)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(red: 0.15, green: 0.15, blue: 0.23))
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
                    .shadow(color: .black.opacity(0.4), radius: 16)
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .onTapGesture { withAnimation { model.toastVisible = false } }
            }
            Spacer()
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.7), value: model.toastVisible)
        .task(id: model.toastTick) {
            guard model.toastVisible else { return }
            try? await Task.sleep(nanoseconds: 4_200_000_000)
            if !Task.isCancelled { model.toastVisible = false }
        }
    }
}
