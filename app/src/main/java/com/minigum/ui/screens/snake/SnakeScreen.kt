package com.minigum.ui.screens.snake

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.minigum.ui.components.HowToPlayDialog
import com.minigum.ui.navigation.leaderboardRoute
import com.minigum.ui.screens.leaderboard.InlineLeaderboard
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SnakeScreen(
    navController: NavController,
    viewModel: SnakeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // UI-only state.
    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }
    var dpadVisible by remember { mutableStateOf(false) }

    // Entering the game: lock to landscape and go immersive full-screen.
    // On exit, restore the previous orientation and system bars.
    LockLandscapeImmersive()

    // While any overlay is up, the game must stay paused.
    val overlayOpen = showHowTo || showPause || state.isGameOver
    LaunchedEffect(overlayOpen) {
        if (overlayOpen && state.isRunning) viewModel.pause()
    }

    // Back press opens the pause menu (or resumes from pause / exits when over).
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
        // Full-screen rectangular playfield with square cells.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val grid = rememberGrid(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            LaunchedEffect(grid.cols, grid.rows) {
                viewModel.configure(grid.cols, grid.rows)
            }
            Board(
                state = state,
                modifier = Modifier.fillMaxSize(),
                onDirection = viewModel::changeDirection,
            )
        }

        // Only the score is shown over the board during play.
        ScorePill(
            score = state.score,
            best = state.bestScore,
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

        // Optional D-pad, overlaid on the right. Toggled from the pause menu.
        if (dpadVisible) {
            DPad(
                onDirection = viewModel::changeDirection,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(Sizes.md),
            )
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
                onLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.SNAKE)) },
                onExit = { navController.navigateUp() },
                settings = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
                    ) {
                        Text("Show D-pad", color = Color.White)
                        Switch(checked = dpadVisible, onCheckedChange = { dpadVisible = it })
                    }
                },
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
                inlineLeaderboard = { InlineLeaderboard(LeaderboardGame.SNAKE, state.score) },
                onFullLeaderboard = { navController.navigate(leaderboardRoute(LeaderboardGame.SNAKE)) },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Snake",
            instructions = snakeInstructions,
            onContinue = {
                showHowTo = false
                viewModel.start()
            },
        )
    }
}

private val snakeInstructions = listOf(
    "Swipe anywhere on the board to steer the snake.",
    "Prefer buttons? Enable the on-screen D-pad from the pause menu.",
    "Eat the red food to grow longer and increase your score.",
    "The snake speeds up as your score climbs.",
    "Don't hit the walls or your own tail — that ends the game.",
    "Tap the ✕ (or press Back) to pause, change settings, or exit.",
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

private data class GridDims(val cols: Int, val rows: Int)

/**
 * Choose square-cell grid dimensions to fill the screen as a rectangle.
 * The shorter side always holds [CELLS_PER_SHORT_SIDE] cells; the longer side
 * gets as many cells as fit, so cells stay square while the grid fills the area.
 */
@Composable
private fun rememberGrid(widthPx: Float, heightPx: Float): GridDims {
    return remember(widthPx, heightPx) {
        val landscape = widthPx >= heightPx
        val shortSide = minOf(widthPx, heightPx)
        val longSide = maxOf(widthPx, heightPx)
        val cellSize = shortSide / CELLS_PER_SHORT_SIDE
        val longCells = (longSide / cellSize).roundToInt().coerceAtLeast(CELLS_PER_SHORT_SIDE)
        if (landscape) GridDims(longCells, CELLS_PER_SHORT_SIDE) else GridDims(CELLS_PER_SHORT_SIDE, longCells)
    }
}

private const val CELLS_PER_SHORT_SIDE = 16

@Composable
private fun ScorePill(score: Int, best: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(Sizes.lg))
            .padding(horizontal = Sizes.md, vertical = Sizes.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
    ) {
        Text("Score $score", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Best $best", color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun Board(
    state: SnakeViewModel.GameState,
    modifier: Modifier = Modifier,
    onDirection: (SnakeViewModel.Direction) -> Unit,
) {
    val foodColor = Color(0xFFFF5252)
    val snakeHeadColor = MaterialTheme.colorScheme.primary
    val snakeBodyColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    var startX by remember { mutableFloatStateOf(0f) }
    var startY by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        startY = offset.y
                    },
                    onDrag = { change, _ ->
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        val threshold = 30f
                        if (abs(dx) > threshold || abs(dy) > threshold) {
                            if (abs(dx) > abs(dy)) {
                                onDirection(if (dx > 0) SnakeViewModel.Direction.RIGHT else SnakeViewModel.Direction.LEFT)
                            } else {
                                onDirection(if (dy > 0) SnakeViewModel.Direction.DOWN else SnakeViewModel.Direction.UP)
                            }
                            startX = change.position.x
                            startY = change.position.y
                        }
                    },
                )
            },
    ) {
        val cell = minOf(size.width / state.cols, size.height / state.rows)
        val originX = (size.width - state.cols * cell) / 2f
        val originY = (size.height - state.rows * cell) / 2f
        drawGrid(gridColor, state.cols, state.rows, cell, originX, originY)
        drawCell(state.food, foodColor, cell, originX, originY)
        state.snake.forEachIndexed { index, c ->
            drawCell(c, if (index == 0) snakeHeadColor else snakeBodyColor, cell, originX, originY)
        }
    }
}

private fun DrawScope.drawGrid(
    color: Color,
    cols: Int,
    rows: Int,
    cell: Float,
    originX: Float,
    originY: Float,
) {
    val gridWidth = cols * cell
    val gridHeight = rows * cell
    for (i in 1 until cols) {
        val x = originX + i * cell
        drawLine(color, Offset(x, originY), Offset(x, originY + gridHeight), strokeWidth = 1f)
    }
    for (j in 1 until rows) {
        val y = originY + j * cell
        drawLine(color, Offset(originX, y), Offset(originX + gridWidth, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawCell(
    cell: SnakeViewModel.Point,
    color: Color,
    size: Float,
    originX: Float,
    originY: Float,
) {
    val inset = size * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(originX + cell.x * size + inset, originY + cell.y * size + inset),
        size = Size(size - inset * 2, size - inset * 2),
        cornerRadius = CornerRadius(size * 0.2f),
    )
}

@Composable
private fun DPad(
    onDirection: (SnakeViewModel.Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(Sizes.lg))
            .padding(Sizes.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DPadButton(Icons.Filled.KeyboardArrowUp, "Up") { onDirection(SnakeViewModel.Direction.UP) }
        Row {
            DPadButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left") { onDirection(SnakeViewModel.Direction.LEFT) }
            Spacer(Modifier.size(40.dp))
            DPadButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right") { onDirection(SnakeViewModel.Direction.RIGHT) }
        }
        DPadButton(Icons.Filled.KeyboardArrowDown, "Down") { onDirection(SnakeViewModel.Direction.DOWN) }
    }
}

@Composable
private fun DPadButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape")
@Composable
private fun SnakeScreenLandscapePreview() {
    MinigumTheme {
        SnakeScreen(navController = rememberNavController())
    }
}
