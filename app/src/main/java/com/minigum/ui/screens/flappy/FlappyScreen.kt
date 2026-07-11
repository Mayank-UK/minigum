package com.minigum.ui.screens.flappy

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
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

@Composable
fun FlappyScreen(
    navController: NavController,
    viewModel: FlappyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }

    LockLandscapeImmersive()

    val overlayOpen = showHowTo || showPause || state.isGameOver
    LaunchedEffect(overlayOpen) {
        if (overlayOpen && state.isRunning) viewModel.pause()
    }

    // One haptic on death.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.isGameOver) {
        if (state.isGameOver) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BackHandler(enabled = !showHowTo) {
        when {
            showPause -> { showPause = false; viewModel.start() }
            state.isGameOver -> navController.navigateUp()
            else -> { viewModel.pause(); showPause = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val aspect = remember(constraints.maxWidth, constraints.maxHeight) {
                if (constraints.maxHeight > 0) constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat() else 16f / 9f
            }
            LaunchedEffect(aspect) { viewModel.configure(aspect) }
            Board(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.isRunning, state.isGameOver) {
                        // Tap anywhere to flap (disabled while overlays are up).
                        detectTapGestures(onTap = {
                            if (state.isRunning && !state.isGameOver) viewModel.flap()
                        })
                    },
            )
        }

        // Big live score, centred at the top — classic Flappy.
        if (state.isRunning && !state.isGameOver) {
            Text(
                "${state.score}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Sizes.md),
            )
        }

        // Best score, top-start.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Sizes.sm)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(Sizes.lg))
                .padding(horizontal = Sizes.md, vertical = Sizes.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Best ${state.bestScore}", color = Color.White, fontWeight = FontWeight.Bold)
        }

        IconButton(
            onClick = { viewModel.pause(); showPause = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Sizes.xxs),
        ) {
            Icon(Icons.Filled.Pause, contentDescription = "Pause")
        }

        if (showPause) {
            GamePauseOverlay(
                onResume = { showPause = false; viewModel.start() },
                onHowToPlay = { showPause = false; showHowTo = true },
                onLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.FLAPPY)) },
                onExit = { navController.navigateUp() },
            )
        }

        if (state.isGameOver && !showPause) {
            GameOverOverlay(
                title = "Game Over",
                stats = listOf(
                    GameStat("Score", "${state.score}"),
                    GameStat("Best", "${state.bestScore}"),
                ),
                onPlayAgain = { viewModel.restart() },
                onExit = { navController.navigateUp() },
                inlineLeaderboard = { InlineLeaderboard(LeaderboardGame.FLAPPY, state.score) },
                onFullLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.FLAPPY)) },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Flappy",
            instructions = flappyInstructions,
            onContinue = {
                showHowTo = false
                viewModel.start()
            },
        )
    }
}

private val flappyInstructions = listOf(
    "Tap anywhere on the screen to flap and gain a little height.",
    "Gravity is always pulling you down, so tap in short, steady beats.",
    "Fly through the gaps between the green pipes.",
    "Each pipe you pass scores a point; the gaps slowly shrink as you go.",
    "Don't hit a pipe or the ground — one touch ends the run.",
    "Tap the ✕ (or press Back) to pause or exit.",
)

@Composable
private fun LockLandscapeImmersive() {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val previousOrientation = activity.requestedOrientation
        val previousBarsBehavior = controller.systemBarsBehavior

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun Board(state: FlappyViewModel.GameState, modifier: Modifier = Modifier) {
    val skyTop = Color(0xFF4FC3F7)
    val skyBottom = Color(0xFFB3E5FC)
    val pipeColor = Color(0xFF66BB6A)
    val pipeShadow = Color(0xFF388E3C)
    val pipeCap = Color(0xFF4CAF50)
    val groundColor = Color(0xFFDED08A)
    val groundShadow = Color(0xFFBFAE6A)
    val birdBody = Color(0xFFFFD54F)
    val birdWing = Color(0xFFFFA000)
    val birdBeak = Color(0xFFFF7043)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val scale = h
        drawSky(skyTop, skyBottom, w, h)
        drawClouds(state, w, h)

        val groundY = FlappyViewModel.GROUND_Y * scale
        // Pipes (drawn behind the ground strip so their bases tuck under it).
        for (p in state.pipes) drawPipe(state, p, pipeColor, pipeShadow, pipeCap, scale)
        drawGround(state, groundColor, groundShadow, groundY, w, h)
        drawBird(state, birdBody, birdWing, birdBeak, scale)
    }
}

private fun DrawScope.drawSky(top: Color, bottom: Color, w: Float, h: Float) {
    drawRect(color = top, size = Size(w, h))
    // Simple two-stop vertical gradient via stacked rects.
    val stops = 14
    for (i in 0 until stops) {
        val t = i.toFloat() / stops
        val c = androidx.compose.ui.graphics.lerp(top, bottom, t)
        drawRect(color = c, topLeft = Offset(0f, h * t), size = Size(w, h / stops + 1f))
    }
}

