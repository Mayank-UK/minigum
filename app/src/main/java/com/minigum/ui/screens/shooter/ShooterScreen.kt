package com.minigum.ui.screens.shooter

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.FilterChip
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
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun ShooterScreen(
    navController: NavController,
    viewModel: ShooterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // UI-only state.
    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }

    // Entering the game: lock to portrait and go immersive full-screen.
    // On exit, restore the previous orientation and system bars.
    LockPortraitImmersive()

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
        // Full-screen playfield. The field is `aspect` wide and 1 tall; in portrait
        // it is tall and narrow, which suits a vertical shooter.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val aspect = remember(constraints.maxWidth, constraints.maxHeight) {
                if (constraints.maxHeight > 0) {
                    constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat()
                } else {
                    9f / 16f
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

        // Red flash + haptic fired each time the player takes a hit.
        HitFeedback(state = state)

        // Score / wave / lives, with active buffs and combo below it.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Sizes.sm),
            verticalArrangement = Arrangement.spacedBy(Sizes.xs),
        ) {
            HudPill(
                score = state.score,
                best = state.bestScore,
                wave = state.wave,
                lives = state.lives,
            )
            BuffRow(state = state)
        }

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
                settings = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
                    ) {
                        Text("Difficulty", color = Color.White)
                        ShooterViewModel.Difficulty.entries.forEach { difficulty ->
                            FilterChip(
                                selected = state.difficulty == difficulty,
                                onClick = { viewModel.setDifficulty(difficulty) },
                                label = { Text(difficulty.label) },
                            )
                        }
                    }
                },
            )
        }

        if (state.isGameOver && !showPause) {
            GameOverOverlay(
                score = state.score,
                best = state.bestScore,
                wave = state.wave,
                onPlayAgain = { viewModel.restart() },
                onExit = { navController.navigateUp() },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Shooter",
            instructions = shooterInstructions,
            onContinue = {
                showHowTo = false
                viewModel.start()
            },
        )
    }
}

private val shooterInstructions = listOf(
    "Drag your finger anywhere to slide your ship left and right.",
    "Your ship fires upward automatically — focus on aiming and dodging.",
    "Shoot the aliens before their marching block reaches your line.",
    "Killing aliens fast builds a combo multiplier — chain kills for up to ×4 score.",
    "Some aliens (top rows) take two hits. Killed aliens may drop power-ups:",
    "⚡ Rapid fire, ✦ Triple shot, or a rare ❤ extra life — fly into them to grab them.",
    "Watch for falling bombs — each hit costs a life, with a brief safe window after.",
    "Tap the ✕ (or press Back) to pause, change difficulty, or exit.",
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
private const val FLASH_MS = 450

/**
 * Per-hit feedback layered over the board: a haptic and a fading red flash,
 * fired once each time the player loses a life. hitEventId bumps per hit.
 */
@Composable
private fun BoxScope.HitFeedback(state: ShooterViewModel.GameState) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HitFlashColor.copy(alpha = flashAlpha.value)),
        )
    }
}

@Composable
private fun HudPill(score: Int, best: Int, wave: Int, lives: Int) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(Sizes.lg))
            .padding(horizontal = Sizes.md, vertical = Sizes.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
    ) {
        Text("Score $score", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Wave $wave", color = Color.White.copy(alpha = 0.85f))
        Text("Best $best", color = Color.White.copy(alpha = 0.85f))
        Text("♥ $lives", color = Color.White.copy(alpha = 0.95f))
    }
}

/** Active buffs and the current combo, shown only while relevant. */
@Composable
private fun BuffRow(state: ShooterViewModel.GameState) {
    val chips = mutableListOf<Pair<String, Color>>()
    if (state.rapidRemaining > 0f) {
        chips += "⚡ ${state.rapidRemaining.roundToInt()}" to Color(0xFFFFD54F)
    }
    if (state.spreadRemaining > 0f) {
        chips += "✦ ${state.spreadRemaining.roundToInt()}" to Color(0xFFFFAB40)
    }
    if (state.combo >= 2) {
        val mult = ShooterViewModel.comboMultiplier(state.combo)
        chips += "Combo ×${mult} (${state.combo})" to Color(0xFF66BB6A)
    }
    if (chips.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(Sizes.xs)) {
        chips.forEach { (label, color) ->
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(color.copy(alpha = 0.85f), RoundedCornerShape(Sizes.sm))
                    .padding(horizontal = Sizes.sm, vertical = Sizes.xxs),
            )
        }
    }
}

