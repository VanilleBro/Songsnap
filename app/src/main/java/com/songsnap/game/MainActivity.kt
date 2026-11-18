package com.songsnap.game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.songsnap.game.ui.SongSnapApp
import com.songsnap.game.ui.theme.SongSnapTheme

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            SongSnapTheme {
                SongSnapApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground(this)
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        if (data.scheme == "songsnap") {
            vm.handleRedirect(data)
            // nicht erneut verarbeiten (z. B. bei Neuerstellung)
            intent.data = null
        }
    }
}
