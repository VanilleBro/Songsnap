package com.songsnap.game.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.songsnap.game.data.Playlist
import com.songsnap.game.ui.components.Logo
import com.songsnap.game.ui.components.ModeToggle
import com.songsnap.game.ui.components.bouncyClick
import com.songsnap.game.ui.theme.Palette
import kotlinx.coroutines.delay

@Composable
fun PlaylistsScreen(vm: GameViewModel) {
    val activity = LocalContext.current as Activity
    val list = vm.playlists

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo(fontSize = 32.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadPlaylists() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Neu laden", tint = Palette.TextDim)
            }
            IconButton(onClick = { vm.logout() }) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Abmelden", tint = Palette.TextDim)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GameSettings(vm)
            }

            if (list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        if (vm.playlistsLoading) {
                            CircularProgressIndicator(color = Palette.Green)
                        } else {
                            Text(
                                "Keine Playlists gefunden.\nTippe oben auf ↻ zum Neuladen.",
                                color = Palette.TextDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            itemsIndexed(list, key = { _, p -> p.id }) { index, p ->
                PlaylistCard(
                    playlist = p,
                    index = index,
                    best = vm.bestFor(p),
                    onClick = { vm.startGame(activity, p) },
                )
            }
        }
    }
}

@Composable
private fun GameSettings(vm: GameViewModel) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Modus", color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ModeToggle(vm.mode, vm::selectMode)
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = vm.mode,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically { -it / 2 })
            },
            label = "modeDesc",
        ) { m ->
            Text(
                if (m == Mode.BEGINNING) {
                    "Jeder Ausschnitt startet ganz am Anfang des Songs."
                } else {
                    "Jeder Song startet an einer zufälligen Stelle – deutlich schwerer!"
                },
                color = Palette.TextDim,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Songs pro Runde", color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(5, 10, 15, 20).forEach { n ->
                val selected = vm.rounds == n
                val bg by animateColorAsState(if (selected) Palette.Green else Palette.SurfaceHi, label = "r$n")
                val fg by animateColorAsState(if (selected) Color.Black else Palette.Text, label = "f$n")
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .bouncyClick { vm.selectRounds(n) }
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .border(1.dp, Palette.Stroke, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Wähle eine Playlist", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, index: Int, best: Int, onClick: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(playlist.id) {
        delay((index % 10) * 55L)
        appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 170f))
    }
    Column(
        Modifier
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f) * (if (playlist.accessible) 1f else 0.55f)
                translationY = (1f - v) * 90f
                val s = 0.85f + 0.15f * v
                scaleX = s
                scaleY = s
            }
            .bouncyClick(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.SurfaceHi),
        ) {
            when {
                playlist.isLiked -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF8B5CF6), Color(0xFFC4B5FD)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                playlist.imageUrl != null -> AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Palette.SurfaceHi, Palette.Violet.copy(alpha = 0.6f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♪", fontSize = 64.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
            )
            Text(
                if (playlist.trackCount >= 0) "${playlist.trackCount} Songs" else "",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            if (best > 0) {
                Text(
                    "🏆 $best",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Amber)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (!playlist.accessible) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Gesperrt", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
            fontSize = 15.sp,
        )
        Text(
            playlist.owner,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Palette.TextDim,
            fontSize = 12.sp,
        )
    }
}
