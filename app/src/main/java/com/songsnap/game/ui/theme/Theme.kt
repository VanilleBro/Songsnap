package com.songsnap.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF07070D)
    val Surface = Color(0xFF12121C)
    val SurfaceHi = Color(0xFF1D1D2C)
    val Stroke = Color(0x1FFFFFFF)
    val Green = Color(0xFF1ED760)
    val Teal = Color(0xFF14B8A6)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Cyan = Color(0xFF22D3EE)
    val Red = Color(0xFFFF4D6D)
    val Amber = Color(0xFFFBBF24)
    val Text = Color(0xFFF4F4FA)
    val TextDim = Color(0xFF9A9AB4)
}

@Composable
fun SongSnapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Green,
            onPrimary = Color.Black,
            secondary = Palette.Violet,
            onSecondary = Color.White,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHi,
            onSurfaceVariant = Palette.TextDim,
            error = Palette.Red,
            outline = Color(0xFF34344A),
        ),
        typography = Typography(),
        content = content,
    )
}
