package com.songsnap.game.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songsnap.game.GameViewModel
import com.songsnap.game.data.SpotifyAuth
import com.songsnap.game.ui.components.GlassCard
import com.songsnap.game.ui.components.GradientButton
import com.songsnap.game.ui.components.Logo
import com.songsnap.game.ui.components.SpinningVinyl
import com.songsnap.game.ui.theme.Palette
import java.security.MessageDigest

fun signingSha1(context: Context): String {
    return try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val sig = signatures?.firstOrNull() ?: return "unbekannt"
        MessageDigest.getInstance("SHA-1").digest(sig.toByteArray())
            .joinToString(":") { String.format("%02X", it) }
    } catch (e: Exception) {
        "unbekannt"
    }
}

@Composable
private fun CopyRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_500)
            copied = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable {
                clipboard.setText(AnnotatedString(value))
                copied = true
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
            Text(
                if (copied) "✓ kopiert" else "kopieren",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (copied) Palette.Green else Palette.Violet,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Palette.Text)
    }
}

@Composable
private fun StepTitle(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Palette.Violet),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", fontWeight = FontWeight.Black, color = Color.White, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontWeight = FontWeight.Bold, color = Palette.Text, fontSize = 16.sp)
    }
}

@Composable
fun SetupScreen(vm: GameViewModel) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sha1 = remember { signingSha1(context) }
    var clientId by remember { mutableStateOf(vm.prefs.clientId) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Logo(fontSize = 38.sp)
        Text(
            "Einmalige Einrichtung (ca. 3 Minuten). Spotify verlangt, dass du dir eine eigene, kostenlose " +
                "Entwickler-App anlegst. Du brauchst Spotify Premium und die Spotify-App auf diesem Handy.",
            color = Palette.TextDim,
            fontSize = 14.sp,
        )

        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepTitle(1, "Spotify-App anlegen")
                Text(
                    "Öffne das Developer-Dashboard, melde dich an und tippe auf „Create app“. Name und " +
                        "Beschreibung sind egal. Bei „Which API/SDKs“ wähle Web API und Android.",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
                GradientButton(
                    "Dashboard öffnen ↗",
                    onClick = { uriHandler.openUri("https://developer.spotify.com/dashboard") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = listOf(Palette.Violet, Palette.Pink),
                    textColor = Color.White,
                    height = 48.dp,
                )
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column {
                StepTitle(2, "Diese Werte eintragen")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Antippen zum Kopieren. Beide Redirect-URIs hinzufügen.",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                CopyRow("Redirect URI 1", SpotifyAuth.REDIRECT_URI)
                CopyRow("Redirect URI 2 (alternativer Login)", SpotifyAuth.LOOPBACK_REDIRECT_URI)
                CopyRow("Android Package", context.packageName)
                CopyRow("SHA1 Fingerprint", sha1)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepTitle(3, "Client ID einfügen")
                Text(
                    "Speichern, dann in den App-Einstellungen die „Client ID“ kopieren und hier einfügen.",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it.trim() },
                    singleLine = true,
                    placeholder = { Text("z. B. 1a2b3c4d5e6f…", color = Palette.TextDim) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Palette.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Green,
                        unfocusedBorderColor = Palette.Stroke,
                        cursorColor = Palette.Green,
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Tipp: Unter „User Management“ deinen Spotify-Account (E-Mail) eintragen, falls der " +
                        "Login mit „403“ scheitert.",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                )
            }
        }

        GradientButton(
            "Speichern & weiter",
            onClick = { vm.saveClientId(clientId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = clientId.isNotBlank(),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun LoginScreen(vm: GameViewModel) {
    val activity = LocalContext.current as Activity
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f)) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpinningVinyl(
            Modifier
                .size(230.dp)
                .graphicsLayer {
                    val s = 0.5f + 0.5f * appear.value
                    scaleX = s
                    scaleY = s
                    rotationZ = (1f - appear.value) * -120f
                    alpha = appear.value.coerceIn(0f, 1f)
                },
            periodMs = 3_200,
        )
        Spacer(Modifier.height(34.dp))
        Logo(fontSize = 46.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Erkennst du deine Songs in 0,1 Sekunden?",
            color = Palette.TextDim,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(44.dp))
        GradientButton(
            "Mit Spotify verbinden",
            onClick = { vm.startLogin(activity, loopback = false) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { vm.startLogin(activity, loopback = true) }) {
            Text("Alternativer Login (falls der erste nicht klappt)", color = Palette.TextDim, fontSize = 13.sp)
        }
        TextButton(onClick = { vm.openSetup() }) {
            Text("Einrichtung / Client-ID ändern", color = Palette.Violet, fontSize = 13.sp)
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpinningVinyl(Modifier.size(170.dp), periodMs = 1_400)
        Spacer(Modifier.height(30.dp))
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "loadmsg",
        ) { m ->
            Text(m, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
