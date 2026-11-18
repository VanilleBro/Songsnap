package com.songsnap.game.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.songsnap.game.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Langsam wabernde Farb-Blobs als Hintergrund. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
                blob(Palette.Violet, w * (0.10f + 0.70f * a), h * (0.08f + 0.25f * b), w * 0.95f)
                blob(Palette.Green, w * (0.90f - 0.55f * b), h * (0.50f + 0.30f * c), w * 0.80f)
                blob(Palette.Pink, w * (0.25f + 0.55f * c), h * (0.98f - 0.30f * a), w * 0.75f)
            },
        content = content,
    )
}

/** Text mit animiertem Farbverlauf. */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    fontWeight: FontWeight = FontWeight.Black,
    colors: List<Color> = listOf(Palette.Green, Palette.Cyan, Palette.Violet, Palette.Pink, Palette.Green),
    animated: Boolean = true,
) {
    val t = rememberInfiniteTransition(label = "gradtext")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "shift")
    val phase = if (animated) shift else 0f
    Text(
        text = text,
        style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, letterSpacing = (-0.5).sp, color = Color.White),
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val w = this.size.width.coerceAtLeast(1f)
                onDrawWithContent {
                    drawContent()
                    val brush = Brush.linearGradient(
                        colors = colors,
                        start = Offset(-w + 2 * w * phase, 0f),
                        end = Offset(w + 2 * w * phase, this.size.height),
                        tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                    )
                    drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                }
            },
    )
}

@Composable
fun Logo(modifier: Modifier = Modifier, fontSize: TextUnit = 40.sp) {
    GradientText("SongSnap", modifier = modifier, fontSize = fontSize)
}

/** Drehende Schallplatte, optional mit Cover als Label. */
@Composable
fun SpinningVinyl(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    spinning: Boolean = true,
    periodMs: Int = 2_600,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning, periodMs) {
        if (spinning) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(periodMs, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }
    Box(modifier.graphicsLayer { rotationZ = rotation.value }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(color = Color(0xFF0C0C14), radius = r)
            for (i in 0 until 10) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f),
                    radius = r * (0.46f + i * 0.054f),
                    style = Stroke(width = 1.3f),
                )
            }
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                ),
                radius = r,
            )
            drawCircle(color = Color.White.copy(alpha = 0.10f), radius = r, style = Stroke(width = 2f))
        }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(0.40f).clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize(0.36f)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Palette.Green, Palette.Violet, Palette.Pink))),
            )
        }
        Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.Bg))
    }
}

/** Springende Equalizer-Balken. */
@Composable
fun Equalizer(
    active: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 5,
    color: Color = Color.Black,
    barWidth: Dp = 7.dp,
) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until bars) {
            val h by t.animateFloat(
                initialValue = 0.18f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(260 + (i * 97) % 260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "bar$i",
            )
            val frac = if (active) h else 0.2f
            Box(
                Modifier
                    .width(barWidth)
                    .fillMaxHeight(frac)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color),
            )
        }
    }
}

/** Klick mit federndem Eindrück-Effekt. */
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Palette.Green, Palette.Teal),
    textColor: Color = Color.Black,
    enabled: Boolean = true,
    height: Dp = 58.dp,
) {
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .height(height)
            .bouncyClick(enabled, onClick)
            .clip(RoundedCornerShape(height / 2))
            .background(Brush.horizontalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.SurfaceHi.copy(alpha = 0.72f))
            .border(1.dp, Palette.Stroke, RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content,
    )
}

private class Particle(
    val vx: Float,
    val vy: Float,
    val color: Color,
    val w: Float,
    val rot: Float,
    val spin: Float,
    val wobble: Float,
)

/** Konfetti-Explosion, ausgelöst durch einen neuen [trigger]-Wert. */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val particles = remember(trigger) {
        val rnd = Random(trigger * 7919L + 17L)
        val colors = listOf(Palette.Green, Palette.Violet, Palette.Pink, Palette.Cyan, Palette.Amber, Color.White)
        List(150) {
            Particle(
                vx = (rnd.nextFloat() - 0.5f) * 1.5f,
                vy = -(0.6f + rnd.nextFloat() * 1.5f),
                color = colors[rnd.nextInt(colors.size)],
                w = 8f + rnd.nextFloat() * 14f,
                rot = rnd.nextFloat() * 360f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                wobble = rnd.nextFloat() * 6f,
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.animateTo(1f, tween(2_800, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val t = p * 2.8f
        val w = this.size.width
        val h = this.size.height
        val alpha = (1f - p * p).coerceIn(0f, 1f)
        val ox = w / 2f
        val oy = h * 0.38f
        particles.forEach { q ->
            val x = ox + q.vx * w * 0.55f * t + sin(t * q.wobble) * 12f
            val y = oy + (q.vy * t + 0.75f * t * t) * h * 0.45f
            rotate(degrees = q.rot + q.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = q.color.copy(alpha = alpha),
                    topLeft = Offset(x - q.w / 2f, y - q.w / 4f),
                    size = Size(q.w, q.w / 2f),
                )
            }
        }
    }
}

/** Seitliches Wackeln (falsche Antwort): Wert 0..1 -> Versatz in dp. */
fun shakeOffset(progress: Float): Float =
    if (progress <= 0f || progress >= 1f) 0f else (sin(progress * PI * 7).toFloat() * 22f * (1f - progress))
