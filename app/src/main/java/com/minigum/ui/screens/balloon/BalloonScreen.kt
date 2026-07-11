package com.minigum.ui.screens.balloon

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.minigum.domain.models.LeaderboardGame
import com.minigum.ui.components.GameOverOverlay
import com.minigum.ui.components.GamePauseOverlay
import com.minigum.ui.components.GameStat
import com.minigum.ui.navigation.leaderboardRoute
import com.minigum.ui.screens.leaderboard.InlineLeaderboard
import com.minigum.ui.components.HowToPlayDialog
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes
import kotlin.math.min

@Composable
fun BalloonScreen(
    navController: NavController,
    viewModel: BalloonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }

    LockPortraitImmersive()

    BackHandler(enabled = !showHowTo) {
        when {
            showPause -> showPause = false
            state.isGameOver -> navController.navigateUp()
            state.calibration == BalloonViewModel.Calibration.DONE -> { viewModel.pause(); showPause = true }
            else -> navController.navigateUp()
        }
    }

    if (!hasPermission) {
        MicRequiredScreen(onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
        return
    }

    LaunchedEffect(showHowTo) {
        if (!showHowTo && state.calibration == BalloonViewModel.Calibration.NONE) viewModel.startWithMic()
    }

    val overlayOpen = showHowTo || showPause || state.isGameOver ||
        state.calibration == BalloonViewModel.Calibration.SILENCE ||
        state.calibration == BalloonViewModel.Calibration.BLOW
    LaunchedEffect(overlayOpen) { if (overlayOpen && state.isRunning) viewModel.pause() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val aspect = if (heightPx > 0f) widthPx / heightPx else 9f / 16f
        LaunchedEffect(aspect) { viewModel.configure(aspect) }

        GameField(state = state)

        // Swipe left/right to move the balloon horizontally.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, drag ->
                        if (widthPx > 0f) viewModel.swipe(drag / widthPx)
                    }
                },
        )

        // HUD: height + coins + best.
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(Sizes.sm),
        ) {
            Text("${(state.maxHeight * 10).toInt()} m", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Best ${state.bestHeight} m   ● ${state.coinsCollected}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }

        IconButton(
            onClick = { viewModel.pause(); showPause = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(Sizes.xxs),
        ) { Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White) }

        IntensityMeter(intensity = state.intensity, modifier = Modifier.align(Alignment.CenterEnd).padding(end = Sizes.sm))

        if (state.calibration == BalloonViewModel.Calibration.SILENCE) {
            CalibrationOverlay("Calibrating silence…", "Stay quiet for a moment.")
        }
        if (state.calibration == BalloonViewModel.Calibration.BLOW) {
            CalibrationOverlay("Blow now!", "Blow into the mic as if inflating a balloon.")
        }
        if (state.calibration == BalloonViewModel.Calibration.FAILED) {
            CalibrationOverlay("Mic unavailable", "Couldn't open the microphone. Check permission and retry.") {
                Button(onClick = { viewModel.startWithMic() }) { Text("Retry") }
            }
        }

        if (showPause) {
            GamePauseOverlay(
                onResume = { showPause = false; viewModel.start() },
                onHowToPlay = { showPause = false; showHowTo = true },
                onLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.BALLOON)) },
                onExit = { navController.navigateUp() },
                settings = {
                    Button(onClick = { viewModel.restart(); showPause = false }) { Text("Restart") }
                },
            )
        }

        if (state.isGameOver) {
            GameOverOverlay(
                title = "Popped!",
                stats = listOf(
                    GameStat("Height", "${(state.maxHeight * 10).toInt()} m"),
                    GameStat("Coins", "${state.coinsCollected}", highlight = true),
                    GameStat("Best", "${state.bestHeight} m"),
                ),
                onPlayAgain = { viewModel.restart() },
                onExit = { navController.navigateUp() },
                inlineLeaderboard = { InlineLeaderboard(LeaderboardGame.BALLOON, (state.maxHeight * 10).toInt()) },
                onFullLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.BALLOON)) },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Balloon Escape",
            instructions = balloonInstructions,
            onContinue = { showHowTo = false },
        )
    }
}

