import SwiftUI

enum Palette {
    static let bg = Color(red: 0.027, green: 0.027, blue: 0.051)
    static let surface = Color(red: 0.07, green: 0.07, blue: 0.11)
    static let surfaceHi = Color(red: 0.114, green: 0.114, blue: 0.173)
    static let stroke = Color.white.opacity(0.12)
    static let green = Color(red: 0.118, green: 0.843, blue: 0.376)
    static let teal = Color(red: 0.078, green: 0.722, blue: 0.651)
    static let violet = Color(red: 0.545, green: 0.361, blue: 0.965)
    static let pink = Color(red: 0.925, green: 0.282, blue: 0.6)
    static let cyan = Color(red: 0.133, green: 0.827, blue: 0.933)
    static let red = Color(red: 1.0, green: 0.302, blue: 0.427)
    static let amber = Color(red: 0.984, green: 0.749, blue: 0.141)
    static let text = Color(red: 0.957, green: 0.957, blue: 0.98)
    static let textDim = Color(red: 0.604, green: 0.604, blue: 0.706)
}

/// Langsam wabernde Farb-Blobs als Hintergrund.
struct AuroraBackground: View {
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { ctx in
            let t = ctx.date.timeIntervalSinceReferenceDate
            Canvas { gc, size in
                let w = size.width
                let h = size.height
                let a = (sin(t * 2 * .pi / 28) + 1) / 2
                let b = (sin(t * 2 * .pi / 38) + 1) / 2
                let c = (sin(t * 2 * .pi / 46) + 1) / 2
                let blobs: [(Color, Double, Double, Double)] = [
                    (Palette.violet, w * (0.10 + 0.70 * a), h * (0.08 + 0.25 * b), w * 0.95),
                    (Palette.green, w * (0.90 - 0.55 * b), h * (0.50 + 0.30 * c), w * 0.80),
                    (Palette.pink, w * (0.25 + 0.55 * c), h * (0.98 - 0.30 * a), w * 0.75),
                ]
                for (color, cx, cy, r) in blobs {
                    let rect = CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)
                    gc.fill(
                        Path(ellipseIn: rect),
                        with: .radialGradient(
                            Gradient(colors: [color.opacity(0.32), .clear]),
                            center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: r
                        )
                    )
                }
            }
        }
        .background(Palette.bg)
        .ignoresSafeArea()
    }
}

/// Text mit wanderndem Farbverlauf.
struct GradientText: View {
    let text: String
    var size: CGFloat = 40
    var weight: Font.Weight = .black

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { ctx in
            let p = ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 5) / 5
            Text(text)
                .font(.system(size: size, weight: weight, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [Palette.green, Palette.cyan, Palette.violet, Palette.pink, Palette.green, Palette.cyan],
                        startPoint: UnitPoint(x: -1 + p * 2, y: 0),
                        endPoint: UnitPoint(x: 1 + p * 2, y: 1)
                    )
                )
        }
    }
}

struct LogoView: View {
    var size: CGFloat = 40
    var body: some View { GradientText(text: "SongSnap", size: size) }
}

/// Drehende Schallplatte, optional mit Cover als Label.
struct VinylView: View {
    var imageURL: URL? = nil
    var period: Double = 2.6

    var body: some View {
        TimelineView(.animation) { ctx in
            let angle = ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period) / period * 360
            GeometryReader { geo in
                let d = min(geo.size.width, geo.size.height)
                ZStack {
                    Circle().fill(Color(red: 0.047, green: 0.047, blue: 0.078))
                    ForEach(0..<10, id: \.self) { i in
                        Circle()
                            .stroke(Color.white.opacity(0.05), lineWidth: 1)
                            .frame(width: d * (0.46 + Double(i) * 0.054), height: d * (0.46 + Double(i) * 0.054))
                    }
                    Circle().fill(
                        AngularGradient(
                            colors: [.clear, .white.opacity(0.10), .clear, .clear, .white.opacity(0.08), .clear],
                            center: .center
                        )
                    )
                    Circle().stroke(Color.white.opacity(0.1), lineWidth: 2)
                    Group {
                        if let imageURL {
                            AsyncImage(url: imageURL) { img in
                                img.resizable().scaledToFill()
                            } placeholder: {
                                LinearGradient(colors: [Palette.green, Palette.violet], startPoint: .topLeading, endPoint: .bottomTrailing)
                            }
                        } else {
                            LinearGradient(colors: [Palette.green, Palette.violet, Palette.pink], startPoint: .topLeading, endPoint: .bottomTrailing)
                        }
                    }
                    .frame(width: d * 0.4, height: d * 0.4)
                    .clipShape(Circle())
                    Circle().fill(Palette.bg).frame(width: 9, height: 9)
                }
                .frame(width: d, height: d)
                .rotationEffect(.degrees(angle))
                .frame(width: geo.size.width, height: geo.size.height)
            }
        }
    }
}