private data class Star(val x: Float, val y: Float, val speed: Float, val radiusPx: Float)

@Composable
private fun Board(
    state: ShooterViewModel.GameState,
    modifier: Modifier = Modifier,
    onPlayerTarget: (Float) -> Unit,
) {
    val shipColor = MaterialTheme.colorScheme.primary
    val bulletColor = Color.White
    val bombColor = Color(0xFFFF5252)
    // Aliens tint by row: top rows (worth more, often tough) run hot.
    val alienRowColors = listOf(
        Color(0xFFBA68C8),
        Color(0xFFFF5252),
        Color(0xFFFFAB40),
        Color(0xFFFFD54F),
        Color(0xFF66BB6A),
    )
    val toughColor = Color(0xFF7986CB)
    val starColor = Color.White

    // Scrolling starfield: a single looping fraction drives each star's wrap.
    val starT by rememberInfiniteTransition(label = "stars")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(STAR_SCROLL_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "starT",
        )
    val stars = remember { List(STAR_COUNT) { randomStar() } }

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                // Drag anywhere: the ship tracks the finger's horizontal position.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pointer = true
                    while (pointer) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val xFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                onPlayerTarget(xFraction)
                            }
                        }
                        pointer = event.changes.any { it.pressed }
                    }
                }
            },
    ) {
        // The field is `aspect` wide and 1 tall, mapped 1:1 to the canvas height.
        val scale = size.height
        drawStars(stars, starT, starColor)
        drawAliens(state, alienRowColors, toughColor, scale)
        drawPowerUps(state, scale)
        drawExplosions(state, scale)
        drawBullets(state, bulletColor, scale)
        drawBombs(state, bombColor, scale)
        drawShip(state, shipColor, scale)
    }
}

private fun DrawScope.drawStars(stars: List<Star>, t: Float, color: Color) {
    for (star in stars) {
        val y = ((star.y + t * star.speed) % 1f) * size.height
        val x = star.x * size.width
        drawCircle(color = color.copy(alpha = 0.6f), radius = star.radiusPx, center = Offset(x, y))
    }
}

// Pixel-art invader sprites (11 wide). 'X' = filled cell.
private val INVADER_PATTERNS = listOf(
    listOf(
        "..X.....X..",
        "...X...X...",
        "..XXXXXXX..",
        ".XX.XXX.XX.",
        "XXXXXXXXXXX",
        "X.XXXXXXX.X",
        "X.X.....X.X",
        "...XX.XX...",
    ),
    listOf(
        "...XXXXX...",
        ".XXXXXXXXX.",
        "XXXXXXXXXXX",
        "XX.X.X.X.XX",
        "XXXXXXXXXXX",
        "..XX...XX..",
        ".XX.....XX.",
        "X.X.....X.X",
    ),
)
private val INVADER_TOUGH = listOf(
    "....XXX....",
    "...XXXXX...",
    "..XXXXXXX..",
    ".XX.XXX.XX.",
    "XXXXXXXXXXX",
    "XX.XXXXX.XX",
    "XX.X...X.XX",
    ".X..X.X..X.",
)

