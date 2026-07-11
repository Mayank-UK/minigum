package com.minigum.ui.screens.runner

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Path
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
import com.minigum.domain.models.LeaderboardGame
import com.minigum.ui.components.GameOverOverlay
import com.minigum.ui.components.GamePauseOverlay
import com.minigum.ui.components.GameStat
import com.minigum.ui.navigation.leaderboardRoute
import com.minigum.ui.screens.leaderboard.InlineLeaderboard
import com.minigum.ui.components.HowToPlayDialog
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun RunnerScreen(
    navController: NavController,
    viewModel: RunnerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // UI-only state.
    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }

    // Entering the game: lock to portrait and go immersive full-screen.
    LockPortraitImmersive()

    val overlayOpen = showHowTo || showPause || state.isGameOver
    LaunchedEffect(overlayOpen) {
        if (overlayOpen && state.isRunning) viewModel.pause()
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
                if (constraints.maxHeight > 0) constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat() else 9f / 16f
            }
            LaunchedEffect(aspect) { viewModel.configure(aspect) }
            Board(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onSwipe = { dir -> handleSwipe(dir, viewModel) },
                onTap = viewModel::jump,
            )
        }

        HitFeedback(state = state)

        HudPill(
            metres = state.metres.toInt(),
            best = state.bestMetres,
            lives = state.lives,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Sizes.sm),
        )

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
                onLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.RUNNER)) },
                onExit = { navController.navigateUp() },
            )
        }

        if (state.isGameOver && !showPause) {
            GameOverOverlay(
                title = "Game Over",
                stats = listOf(
                    GameStat("Distance", "${state.metres.toInt()} m"),
                    GameStat("Best", "${state.bestMetres} m"),
                ),
                onPlayAgain = { viewModel.restart() },
                onExit = { navController.navigateUp() },
                inlineLeaderboard = { InlineLeaderboard(LeaderboardGame.RUNNER, state.metres.toInt()) },
                onFullLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.RUNNER)) },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Runner",
            instructions = runnerInstructions,
            onContinue = {
                showHowTo = false
                viewModel.start()
            },
        )
    }
}

private enum class SwipeDir { LEFT, RIGHT, UP, DOWN }

private fun handleSwipe(dir: SwipeDir, viewModel: RunnerViewModel) {
    when (dir) {
        SwipeDir.LEFT -> viewModel.moveLane(-1)
        SwipeDir.RIGHT -> viewModel.moveLane(1)
        SwipeDir.UP -> viewModel.jump()
        SwipeDir.DOWN -> viewModel.slide()
    }
}

private val runnerInstructions = listOf(
    "Swipe left or right to switch between the three lanes.",
    "Swipe up (or just tap) to jump — clear the orange hurdles.",
    "Swipe down to slide — duck under the blue overhead bars.",
    "Red blocks fill the whole lane: switch lanes to avoid them.",
    "You have three lives; a hit costs one with a brief safe window.",
    "You speed up the farther you run, so react fast.",
    "Tap the ✕ (or press Back) to pause or exit.",
)

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
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private val HitFlashColor = Color(0xFFE53935)
private const val FLASH_PEAK = 0.5f
private const val FLASH_MS = 400

@Composable
private fun BoxScope.HitFeedback(state: RunnerViewModel.GameState) {
    val haptic = LocalHapticFeedback.current
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(state.hitEventId) {
        if (state.hitEventId > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            flashAlpha.snapTo(FLASH_PEAK)
            flashAlpha.animateTo(0f, tween(FLASH_MS, easing = FastOutSlowInEasing))
        }
    }

    if (flashAlpha.value > 0f) {
        Box(modifier = Modifier.fillMaxSize().background(HitFlashColor.copy(alpha = flashAlpha.value)))
    }
}

@Composable
private fun HudPill(metres: Int, best: Int, lives: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(Sizes.lg))
            .padding(horizontal = Sizes.md, vertical = Sizes.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
    ) {
        Text("$metres m", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Best $best", color = Color.White.copy(alpha = 0.85f))
        Text("♥ $lives", color = Color.White.copy(alpha = 0.95f))
    }
}

@Composable
private fun Board(
    state: RunnerViewModel.GameState,
    modifier: Modifier = Modifier,
    onSwipe: (SwipeDir) -> Unit,
    onTap: () -> Unit,
) {
    val roadColor = Color(0xFF2B2B33)
    val edgeColor = Color(0xFF3A3A44)
    val dashColor = Color.White.copy(alpha = 0.32f)
    val lowColor = Color(0xFFFFAB40)
    val highColor = Color(0xFF42A5F5)
    val fullColor = Color(0xFFEF5350)
    val playerColor = MaterialTheme.colorScheme.primary
    val shadowColor = Color.Black.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                val threshold = 28f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var startX = down.position.x
                    var startY = down.position.y
                    var consumed = false
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        if (change.pressed) {
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (!consumed && (abs(dx) > threshold || abs(dy) > threshold)) {
                                val dir = if (abs(dx) > abs(dy)) {
                                    if (dx > 0) SwipeDir.RIGHT else SwipeDir.LEFT
                                } else {
                                    if (dy > 0) SwipeDir.DOWN else SwipeDir.UP
                                }
                                onSwipe(dir)
                                consumed = true
                            }
                        } else {
                            // Finger lifted: a tiny, unhandled movement counts as a tap = jump.
                            if (!consumed) onTap()
                            active = false
                        }
                    }
                }
            },
    ) {
        drawScene(
            state, roadColor, edgeColor, dashColor,
            lowColor, highColor, fullColor, playerColor, shadowColor,
        )
    }
}

