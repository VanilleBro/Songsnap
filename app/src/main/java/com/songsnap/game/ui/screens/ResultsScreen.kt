package com.songsnap.game.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.songsnap.game.GameViewModel
import com.songsnap.game.Mode
import com.songsnap.game.RoundResult
import com.songsnap.game.STAGE_LABELS
import com.songsnap.game.STAGE_POINTS
import com.songsnap.game.ui.components.ConfettiBurst
import com.songsnap.game.ui.components.GradientButton
import com.songsnap.game.ui.components.GradientText
import com.songsnap.game.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun ResultsScreen(vm: GameViewModel) {
    val g = vm.game ?: return
    val activity = LocalContext.current as Activity
    val maxScore = (g.songs.size * STAGE_POINTS[0]).coerceAtLeast(1)
    val solved = g.results.count { it.solvedStage != null }
    val pct = g.score.toFloat() / maxScore

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(250)
        started = true
    }
    val shown by animateIntAsState(if (started) g.score else 0, tween(1_700, easing = FastOutSlowInEasing), label = "score")

    val inf = rememberInfiniteTransition(label = "trophy")
    val bob by inf.animateFloat(-8f, 8f, infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val badgePulse by inf.animateFloat(1f, 1.1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "badge")

    val (emoji, title) = when {
        pct >= 0.8f -> "🧠" to "Musik-Genie!"
        pct >= 0.6f -> "🔥" to "Richtig stark!"
        pct >= 0.4f -> "🎧" to "Solide Ohren!"
        pct >= 0.2f -> "🎶" to "Da geht noch was"
        else -> "💪" to "Übung macht den Meister"
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        emoji,
                        fontSize = 76.sp,
                        modifier = Modifier.graphicsLayer { translationY = bob * density },
                    )
                    Text(title, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    GradientText("$shown", fontSize = 72.sp)
                    Text("von $maxScore Punkten", color = Palette.TextDim, fontSize = 14.sp)
                    if (vm.newHighscore) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "🏆 NEUER HIGHSCORE!",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = badgePulse
                                    scaleY = badgePulse
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(Palette.Amber)
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    } else if (vm.previousBest > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Bestwert: ${vm.previousBest}", color = Palette.TextDim, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatChip("$solved / ${g.songs.size}", "erkannt")
                        StatChip(if (g.mode == Mode.BEGINNING) "⏮" else "🎲", if (g.mode == Mode.BEGINNING) "Beginning" else "Random")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            itemsIndexed(g.results) { index, r ->
                ResultRow(index, r)
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    GradientButton(
                        "Nochmal spielen 🔁",
                        onClick = { vm.playAgain(activity) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton(
                        "Andere Playlist",
                        onClick = { vm.backToPlaylists() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = listOf(Palette.SurfaceHi, Palette.SurfaceHi),
                        textColor = Palette.Text,
                    )
                }
            }
        }
        if (vm.newHighscore) ConfettiBurst(trigger = 1)
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHi.copy(alpha = 0.8f))
            .border(1.dp, Palette.Stroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Palette.Text, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Palette.TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun ResultRow(index: Int, r: RoundResult) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(600L + index * 90L)
        appear.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 220f))
    }
    val ok = r.solvedStage != null
    val accent = if (ok) Palette.Green else Palette.Red
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationX = (1f - appear.value) * 160f
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHi.copy(alpha = 0.75f))
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = r.song.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.Surface),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.song.name,
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                r.song.artistText,
                color = Palette.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (ok) STAGE_LABELS[r.solvedStage ?: 0] else "✕",
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                textAlign = TextAlign.End,
            )
            Text(if (ok) "+${r.points}" else "0", color = Palette.TextDim, fontSize = 11.sp)
        }
    }
}