private fun DrawScope.drawShip(state: ShooterViewModel.GameState, color: Color, scale: Float) {
    // Blink the ship while invulnerable so the player sees the safe window.
    if (state.invulnRemaining > 0f && (state.invulnRemaining * 12f).toInt() % 2 == 0) return
    val cx = state.playerX * scale
    val cy = ShooterViewModel.PLAYER_Y * scale
    val w = state.playerHalfW * 2f * scale
    val h = ShooterViewModel.PLAYER_HALF_H * 2f * scale
    val flame = Color(0xFFFFAB40)
    val cockpit = Color(0xFFB3E5FC)

    // Engine flame.
    drawPath(
        path(listOf(Offset(cx - w * 0.12f, cy + h * 0.3f), Offset(cx, cy + h * 0.8f), Offset(cx + w * 0.12f, cy + h * 0.3f))),
        color = flame,
    )
    // Wings.
    val wing = color.copy(alpha = 0.7f)
    drawPath(path(listOf(Offset(cx - w * 0.2f, cy), Offset(cx - w * 0.55f, cy + h * 0.55f), Offset(cx - w * 0.05f, cy + h * 0.4f))), color = wing)
    drawPath(path(listOf(Offset(cx + w * 0.2f, cy), Offset(cx + w * 0.55f, cy + h * 0.55f), Offset(cx + w * 0.05f, cy + h * 0.4f))), color = wing)
    // Hull.
    drawPath(path(listOf(Offset(cx, cy - h * 0.9f), Offset(cx + w * 0.22f, cy + h * 0.3f), Offset(cx - w * 0.22f, cy + h * 0.3f))), color = color)
    // Cockpit.
    drawCircle(cockpit, w * 0.1f, Offset(cx, cy - h * 0.15f))
    // Spread buff: extra barrels at the wing tips.
    if (state.spreadRemaining > 0f) {
        val bw = w * 0.08f
        drawRect(color, Offset(cx - w * 0.5f - bw / 2f, cy - h * 0.4f), Size(bw, h * 0.5f))
        drawRect(color, Offset(cx + w * 0.5f - bw / 2f, cy - h * 0.4f), Size(bw, h * 0.5f))
    }
}

private fun path(points: List<Offset>) = androidx.compose.ui.graphics.Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

