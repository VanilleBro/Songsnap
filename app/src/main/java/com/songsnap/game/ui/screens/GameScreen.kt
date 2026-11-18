package com.songsnap.game.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.songsnap.game.GameState
import com.songsnap.game.GameViewModel
import com.songsnap.game.GuessEntry
import com.songsnap.game.GuessKind
import com.songsnap.game.Phase
import com.songsnap.game.STAGES_MS
import com.songsnap.game.STAGE_LABELS
import com.songsnap.game.data.Song
import com.songsnap.game.formatSeconds
import com.songsnap.game.ui.components.ConfettiBurst
import com.songsnap.game.ui.components.GradientButton
import com.songsnap.game.ui.components.PlayOrb
import com.songsnap.game.ui.components.SpinningVinyl
import com.songsnap.game.ui.components.StageBar
import com.songsnap.game.ui.components.bouncyClick
import com.songsnap.game.ui.components.shakeOffset
import com.songsnap.game.ui.theme.Palette

@Composable
fun GameScreen(vm: GameViewModel) {
    val g = vm.game ?: return
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var query by remember(g.round) { mutableStateOf("") }
    val suggestions = remember(query, g.allSongs) { vm.search(query) }

    // Wackeln + Vibration bei falscher Antwort
    val shake = remember { Animatable(0f) }
    LaunchedEffect(g.wrongTick) {
        if (g.wrongTick > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            shake.snapTo(0f)
            shake.animateTo(1f, tween(480, easing = LinearEasing))
        }
    }
    LaunchedEffect(g.phase) {
        if (g.phase == Phase.REVEAL_WIN) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (g.phase == Phase.REVEAL_WIN || g.phase == Phase.REVEAL_LOSS) focus.clearFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
        ) {
            TopBar(g, onClose = { vm.quitGame() })
            Spacer(Modifier.height(14.dp))
            StageBar(stage = g.stage, phase = g.phase)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayOrb(
                        phase = g.phase,
                        audible = vm.audible,
                        playTick = vm.playTick,
                        durationMs = STAGES_MS[g.stage],
                        onClick = { vm.play() },
                        modifier = Modifier.offset(x = shakeOffset(shake.value).dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    AnimatedContent(
                        targetState = hintText(g),
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(120))
                        },
                        label = "hint",
                    ) { text ->
                        Text(
                            text,
                            color = Palette.TextDim,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            GuessHistory(g.guesses)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    enabled = g.canGuess,
                    singleLine = true,
                    placeholder = { Text("Song oder Künstler …", color = Palette.TextDim) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.TextDim) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Leeren", tint = Palette.TextDim)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        suggestions.firstOrNull()?.let {
                            vm.guess(it)
                            query = ""
                            focus.clearFocus()
                        }
                    }),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Violet,
                        unfocusedBorderColor = Palette.Stroke,
                        disabledBorderColor = Palette.Stroke,
                        cursorColor = Palette.Violet,
                        focusedContainerColor = Palette.SurfaceHi.copy(alpha = 0.85f),
                        unfocusedContainerColor = Palette.SurfaceHi.copy(alpha = 0.85f),
                        disabledContainerColor = Palette.SurfaceHi.copy(alpha = 0.5f),
                        focusedTextColor = Palette.Text,
                        unfocusedTextColor = Palette.Text,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SkipButton(g, onClick = { vm.skip() })
            }
            Spacer(Modifier.height(12.dp))
        }

        // Vorschläge schweben über dem Eingabefeld
        AnimatedVisibility(
            visible = query.isNotBlank() && g.canGuess,
            enter = fadeIn(tween(160)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 84.dp),
        ) {
            SuggestionList(suggestions) { s ->
                vm.guess(s)
                query = ""
                focus.clearFocus()
            }
        }

        AnimatedVisibility(
            visible = g.phase == Phase.REVEAL_WIN || g.phase == Phase.REVEAL_LOSS,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
        ) {
            RevealOverlay(g, onNext = { vm.nextRound() })
        }

        ConfettiBurst(trigger = vm.confettiTick)
    }
}

private fun hintText(g: GameState): String = when (g.phase) {
    Phase.PREPARING -> "Song wird vorbereitet …"
    Phase.READY -> "Tippe zum Abspielen · ${STAGE_LABELS[g.stage]}"
    Phase.PLAYING -> "Hör genau hin …"
    Phase.REVEAL_WIN, Phase.REVEAL_LOSS -> " "
}

