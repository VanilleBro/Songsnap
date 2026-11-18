package com.songsnap.game.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songsnap.game.Mode
import com.songsnap.game.Phase
import com.songsnap.game.STAGE_LABELS
import com.songsnap.game.ui.theme.Palette
import kotlinx.coroutines.delay

/** Großer Play-Knopf mit rotierendem Leuchtring und Fortschrittsring. */
@Composable
fun PlayOrb(
    phase: Phase,
    audible: Boolean,
    playTick: Int,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = phase == Phase.READY
    val inf = rememberInfiniteTransition(label = "orb")
    val pulse by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "spin")
    val haloAlpha by animateFloatAsState(if (audible) 1f else if (ready) 0.55f else 0.25f, tween(250), label = "halo")
    val audibleScale by animateFloatAsState(if (audible) 1.1f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "aud")

    val progress = remember { Animatable(0f) }
    LaunchedEffect(playTick) {
        if (playTick > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMs.toInt().coerceAtLeast(16), easing = LinearEasing))
        }
    }
    LaunchedEffect(phase) {
        if (phase == Phase.PREPARING) progress.snapTo(0f)
    }

    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        // weicher Glow
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = haloAlpha }) {
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette.Green.copy(alpha = 0.55f), Palette.Violet.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        // rotierender Farbring
        Canvas(
            Modifier
                .size(212.dp)
                .graphicsLayer {
                    rotationZ = spin
                    alpha = 0.35f + 0.65f * haloAlpha
                },
        ) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(Palette.Green, Palette.Cyan, Palette.Violet, Palette.Pink, Palette.Green)),
                radius = this.size.minDimension / 2f - 3.dp.toPx(),
                style = Stroke(width = 4.dp.toPx()),
            )
        }
        // Fortschritt des Ausschnitts
        Canvas(Modifier.size(190.dp)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (progress.value > 0f) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // Knopf
        val s = (if (ready) pulse else 1f) * audibleScale
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer {
                    scaleX = s
                    scaleY = s
                }
                .shadow(24.dp, CircleShape, ambientColor = Palette.Green, spotColor = Palette.Green)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Green, Palette.Teal)))
                .bouncyClick(enabled = ready, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                Phase.PLAYING -> Equalizer(active = audible, modifier = Modifier.height(52.dp))
                Phase.PREPARING -> CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(46.dp),
                )
                else -> Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Abspielen",
                    tint = Color.Black,
                    modifier = Modifier.size(84.dp),
                )
            }
        }
    }
}

/** Die 7 Stufen 0,1 s … 16 s. */
@Composable
fun StageBar(stage: Int, phase: Phase, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "stage")
    val glow by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "glow")
    val won = phase == Phase.REVEAL_WIN
    val lost = phase == Phase.REVEAL_LOSS
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        STAGE_LABELS.forEachIndexed { i, label ->
            val isCurrent = i == stage
            val target = when {
                won && isCurrent -> Palette.Green
                lost && isCurrent -> Palette.Red
                i < stage -> Palette.Red.copy(alpha = 0.55f)
                isCurrent -> Palette.Violet
                else -> Palette.SurfaceHi
            }
            val color by animateColorAsState(target, tween(450), label = "c$i")
            val scale by animateFloatAsState(
                if (isCurrent) 1.08f else 1f,
                spring(dampingRatio = 0.35f, stiffness = 400f),
                label = "s$i",
            )
            val pulsing = isCurrent && !won && !lost
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (pulsing) glow else 1f
                    }
                    .clip(RoundedCornerShape(11.dp))
                    .background(color)
                    .border(
                        1.dp,
                        if (isCurrent) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                        RoundedCornerShape(11.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (i <= stage) Color.White else Palette.TextDim,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Schalter Beginning / Random mit gleitender Pille. */
@Composable
fun ModeToggle(mode: Mode, onChange: (Mode) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Palette.SurfaceHi)
            .border(1.dp, Palette.Stroke, RoundedCornerShape(27.dp)),
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(
            if (mode == Mode.BEGINNING) 0.dp else half,
            spring(dampingRatio = 0.68f, stiffness = 380f),
            label = "pill",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(half)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Brush.horizontalGradient(listOf(Palette.Violet, Palette.Pink))),
        )
        Row(Modifier.fillMaxSize()) {
            Mode.entries.forEach { m ->
                val selected = m == mode
                val textColor by animateColorAsState(if (selected) Color.White else Palette.TextDim, label = "t")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onChange(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (m == Mode.BEGINNING) "⏮  Beginning" else "🎲  Random",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = textColor,
                    )
                }
            }
        }
    }
}

/** Kleine Benachrichtigung oben am Bildschirm. */
@Composable
fun TopToast(text: String, visible: Boolean, tick: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(tick, visible) {
        if (visible) {
            delay(4_200)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(16.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF26263A))
                .border(1.dp, Palette.Stroke, RoundedCornerShape(18.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(text, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