private fun DrawScope.drawAliens(
    state: ShooterViewModel.GameState,
    rowColors: List<Color>,
    toughColor: Color,
    scale: Float,
) {
    for (alien in state.aliens) {
        if (!alien.alive) continue
        val cx = (state.groupX + alien.col * state.cellW) * scale
        val cy = (state.groupY + alien.row * ShooterViewModel.ALIEN_CELL_H) * scale
        val pattern = if (alien.maxHp >= 2) INVADER_TOUGH else INVADER_PATTERNS[alien.row % INVADER_PATTERNS.size]
        // Tough aliens are indigo; a damaged tough alien (hp 1 of 2) dims to show it.
        val color = when {
            alien.maxHp >= 2 && alien.hp >= 2 -> toughColor
            alien.maxHp >= 2 -> toughColor.copy(alpha = 0.55f)
            else -> rowColors.getOrElse(alien.row) { rowColors.last() }
        }
        val cols = pattern[0].length
        val rows = pattern.size
        val cell = (state.alienHalfW * 2f * scale) / cols
        val left = cx - cols * cell / 2f
        val top = cy - rows * cell / 2f
        for (r in 0 until rows) {
            val line = pattern[r]
            for (c in 0 until cols) {
                if (line[c] == 'X') {
                    drawRect(color, Offset(left + c * cell, top + r * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

private fun DrawScope.drawBullets(state: ShooterViewModel.GameState, color: Color, scale: Float) {
    // Compact cyan diamond bolts — about bomb-sized, but a different shape and
    // colour from the round red enemy bombs so the two never read the same.
    val cyan = Color(0xFF40C4FF)
    val s = ShooterViewModel.BOMB_HALF_W * 1.4f * scale
    val w = ShooterViewModel.BULLET_HALF_W * 2f * scale
    for (bullet in state.bullets) {
        val x = bullet.x * scale
        val y = bullet.y * scale
        // Short tail behind the bolt (it travels upward).
        drawRect(cyan.copy(alpha = 0.3f), Offset(x - w / 2f, y), Size(w, s * 1.2f))
        // Diamond head + bright core.
        drawPath(path(listOf(Offset(x, y - s), Offset(x + s * 0.65f, y), Offset(x, y + s), Offset(x - s * 0.65f, y))), color = cyan)
        drawCircle(color, s * 0.28f, Offset(x, y))
    }
}

private fun DrawScope.drawBombs(state: ShooterViewModel.GameState, color: Color, scale: Float) {
    val r = ShooterViewModel.BOMB_HALF_W * scale
    for (bomb in state.bombs) {
        val x = bomb.x * scale
        val y = bomb.y * scale
        // Flame trail above (the bomb is falling from above).
        drawPath(
            path(listOf(Offset(x - r * 0.6f, y - r), Offset(x, y - r * 2.6f), Offset(x + r * 0.6f, y - r))),
            color = Color(0xFFFFAB40),
        )
        drawCircle(color, r, Offset(x, y)) // red body
        drawCircle(Color(0xFFB71C1C), r * 0.6f, Offset(x, y)) // dark core
        drawCircle(Color.White.copy(alpha = 0.8f), r * 0.22f, Offset(x - r * 0.3f, y - r * 0.3f)) // highlight
    }
}

private fun DrawScope.drawPowerUps(state: ShooterViewModel.GameState, scale: Float) {
    val r = ShooterViewModel.POWERUP_HALF * scale
    for (pu in state.powerUps) {
        val center = Offset(pu.x * scale, pu.y * scale)
        val (fill, mark) = when (pu.kind) {
            ShooterViewModel.PowerKind.RAPID -> Color(0xFFFFD54F) to Color.Black       // ⚡ dot
            ShooterViewModel.PowerKind.SPREAD -> Color(0xFFFFAB40) to Color.White      // ✦ spread
            ShooterViewModel.PowerKind.LIFE -> Color(0xFF66BB6A) to Color.White        // ❤ plus
        }
        drawCircle(color = fill, radius = r, center = center)
        when (pu.kind) {
            ShooterViewModel.PowerKind.RAPID -> drawCircle(color = mark, radius = r * 0.35f, center = center)
            ShooterViewModel.PowerKind.SPREAD -> {
                val d = r * 0.6f
                drawLine(mark, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), strokeWidth = r * 0.3f)
                drawLine(mark, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), strokeWidth = r * 0.3f)
            }
            ShooterViewModel.PowerKind.LIFE -> {
                val d = r * 0.6f
                drawLine(mark, Offset(center.x - d, center.y), Offset(center.x + d, center.y), strokeWidth = r * 0.3f)
                drawLine(mark, Offset(center.x, center.y - d), Offset(center.x, center.y + d), strokeWidth = r * 0.3f)
            }
        }
    }
}

private fun DrawScope.drawExplosions(state: ShooterViewModel.GameState, scale: Float) {
    val base = state.alienHalfW * scale
    for (e in state.explosions) {
        val progress = 1f - (e.remaining / ShooterViewModel.EXPLOSION_DURATION_S).coerceIn(0f, 1f)
        val radius = base * (0.6f + progress * 2.2f)
        val alpha = (1f - progress).coerceIn(0f, 1f)
        drawCircle(color = Color(0xFFFFAB40).copy(alpha = alpha * 0.8f), radius = radius, center = Offset(e.x * scale, e.y * scale))
        drawCircle(color = Color.White.copy(alpha = alpha * 0.6f), radius = radius * 0.5f, center = Offset(e.x * scale, e.y * scale))
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    best: Int,
    wave: Int,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
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
            Text("Game Over", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Score: $score", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("Reached wave $wave", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
            Text("Best: $best", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(Sizes.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.md)) {
                Button(onClick = onPlayAgain) { Text("Play again") }
                Button(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

private const val STAR_COUNT = 50
private const val STAR_SCROLL_MS = 4000

private fun randomStar(): Star = Star(
    x = Random.nextFloat(),
    y = Random.nextFloat(),
    speed = 0.2f + Random.nextFloat() * 0.8f,
    radiusPx = (1f + Random.nextFloat() * 1.5f),
)

@Preview(showBackground = true, device = "spec:width=800dp,height=1280dp,dpi=240,orientation=portrait")
@Composable
private fun ShooterScreenPortraitPreview() {
    MinigumTheme {
        ShooterScreen(navController = rememberNavController())
    }
}