/** A few drifting clouds for depth, parallax-scrolled slower than the pipes. */
private fun DrawScope.drawClouds(state: FlappyViewModel.GameState, w: Float, h: Float) {
    val drift = (state.scroll * 0.3f * h) % (w * 1.5f)
    val clouds = listOf(0.15f to 0.18f, 0.55f to 0.12f, 0.9f to 0.22f)
    for ((baseX, baseY) in clouds) {
        var x = baseX * w - drift
        if (x < -w * 0.2f) x += w * 1.5f
        val y = baseY * h
        val r = h * 0.05f
        val cloud = Color.White.copy(alpha = 0.85f)
        drawCircle(cloud, r, Offset(x, y))
        drawCircle(cloud, r * 0.8f, Offset(x + r, y + r * 0.2f))
        drawCircle(cloud, r * 0.7f, Offset(x - r, y + r * 0.2f))
    }
}

private fun DrawScope.drawPipe(
    state: FlappyViewModel.GameState,
    pipe: FlappyViewModel.Pipe,
    color: Color,
    shadow: Color,
    cap: Color,
    scale: Float,
) {
    val gapHalf = (0.3f - state.score * 0.004f).coerceAtLeast(0.2f) / 2f * scale
    val x = pipe.x * scale
    val pw = state.pipeW * scale
    val gapTop = (pipe.gapCenter - gapHalf / scale) * scale
    val gapBottom = (pipe.gapCenter + gapHalf / scale) * scale
    val groundY = FlappyViewModel.GROUND_Y * scale

    // Top segment: from ceiling to gap top.
    drawRect(color, Offset(x, 0f), Size(pw, gapTop))
    drawRect(shadow, Offset(x + pw * 0.7f, 0f), Size(pw * 0.3f, gapTop)) // right-edge shading
    drawRect(cap, Offset(x - pw * 0.08f, gapTop - scale * 0.03f), Size(pw * 1.16f, scale * 0.03f))
    // Bottom segment: from gap bottom to ground.
    drawRect(color, Offset(x, gapBottom), Size(pw, groundY - gapBottom))
    drawRect(shadow, Offset(x + pw * 0.7f, gapBottom), Size(pw * 0.3f, groundY - gapBottom))
    drawRect(cap, Offset(x - pw * 0.08f, gapBottom), Size(pw * 1.16f, scale * 0.03f))
}

private fun DrawScope.drawGround(
    state: FlappyViewModel.GameState,
    color: Color,
    shadow: Color,
    groundY: Float,
    w: Float,
    h: Float,
) {
    drawRect(color, Offset(0f, groundY), Size(w, h - groundY))
    drawRect(shadow, Offset(0f, groundY), Size(w, h * 0.012f))
    // Scrolling dashes on the ground for a sense of speed.
    val dashW = w * 0.06f
    val period = dashW * 2f
    val offset = (state.scroll * h) % period
    var x = -offset
    while (x < w) {
        drawRect(shadow, Offset(x, groundY + h * 0.03f), Size(dashW, h * 0.015f))
        x += period
    }
}

private fun DrawScope.drawBird(
    state: FlappyViewModel.GameState,
    body: Color,
    wing: Color,
    beak: Color,
    scale: Float,
) {
    val cx = state.birdX * scale
    val cy = state.birdY * scale
    val r = FlappyViewModel.BIRD_R * scale
    // Tilt: nose up when rising, dive when falling.
    val angle = (state.birdVy * 70f).coerceIn(-25f, 90f)
    rotate(angle, pivot = Offset(cx, cy)) {
        // Body.
        drawCircle(body, r, Offset(cx, cy))
        drawCircle(Color.White, r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.12f))
        // Belly.
        drawCircle(wing.copy(alpha = 0.5f), r * 0.7f, Offset(cx, cy + r * 0.35f))
        // Wing.
        drawOval(wing, Offset(cx - r * 1.1f, cy - r * 0.2f), Size(r * 1.1f, r * 0.7f))
        // Eye.
        drawCircle(Color.White, r * 0.32f, Offset(cx + r * 0.45f, cy - r * 0.4f))
        drawCircle(Color.Black, r * 0.16f, Offset(cx + r * 0.5f, cy - r * 0.4f))
        // Beak.
        drawEquilateral(beak, Offset(cx + r * 0.9f, cy), r * 0.5f)
    }
}

private fun DrawScope.drawEquilateral(color: Color, tip: Offset, size: Float) {
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(tip.x, tip.y) // point
        lineTo(tip.x - size, tip.y - size * 0.6f)
        lineTo(tip.x - size, tip.y + size * 0.6f)
        close()
    }
    drawPath(p, color)
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape")
@Composable
private fun FlappyScreenLandscapePreview() {
    MinigumTheme {
        FlappyScreen(navController = rememberNavController())
    }
}
