import SwiftUI

@main
struct SongSnapApp: App {
    @StateObject private var model = GameModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .preferredColorScheme(.dark)
                .onOpenURL { url in model.handleOpenURL(url) }
                .onChange(of: scenePhase) { phase in
                    switch phase {
                    case .active: model.onForeground()
                    case .background: model.onBackground()
                    default: break
                    }
                }
        }
    }
}

struct RootView: View {
    @ObservedObject var model: GameModel

    var body: some View {
        ZStack {
            AuroraBackground()
            HiddenVolumeView().frame(width: 1, height: 1).opacity(0.01)

            Group {
                switch model.screen {
                case .setup: SetupView(model: model)
                case .login: LoginView(model: model)
                case .playlists: PlaylistsView(model: model)
                case let .loading(message): LoadingView(message: message)
                case .game: GameView(model: model)
                case .results: ResultsView(model: model)
                }
            }
            .id(model.screen.key)
            .transition(.asymmetric(
                insertion: .opacity.combined(with: .scale(scale: 0.94)),
                removal: .opacity.combined(with: .scale(scale: 1.04))
            ))

            ToastView(model: model)
        }
        .animation(.easeInOut(duration: 0.35), value: model.screen.key)
    }
}