/** Fake-3D projection: lanes/obstacles recede to a vanishing point near the top. */
private fun DrawScope.drawScene(
    state: RunnerViewModel.GameState,
    roadColor: Color,
    edgeColor: Color,
    dashColor: Color,
    lowColor: Color,
    highColor: Color,
    fullColor: Color,
    playerColor: Color,
    shadowColor: Color,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.34f
    val baseY = h * 0.8f
    val vx = w * 0.5f
    val laneOff = w * 0.3f
    val perspK = 1.6f

    fun scaleZ(z: Float): Float = 1f / (1f + z * perspK)
    fun projX(lane: Float, z: Float): Float = vx + lane * laneOff * scaleZ(z)
    fun projY(z: Float): Float = horizonY + (baseY - horizonY) * scaleZ(z)

    // Road bed: trapezoid from the horizon to past the bottom of the screen.
    val farZ = 1.3f
    val nearZ = -0.3f
    val road = Path().apply {
        moveTo(projX(-1.6f, farZ), projY(farZ))
        lineTo(projX(1.6f, farZ), projY(farZ))
        lineTo(projX(1.6f, nearZ), projY(nearZ))
        lineTo(projX(-1.6f, nearZ), projY(nearZ))
        close()
    }
    drawPath(road, roadColor)

    // Side rails.
    for (edge in listOf(-1.6f, 1.6f)) {
        drawLine(
            edgeColor,
            Offset(projX(edge, farZ), projY(farZ)),
            Offset(projX(edge, nearZ), projY(nearZ)),
            strokeWidth = h * 0.006f,
        )
    }

    // Scrolling lane dividers convey forward speed.
    val dashSpacing = 0.12f
    var z = (state.metres * 0.02f) % dashSpacing
    while (z < 1.2f) {
        val sc = scaleZ(z)
        val y = projY(z)
        val dh = h * 0.03f * sc
        val dw = w * 0.012f * sc
        for (div in listOf(-0.5f, 0.5f)) {
            val x = projX(div, z)
            drawRect(dashColor, Offset(x - dw / 2f, y - dh), Size(dw, dh))
        }
        z += dashSpacing
    }

    // Obstacles, near-to-far so closer ones overlap farther ones.
    val sorted = state.obstacles.sortedByDescending { it.z }
    for (o in sorted) {
        val sc = scaleZ(o.z)
        val cx = projX(o.lane.toFloat(), o.z)
        val groundY = projY(o.z)
        val ow = laneOff * 0.5f * sc
        when (o.kind) {
            RunnerViewModel.ObstacleKind.LOW -> drawHurdle(cx, groundY, ow, sc, h, lowColor)
            RunnerViewModel.ObstacleKind.HIGH -> drawGate(cx, groundY, ow, sc, h, highColor)
            RunnerViewModel.ObstacleKind.FULL -> drawTrain(cx, groundY, ow, sc, h, fullColor)
        }
    }

    // Player: a runner character, raised by jump height, crouched while sliding.
    val px = projX(state.playerLane, 0f)
    val feetY = baseY - state.playerY * h
    val pw = laneOff * 0.5f
    val blink = state.invulnRemaining > 0f && (state.invulnRemaining * 12f).toInt() % 2 == 0
    // Shadow on the ground (stays put even while jumping).
    drawOval(shadowColor, topLeft = Offset(px - pw * 0.5f, baseY - h * 0.01f), size = Size(pw, h * 0.02f))
    if (!blink) {
        val phase = state.metres * 0.9f
        val swing = if (state.playerY > 0.001f) 0f else sin(phase) // tuck legs mid-air
        drawRunner(px, feetY, h, state.isSliding, swing, playerColor)
    }
}

