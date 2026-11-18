package com.songsnap.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.songsnap.game.GameViewModel
import com.songsnap.game.Screen
import com.songsnap.game.ui.components.AuroraBackground
import com.songsnap.game.ui.components.TopToast
import com.songsnap.game.ui.screens.GameScreen
import com.songsnap.game.ui.screens.LoadingScreen
import com.songsnap.game.ui.screens.LoginScreen
import com.songsnap.game.ui.screens.PlaylistsScreen
import com.songsnap.game.ui.screens.ResultsScreen
import com.songsnap.game.ui.screens.SetupScreen

@Composable
fun SongSnapApp(vm: GameViewModel) {
    val screen = vm.screen

    BackHandler(enabled = screen == Screen.Game) { vm.quitGame() }
    BackHandler(enabled = screen == Screen.Results) { vm.backToPlaylists() }
    BackHandler(enabled = screen == Screen.Setup && vm.prefs.clientId.isNotBlank()) { vm.leaveSetup() }

    AuroraBackground {
        AnimatedContent(
            targetState = screen,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(380)) + scaleIn(initialScale = 0.94f, animationSpec = tween(380))) togetherWith
                    (fadeOut(tween(200)) + scaleOut(targetScale = 1.04f, animationSpec = tween(200)))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Setup -> SetupScreen(vm)
                Screen.Login -> LoginScreen(vm)
                Screen.Playlists -> PlaylistsScreen(vm)
                is Screen.Loading -> LoadingScreen(s.message)
                Screen.Game -> GameScreen(vm)
                Screen.Results -> ResultsScreen(vm)
            }
        }
        TopToast(
            text = vm.toastText,
            visible = vm.toastVisible,
            tick = vm.toastTick,
            onDismiss = vm::dismissToast,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
    }
}