private val balloonInstructions = listOf(
    "Blow (or make any sound) to make the balloon rise. Stay silent to fall.",
    "Climb forever upward — the higher you go, the higher your score.",
    "Land on platforms to rest, then blow to rise again. Green platforms are springs that boost you.",
    "Don't hit the birds sweeping across — they'll pop you.",
    "Grab coins for bonus score.",
    "Swipe left/right to drift. Falling drops you onto a lower platform — only spikes and birds pop you.",
)

// ---- Game field (canvas) -----------------------------------------------

@Composable
private fun GameField(state: BalloonViewModel.GameState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cam = state.cameraWorldY

        // Sky fades as you climb higher.
        val high = (state.maxHeight * 0.08f).coerceIn(0f, 1f)
        drawRect(
            Brush.verticalGradient(
                listOf(
                    androidx.compose.ui.graphics.lerp(Color(0xFF4FC3F7), Color(0xFF1A237E), high),
                    androidx.compose.ui.graphics.lerp(Color(0xFFB3E5FC), Color(0xFF3949AB), high),
                ),
            ),
        )

        // Parallax clouds drift slowly as the camera climbs.
        val cloudOffset = (cam * 0.3f) % 1f
        for (c in clouds) {
            val fy = ((c.fy - cloudOffset) % 1f + 1f) % 1f
            drawCloud(c.x * w, fy * h, c.scale * min(w, h) * 0.06f)
        }

        fun screenY(worldY: Float): Float = (0.45f - (worldY - cam)) * h

        // Platforms (scattered, varying width, solid) + their spikes.
        for (p in state.platforms) {
            val topSy = screenY(p.worldY)
            val bottomSy = screenY(p.worldY - 0.03f)
            if (topSy < -60f || bottomSy > h + 60f) continue
            val left = (p.cx - p.halfWidth) * w
            val pw = p.halfWidth * 2f * w
            val isSpring = p.kind == BalloonViewModel.PlatformKind.SPRING
            drawRoundRect(
                if (isSpring) Color(0xFF66BB6A) else Color(0xFF8D6E63),
                topLeft = Offset(left, topSy),
                size = Size(pw, bottomSy - topSy),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            if (isSpring) {
                drawRoundRect(Color(0xFFC8E6C9), topLeft = Offset(left + pw / 2f - 10f, topSy - 8f), size = Size(20f, 8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            }
            // Top spikes over part of the surface.
            if (p.spikeSpan > 0f) {
                val zoneLeft = left + p.spikeStart * pw
                val zoneRight = left + (p.spikeStart + p.spikeSpan) * pw
                drawSpikes(zoneLeft, zoneRight, baseY = topSy, pointingUp = true)
            }
            // Bottom spikes across the underside.
            if (p.bottomSpikes) drawSpikes(left, left + pw, baseY = bottomSy, pointingUp = false)
        }

        // Coins.
        for (c in state.coins) {
            if (c.taken) continue
            val sy = screenY(c.worldY)
            if (sy < -20f || sy > h + 20f) continue
            drawCircle(Color(0xFFFFD54F), radius = min(w, h) * 0.022f, center = Offset(c.x * w, sy))
            drawCircle(Color(0xFFFFA000), radius = min(w, h) * 0.022f, center = Offset(c.x * w, sy), style = Stroke(width = 2f))
        }

        // Birds.
        for (b in state.birds) {
            val sy = screenY(b.worldY)
            if (sy < -30f || sy > h + 30f) continue
            val r = b.r * min(w, h)
            drawCircle(Color(0xFF4E342E), radius = r, center = Offset(b.x * w, sy))
            drawCircle(Color(0xFFFFB300), radius = r * 0.4f, center = Offset(b.x * w + (if (b.vx >= 0) r else -r), sy))
        }

        // Balloon — a true circle, sized to its (aspect-corrected) collision radius.
        val by = screenY(state.balloonWorldY)
        val bx = state.balloonX * w
        val br = BalloonViewModel.BALLOON_R * h
        drawCircle(Color(0xFFEF5350), radius = br, center = Offset(bx, by))
        drawCircle(Color.White.copy(alpha = 0.3f), radius = br * 0.35f, center = Offset(bx - br * 0.3f, by - br * 0.3f))
        if (state.intensity > 0.05f) {
            drawCircle(Color.White.copy(alpha = 0.25f * state.intensity), radius = br * (0.6f + state.intensity), center = Offset(bx, by + br * 0.9f))
        }
    }
}

/** A soft puff made of overlapping circles. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(cx: Float, cy: Float, r: Float) {
    val c = Color.White.copy(alpha = 0.85f)
    drawCircle(c, radius = r, center = Offset(cx, cy))
    drawCircle(c, radius = r * 0.8f, center = Offset(cx - r, cy + r * 0.2f))
    drawCircle(c, radius = r * 0.8f, center = Offset(cx + r, cy + r * 0.2f))
    drawCircle(c, radius = r * 0.7f, center = Offset(cx, cy - r * 0.5f))
}

private data class Cloud(val x: Float, val fy: Float, val scale: Float)

private val clouds = listOf(
    Cloud(0.15f, 0.20f, 0.9f), Cloud(0.80f, 0.45f, 1.2f), Cloud(0.50f, 0.72f, 1.0f),
    Cloud(0.30f, 0.10f, 0.7f), Cloud(0.65f, 0.33f, 1.1f), Cloud(0.90f, 0.88f, 0.8f),
    Cloud(0.10f, 0.60f, 1.0f), Cloud(0.45f, 0.95f, 0.9f), Cloud(0.72f, 0.05f, 0.85f),
)

/** Draw a row of triangular spikes between [xStart] and [xEnd], sitting on [baseY]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpikes(
    xStart: Float,
    xEnd: Float,
    baseY: Float,
    pointingUp: Boolean,
) {
    val span = xEnd - xStart
    if (span <= 0f) return
    val tooth = 18f
    val tip = 14f
    val path = Path()
    var x = xStart
    path.moveTo(x, baseY)
    while (x < xEnd - 0.5f) {
        val mid = min(x + tooth / 2f, xEnd)
        val end = min(x + tooth, xEnd)
        val tipY = if (pointingUp) baseY - tip else baseY + tip
        path.lineTo(mid, tipY)
        path.lineTo(end, baseY)
        x = end
    }
    path.lineTo(xEnd, if (pointingUp) baseY - tip else baseY + tip)
    path.lineTo(xEnd, baseY)
    path.close()
    drawPath(path, Color(0xFFB0BEC5))
    drawPath(path, Color(0xFF607D8B), style = Stroke(width = 1.5f))
}

@Composable
private fun IntensityMeter(intensity: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(18.dp)
            .height(140.dp)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(Sizes.lg))
            .padding(3.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(intensity.coerceIn(0f, 1f))
                .background(Color(0xFFFFB74D), RoundedCornerShape(Sizes.lg)),
        )
    }
}

// ---- Overlays ----------------------------------------------------------

@Composable
private fun MicRequiredScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
            modifier = Modifier.padding(Sizes.lg),
        ) {
            Icon(Icons.Filled.MicOff, contentDescription = null, modifier = Modifier.padding(Sizes.md), tint = MaterialTheme.colorScheme.primary)
            Text("Microphone required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "Balloon Escape is controlled by your voice. Grant microphone access to play.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = onRequest) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(Sizes.xs))
                Text("Allow microphone")
            }
        }
    }
}

@Composable
private fun CalibrationOverlay(title: String, subtitle: String, extra: @Composable () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.sm),
            modifier = Modifier.padding(Sizes.lg),
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            extra()
        }
    }
}

// ---- Lifecycle ---------------------------------------------------------

@Composable
private fun LockPortraitImmersive() {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val previousOrientation = activity.requestedOrientation
        val previousBarsBehavior = controller.systemBarsBehavior
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onDispose {
            activity.requestedOrientation = previousOrientation
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBarsBehavior
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) { if (ctx is Activity) return ctx; ctx = ctx.baseContext }
    return null
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=440,orientation=portrait")
@Composable
private fun BalloonScreenPreview() {
    MinigumTheme { BalloonScreen(navController = rememberNavController()) }
}