/** LOW: a striped hazard barrier on legs — reads as "jump over". */
private fun DrawScope.drawHurdle(cx: Float, groundY: Float, ow: Float, sc: Float, h: Float, base: Color) {
    val oh = h * 0.05f * sc
    val left = cx - ow / 2f
    val top = groundY - oh
    val dark = Color(0xFF3E2723)
    drawRoundRect(base, Offset(left, top), Size(ow, oh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(oh * 0.2f))
    // Hazard stripes.
    val n = 4
    for (i in 0 until n) {
        val sx = left + ow * (i + 0.1f) / n
        drawLine(dark, Offset(sx, top), Offset(sx + ow * 0.12f, top + oh), h * 0.01f * sc)
    }
    // Legs.
    drawLine(dark, Offset(left + ow * 0.2f, groundY), Offset(left + ow * 0.2f, top + oh), h * 0.012f * sc)
    drawLine(dark, Offset(left + ow * 0.8f, groundY), Offset(left + ow * 0.8f, top + oh), h * 0.012f * sc)
}

/** HIGH: an overhead sign on posts with downward chevrons — reads as "slide under". */
private fun DrawScope.drawGate(cx: Float, groundY: Float, ow: Float, sc: Float, h: Float, base: Color) {
    val oh = h * 0.05f * sc
    val gap = h * 0.05f * sc
    val barTop = groundY - gap - oh
    val post = Color(0xFF455A64)
    drawLine(post, Offset(cx - ow / 2f, groundY), Offset(cx - ow / 2f, barTop), h * 0.014f * sc)
    drawLine(post, Offset(cx + ow / 2f, groundY), Offset(cx + ow / 2f, barTop), h * 0.014f * sc)
    drawRoundRect(base, Offset(cx - ow / 2f - ow * 0.08f, barTop), Size(ow * 1.16f, oh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(oh * 0.25f))
    // Downward chevrons hint "duck".
    val mid = barTop + oh / 2f
    drawLine(Color.White, Offset(cx - ow * 0.22f, mid), Offset(cx, mid + oh * 0.22f), h * 0.012f * sc)
    drawLine(Color.White, Offset(cx + ow * 0.22f, mid), Offset(cx, mid + oh * 0.22f), h * 0.012f * sc)
}

/** FULL: a train/tram with windows and wheels — reads as "blocked, change lane". */
private fun DrawScope.drawTrain(cx: Float, groundY: Float, ow: Float, sc: Float, h: Float, base: Color) {
    val oh = h * 0.16f * sc
    val left = cx - ow / 2f
    val top = groundY - oh
    drawRoundRect(base, Offset(left, top), Size(ow, oh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(oh * 0.12f))
    drawRect(Color(0xFFB71C1C), Offset(left, top), Size(ow, oh * 0.18f)) // roof stripe
    // Windows.
    val win = Color(0xFFB3E5FC)
    val wn = 3
    val gap = ow * 0.08f
    val ww = (ow - gap * (wn + 1)) / wn
    for (i in 0 until wn) {
        val wx = left + gap + i * (ww + gap)
        drawRoundRect(win, Offset(wx, top + oh * 0.26f), Size(ww, oh * 0.3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(oh * 0.05f))
    }
    // Wheels.
    drawCircle(Color(0xFF212121), oh * 0.13f, Offset(left + ow * 0.25f, groundY))
    drawCircle(Color(0xFF212121), oh * 0.13f, Offset(left + ow * 0.75f, groundY))
}

/** A small runner: head, torso, swinging arms and legs; crouches when sliding. */
private fun DrawScope.drawRunner(cx: Float, feetY: Float, h: Float, sliding: Boolean, swing: Float, suit: Color) {
    val skin = Color(0xFFFFCC80)
    val shoe = Color(0xFF263238)
    val stroke = h * 0.014f

    if (sliding) {
        // Crouched slide: body leans forward, head low.
        val headY = feetY - h * 0.045f
        drawLine(suit, Offset(cx - h * 0.03f, feetY - h * 0.02f), Offset(cx + h * 0.03f, headY + h * 0.01f), stroke)
        drawLine(shoe, Offset(cx - h * 0.035f, feetY - h * 0.005f), Offset(cx + h * 0.03f, feetY - h * 0.005f), stroke)
        drawCircle(skin, h * 0.02f, Offset(cx + h * 0.05f, headY))
        return
    }

    val hipY = feetY - h * 0.05f
    val shoulderY = hipY - h * 0.045f
    val headR = h * 0.021f
    val sw = h * 0.022f * swing
    // Legs (opposite stride).
    drawLine(shoe, Offset(cx, hipY), Offset(cx - sw, feetY), stroke)
    drawLine(shoe, Offset(cx, hipY), Offset(cx + sw, feetY), stroke)
    // Torso.
    drawLine(suit, Offset(cx, shoulderY), Offset(cx, hipY), stroke * 1.4f)
    // Arms (counter-swing to legs).
    drawLine(suit, Offset(cx, shoulderY + h * 0.005f), Offset(cx + sw, shoulderY + h * 0.035f), stroke)
    drawLine(suit, Offset(cx, shoulderY + h * 0.005f), Offset(cx - sw, shoulderY + h * 0.035f), stroke)
    // Head.
    drawCircle(skin, headR, Offset(cx, shoulderY - headR * 0.9f))
}

@Preview(showBackground = true, device = "spec:width=800dp,height=1280dp,dpi=240,orientation=portrait")
@Composable
private fun RunnerScreenPortraitPreview() {
    MinigumTheme {
        RunnerScreen(navController = rememberNavController())
    }
}