/// Springende Equalizer-Balken.
struct EqualizerView: View {
    var active: Bool
    var color: Color = .black
    var bars = 5

    var body: some View {
        TimelineView(.animation) { ctx in
            let t = ctx.date.timeIntervalSinceReferenceDate
            HStack(alignment: .bottom, spacing: 5) {
                ForEach(0..<bars, id: \.self) { i in
                    let speed = 7.0 + Double(i) * 2.3
                    let h = active ? 0.2 + 0.8 * abs(sin(t * speed + Double(i))) : 0.2
                    GeometryReader { geo in
                        VStack {
                            Spacer(minLength: 0)
                            Capsule().fill(color).frame(height: geo.size.height * h)
                        }
                    }
                    .frame(width: 7)
                }
            }
        }
    }
}

/// Federnder Druck-Effekt für Buttons.
struct BouncyButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.5), value: configuration.isPressed)
    }
}

struct GradientButton: View {
    let title: String
    var colors: [Color] = [Palette.green, Palette.teal]
    var textColor: Color = .black
    var height: CGFloat = 58
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 17, weight: .heavy, design: .rounded))
                .foregroundColor(textColor)
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .background(LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing))
                .clipShape(Capsule())
        }
        .buttonStyle(BouncyButtonStyle())
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

struct GlassCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.surfaceHi.opacity(0.72))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Palette.stroke, lineWidth: 1))
    }
}

/// Konfetti-Explosion, ausgelöst durch einen neuen [trigger]-Wert.
struct ConfettiView: View {
    let trigger: Int
    @State private var start: Date?

    private struct Particle {
        let vx, vy, w, rot, spin, wobble: Double
        let color: Color
    }

    @State private var particles: [Particle] = []
    private let duration = 2.8

    var body: some View {
        TimelineView(.animation) { ctx in
            Canvas { gc, size in
                guard let start else { return }
                let elapsed = ctx.date.timeIntervalSince(start)
                guard elapsed < duration else { return }
                let p = elapsed / duration
                let alpha = max(0, 1 - p * p)
                let ox = size.width / 2
                let oy = size.height * 0.38
                for q in particles {
                    let x = ox + q.vx * size.width * 0.55 * elapsed + sin(elapsed * q.wobble) * 12
                    let y = oy + (q.vy * elapsed + 0.75 * elapsed * elapsed) * size.height * 0.45
                    var ctx2 = gc
                    ctx2.translateBy(x: x, y: y)
                    ctx2.rotate(by: .degrees(q.rot + q.spin * elapsed))
                    ctx2.fill(
                        Path(CGRect(x: -q.w / 2, y: -q.w / 4, width: q.w, height: q.w / 2)),
                        with: .color(q.color.opacity(alpha))
                    )
                }
            }
        }
        .allowsHitTesting(false)
        .onAppear { if trigger > 0 { fire() } }
        .onChange(of: trigger) { _ in fire() }
    }

    private func fire() {
        let colors = [Palette.green, Palette.violet, Palette.pink, Palette.cyan, Palette.amber, .white]
        particles = (0..<150).map { _ in
            Particle(
                vx: (Double.random(in: 0...1) - 0.5) * 1.5,
                vy: -(0.6 + Double.random(in: 0...1.5)),
                w: 8 + Double.random(in: 0...14),
                rot: Double.random(in: 0...360),
                spin: (Double.random(in: 0...1) - 0.5) * 900,
                wobble: Double.random(in: 0...6),
                color: colors.randomElement()!
            )
        }
        start = Date()
    }
}

/// Seitliches Wackeln bei falscher Antwort.
struct ShakeEffect: GeometryEffect {
    var animatableData: CGFloat

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 16 * sin(animatableData * .pi * 6), y: 0))
    }
}

/// Bunter Kreis-Hintergrund, falls kein Cover existiert.
struct CoverImage: View {
    let url: URL?
    var corner: CGFloat = 12

    var body: some View {
        ZStack {
            Palette.surfaceHi
            if let url {
                AsyncImage(url: url) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
            } else {
                Text("♪").font(.system(size: 30)).foregroundColor(.white.opacity(0.7))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
    }
}