@Composable
private fun TopBar(g: GameState, onClose: () -> Unit) {
    val shownScore by animateIntAsState(g.score, tween(900), label = "score")
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Beenden", tint = Palette.TextDim)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Song ${g.round + 1} / ${g.songs.size}",
                color = Palette.Text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
            Text(
                g.playlist.name,
                color = Palette.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Palette.Violet, Palette.Pink)))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("★ $shownScore", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SkipButton(g: GameState, onClick: () -> Unit) {
    val last = g.stage >= STAGES_MS.size - 1
    val label = if (last) "Auf-\ngeben" else "+" + formatSeconds(STAGES_MS[g.stage + 1] - STAGES_MS[g.stage])
    Box(
        Modifier
            .size(width = 74.dp, height = 56.dp)
            .graphicsLayer { alpha = if (g.canGuess) 1f else 0.4f }
            .bouncyClick(enabled = g.canGuess, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (last) Palette.Red.copy(alpha = 0.85f) else Palette.SurfaceHi)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!last) Text("Skip", color = Palette.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                label,
                color = Color.White,
                fontSize = if (last) 12.sp else 15.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
            )
        }
    }
}

@Composable
private fun GuessHistory(guesses: List<GuessEntry>) {
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        guesses.takeLast(4).forEach { entry ->
            key(entry.stage, entry.text) {
                GuessRow(entry)
            }
        }
    }
}

@Composable
private fun GuessRow(entry: GuessEntry) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it } + fadeIn(),
    ) {
        val (icon, color) = when (entry.kind) {
            GuessKind.WRONG -> "✕" to Palette.Red
            GuessKind.SKIPPED -> "»" to Palette.TextDim
            GuessKind.CORRECT -> "✓" to Palette.Green
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.13f))
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                entry.text,
                color = Palette.Text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(STAGE_LABELS[entry.stage], color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<Song>, onPick: (Song) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1B1B2A))
            .border(1.dp, Palette.Stroke, RoundedCornerShape(20.dp)),
    ) {
        if (suggestions.isEmpty()) {
            Text(
                "Kein Treffer in dieser Playlist",
                color = Palette.TextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(18.dp),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(suggestions, key = { it.uri }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(s) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = s.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Palette.SurfaceHi),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                color = Palette.Text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                s.artistText,
                                color = Palette.TextDim,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevealOverlay(g: GameState, onNext: () -> Unit) {
    val song = g.current
    val win = g.phase == Phase.REVEAL_WIN
    val appear = remember(g.round) { Animatable(0f) }
    LaunchedEffect(g.round) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 140f)) }
    val points by animateIntAsState(if (appear.value > 0.5f) g.lastPoints else 0, tween(900), label = "pts")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (win) "Richtig! 🎉" else "Leider nicht 😢",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = if (win) Palette.Green else Palette.Red,
                modifier = Modifier.graphicsLayer {
                    val s = 0.4f + 0.6f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value.coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.height(22.dp))

            // Cover + herausgleitende Schallplatte
            Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                SpinningVinyl(
                    modifier = Modifier
                        .size(205.dp)
                        .graphicsLayer {
                            translationX = appear.value * 62.dp.toPx()
                            alpha = appear.value.coerceIn(0f, 1f)
                        },
                    imageUrl = song.imageUrl,
                    periodMs = 2_000,
                )
                Box(
                    Modifier
                        .size(215.dp)
                        .graphicsLayer {
                            translationX = -appear.value * 34.dp.toPx()
                            rotationZ = (1f - appear.value) * 25f
                            val s = 0.6f + 0.4f * appear.value
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(28.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.SurfaceHi),
                ) {
                    AsyncImage(
                        model = song.imageUrl,
                        contentDescription = song.album,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                song.name,
                color = Palette.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(song.artistText, color = Palette.TextDim, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))

            AnimatedVisibility(visible = appear.value > 0.3f, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn()) {
                if (win) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Palette.Green.copy(alpha = 0.16f))
                            .border(1.dp, Palette.Green.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "+$points Punkte · nach ${STAGE_LABELS[g.stage]}",
                            color = Palette.Green,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                        )
                    }
                } else {
                    Text(
                        "Auch 16 Sekunden haben nicht gereicht.",
                        color = Palette.TextDim,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
            GradientButton(
                if (g.isLastRound) "Ergebnis ansehen 🏁" else "Nächster Song →",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
