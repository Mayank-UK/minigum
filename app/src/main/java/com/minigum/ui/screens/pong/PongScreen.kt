package com.minigum.ui.screens.pong

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
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
import com.minigum.ui.components.GamePauseOverlay
import com.minigum.ui.components.HowToPlayDialog
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes

@Composable
fun PongScreen(
    navController: NavController,
    viewModel: PongViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // UI-only state.
    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }

    // Entering the game: lock to landscape and go immersive full-screen.
    // On exit, restore the previous orientation and system bars.
    LockLandscapeImmersive()

    // While any overlay is up, the game must stay paused.
    val overlayOpen = showHowTo || showPause || state.isGameOver
    LaunchedEffect(overlayOpen) {
        if (overlayOpen && state.isRunning) viewModel.pause()
    }

    // Back press opens the pause menu (or resumes / exits when over).
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
        // Full-screen playfield. The field is `aspect` wide and 1 tall; since the
        // screen is locked to landscape it fills the whole area with no letterboxing.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val aspect = remember(constraints.maxWidth, constraints.maxHeight) {
                if (constraints.maxHeight > 0) {
                    constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat()
                } else {
                    16f / 9f
                }
            }
            LaunchedEffect(aspect) {
                viewModel.configure(aspect)
            }
            Board(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onPlayerTarget = viewModel::setPlayerTarget,
            )
        }

        // Color flash + banner fired on each point; sits above the board, below the HUD.
        PointCelebration(state = state)

        // Match score, shown over the board during play.
        ScorePill(
            playerScore = state.playerScore,
            aiScore = state.aiScore,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Sizes.sm),
        )

        // The single persistent control: pause.
        IconButton(
            onClick = {
                viewModel.pause()
                showPause = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Sizes.xxs),
        ) {
            Icon(Icons.Filled.Pause, contentDescription = "Pause")
        }

        if (showPause) {
            GamePauseOverlay(
                onResume = {
                    showPause = false
                    viewModel.start()
                },
                onHowToPlay = {
                    showPause = false
                    showHowTo = true
                },
                onExit = { navController.navigateUp() },
            )
        }

        if (state.isGameOver && !showPause) {
            GameOverOverlay(
                winner = state.winner,
                playerScore = state.playerScore,
                aiScore = state.aiScore,
                onPlayAgain = { viewModel.restart() },
                onExit = { navController.navigateUp() },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Pong",
            instructions = pongInstructions,
            onContinue = {
                showHowTo = false
                viewModel.start()
            },
        )
    }
}

private val pongInstructions = listOf(
    "Drag your finger anywhere on the board to slide your paddle (left side) up and down.",
    "Hit the ball so it gets past the AI paddle on the right to score a point.",
    "Where the ball strikes your paddle changes its angle — edge hits shoot off sharper.",
    "Each paddle hit speeds the ball up; long rallies get fast, so stay sharp.",
    "First to ${PongViewModel.WIN_SCORE} points wins the match.",
    "Tap the ✕ (or press Back) to pause, change difficulty, or exit.",
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
private fun ScorePill(
    playerScore: Int,
    aiScore: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(Sizes.lg))
            .padding(horizontal = Sizes.md, vertical = Sizes.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
    ) {
        Text("You $playerScore", color = Color.White, fontWeight = FontWeight.Bold)
        Text("$aiScore AI", color = Color.White.copy(alpha = 0.85f))
    }
}

private val ScoreGreen = Color(0xFF4CAF50)
private val ScoreRed = Color(0xFFE53935)
private const val FLASH_PEAK = 0.45f
private const val FLASH_MS = 650

/**
 * Per-point feedback layered over the board: a haptic, a full-screen color
 * flash that fades out, and a centered banner shown during the post-point freeze.
 * Green celebrates the player scoring; red commiserates the AI scoring.
 */
@Composable
private fun BoxScope.PointCelebration(state: PongViewModel.GameState) {
    val haptic = LocalHapticFeedback.current
    val flashAlpha = remember { Animatable(0f) }

    // Fire one haptic + a fading flash per point. scoreEventId bumps each point.
    LaunchedEffect(state.scoreEventId) {
        if (state.scoreEventId > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            flashAlpha.snapTo(FLASH_PEAK)
            flashAlpha.animateTo(0f, tween(FLASH_MS, easing = FastOutSlowInEasing))
        }
    }

    val isPlayerScore = state.lastScorer == PongViewModel.Winner.PLAYER
    val accent = if (isPlayerScore) ScoreGreen else ScoreRed

    if (flashAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accent.copy(alpha = flashAlpha.value)),
        )
    }

    AnimatedVisibility(
        visible = state.pendingServe != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        val text = if (isPlayerScore) "You scored!" else "Point — AI"
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .background(accent.copy(alpha = 0.9f), RoundedCornerShape(Sizes.lg))
                .padding(horizontal = Sizes.lg, vertical = Sizes.md),
        )
    }
}

@Composable
private fun Board(
    state: PongViewModel.GameState,
    modifier: Modifier = Modifier,
    onPlayerTarget: (Float) -> Unit,
) {
    val playerColor = MaterialTheme.colorScheme.primary
    val aiColor = MaterialTheme.colorScheme.secondary
    val ballColor = Color.White
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                // Drag anywhere: the player paddle tracks the finger's vertical position.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pointer = true
                    while (pointer) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val yFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                                onPlayerTarget(yFraction)
                            }
                        }
                        pointer = event.changes.any { it.pressed }
                    }
                }
            },
    ) {
        // The field is `aspect` wide and 1 tall, mapped 1:1 to the canvas height.
        val scale = size.height
        drawCenterLine(lineColor, state.aspect, scale)
        drawPaddle(playerColor, PongViewModel.playerEdgeX(), state.playerY, scale)
        drawPaddle(aiColor, PongViewModel.aiEdgeX(state.aspect), state.aiY, scale)
        // Hide the ball during the post-point freeze; it reappears at center on serve.
        if (state.pendingServe == null) {
            drawBall(ballColor, state.ball, scale)
        }
    }
}

private fun DrawScope.drawCenterLine(color: Color, aspect: Float, scale: Float) {
    val centerX = aspect / 2f * scale
    val dash = size.height * 0.04f
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = color,
            start = Offset(centerX, y),
            end = Offset(centerX, (y + dash).coerceAtMost(size.height)),
            strokeWidth = 4f,
        )
        y += dash * 2f
    }
}

private fun DrawScope.drawPaddle(color: Color, centerX: Float, centerY: Float, scale: Float) {
    val w = PongViewModel.PADDLE_WIDTH * scale
    val h = PongViewModel.PADDLE_HEIGHT * scale
    drawRect(
        color = color,
        topLeft = Offset(centerX * scale - w / 2f, centerY * scale - h / 2f),
        size = Size(w, h),
    )
}

private fun DrawScope.drawBall(color: Color, ball: PongViewModel.Ball, scale: Float) {
    drawCircle(
        color = color,
        radius = PongViewModel.BALL_RADIUS * scale,
        center = Offset(ball.x * scale, ball.y * scale),
    )
}

@Composable
private fun GameOverOverlay(
    winner: PongViewModel.Winner?,
    playerScore: Int,
    aiScore: Int,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val youWon = winner == PongViewModel.Winner.PLAYER
    val title = if (youWon) "You win!" else "AI wins"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.sm),
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("You $playerScore — $aiScore AI", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(Sizes.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.md)) {
                Button(onClick = onPlayAgain) { Text("Play again") }
                Button(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape")
@Composable
private fun PongScreenLandscapePreview() {
    MinigumTheme {
        PongScreen(navController = rememberNavController())
    }
}
