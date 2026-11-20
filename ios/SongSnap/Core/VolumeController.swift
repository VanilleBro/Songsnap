import AVFoundation
import MediaPlayer
import SwiftUI
import UIKit

/// iOS erlaubt keiner App, den Ton anderer Apps stummzuschalten. Der einzige Weg ist die
/// System-Lautstärke über einen (unsichtbaren) MPVolumeView-Regler. Damit gibt SongSnap den
/// Ton nur für die exakte Ausschnittlänge frei.
@MainActor
final class VolumeController {
    static let shared = VolumeController()

    let volumeView: MPVolumeView
    private(set) var userVolume: Float
    private(set) var isMuted = false
    private let defaults = UserDefaults.standard

    private init() {
        volumeView = MPVolumeView(frame: CGRect(x: 0, y: 0, width: 40, height: 40))
        let saved = UserDefaults.standard.float(forKey: "user_volume")
        userVolume = saved > 0.02 ? saved : 0.6
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private var slider: UISlider? {
        volumeView.subviews.compactMap { $0 as? UISlider }.first
    }

    var currentSystemVolume: Float { AVAudioSession.sharedInstance().outputVolume }

    private func setSystemVolume(_ v: Float) {
        slider?.setValue(v, animated: false)
        slider?.sendActions(for: .valueChanged)
    }

    /// Merkt sich die aktuelle Lautstärke des Nutzers (nur wenn nicht von uns stummgeschaltet).
    func captureUserVolume() {
        guard !isMuted else { return }
        let v = currentSystemVolume
        if v > 0.02 {
            userVolume = v
            defaults.set(v, forKey: "user_volume")
        }
    }

    func mute() {
        guard !isMuted else { return }
        captureUserVolume()
        isMuted = true
        defaults.set(true, forKey: "muted_by_app")
        setSystemVolume(0)
    }

    func unmute() {
        guard isMuted else { return }
        isMuted = false
        defaults.set(false, forKey: "muted_by_app")
        setSystemVolume(userVolume)
    }

    /// Falls die App beim letzten Mal mitten im Ausschnitt beendet wurde.
    func restoreIfNeeded() {
        if defaults.bool(forKey: "muted_by_app") {
            defaults.set(false, forKey: "muted_by_app")
            isMuted = false
            setSystemVolume(userVolume)
        }
    }
}

/// Der Regler muss in der View-Hierarchie hängen, damit iOS die Lautstärke-Einblendung unterdrückt.
struct HiddenVolumeView: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let container = UIView(frame: CGRect(x: 0, y: 0, width: 40, height: 40))
        let v = VolumeController.shared.volumeView
        v.frame = CGRect(x: -2000, y: -2000, width: 40, height: 40)
        v.alpha = 0.01
        v.isUserInteractionEnabled = false
        container.addSubview(v)
        container.isUserInteractionEnabled = false
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
