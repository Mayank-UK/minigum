package com.minigum.ui.screens.ludo

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun LudoScreen(
    navController: NavController,
    viewModel: LudoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }
    var showNewGame by remember { mutableStateOf(false) }

    LockLandscapeImmersive()

    val overlayOpen = showHowTo || showPause || showNewGame || state.phase == LudoEngine.Phase.GAMEOVER
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) viewModel.pauseGame() else viewModel.resumeGame()
    }

    BackHandler {
        when {
            showPause -> showPause = false
            showNewGame -> showNewGame = false
            showHowTo -> navController.navigateUp()
            state.phase == LudoEngine.Phase.GAMEOVER -> navController.navigateUp()
            else -> showPause = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Sizes.md),
            horizontalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                BoxWithConstraints {
                    val boardSize = minOf(maxWidth, maxHeight)
                    Board(
                        state = state,
                        boardSize = boardSize,
                        onSelectToken = viewModel::selectToken,
                        modifier = Modifier
                            .size(boardSize)
                            .clip(RoundedCornerShape(Sizes.sm))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(Sizes.sm)),
                    )
                }
            }

            SidePanel(
                state = state,
                onRoll = viewModel::rollDice,
                onPause = { showPause = true },
                modifier = Modifier.width(SidePanelWidth),
            )
        }

        state.turnBanner?.let { player -> TurnBanner(player) }

        if (state.phase == LudoEngine.Phase.GAMEOVER) {
            GameOverOverlay(
                winner = state.game.winners.lastOrNull(),
                seats = state.seats,
                onNewGame = { showNewGame = true },
                onExit = { navController.navigateUp() },
            )
        }

        if (showPause) {
            GamePauseOverlay(
                onResume = { showPause = false },
                onHowToPlay = { showPause = false; showHowTo = true },
                onExit = { navController.navigateUp() },
                settings = {
                    Column(
                        modifier = Modifier.width(280.dp),
                        verticalArrangement = Arrangement.spacedBy(Sizes.md),
                    ) {
                        Button(onClick = viewModel::toggleFlip, modifier = Modifier.fillMaxWidth()) { Text("Flip board") }
                        Button(onClick = { showPause = false; showNewGame = true }, modifier = Modifier.fillMaxWidth()) { Text("New game") }
                    }
                },
            )
        }
    }

    if (showNewGame) {
        NewGameDialog(
            onDismiss = { showNewGame = false },
            onStart = { seats -> showNewGame = false; viewModel.startNewGame(seats) },
        )
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Ludo",
            instructions = ludoInstructions,
            onContinue = {
                showHowTo = false
                // On first entry (no moves yet) pick seats via the New game dialog.
                val fresh = state.lastMove == null && state.game.die == null &&
                    state.game.spots.values.all { it is LudoEngine.Spot.Base }
                if (fresh) showNewGame = true
            },
        )
    }
}

private val SidePanelWidth = 232.dp

private val ludoInstructions = listOf(
    "Each colour has 4 tokens in its yard. Red moves first, then Green, Yellow, Blue.",
    "Tap Roll. You need a 6 to bring a token out of its yard onto its start square.",
    "Move one of your tokens forward by the die value along the track toward your home column.",
    "Land exactly on the final home square to finish a token — a roll that overshoots can't be used for that token.",
    "Landing on a lone opponent (not on a starred safe square) sends it back to its yard.",
    "Rolling a 6 (or capturing) earns another roll. Roll three 6s in a row, though, and you lose the turn.",
    "First colour to bring all four tokens home wins. Tap pause for Flip board, New game, or to change seats.",
)

// --- board geometry (15×15 grid; rendering-only, verified via @Preview) ---

private typealias Cell = Pair<Int, Int> // (row, col)

private val MAIN_TRACK: List<Cell> = listOf(
    6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
    5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6,
    0 to 7,
    0 to 8, 1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8,
    6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
    7 to 14,
    8 to 14, 8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9,
    9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8,
    14 to 7,
    14 to 6, 13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
    8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0,
    7 to 0, 6 to 0,
)

// Home columns step 0..5 (step 5 sits beside the centre) per colour.
private val HOME_COLUMNS: Map<LudoEngine.Player, List<Cell>> = mapOf(
    LudoEngine.Player.RED to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 6),
    LudoEngine.Player.GREEN to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7, 6 to 7),
    LudoEngine.Player.YELLOW to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9, 7 to 8),
    LudoEngine.Player.BLUE to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7, 8 to 7),
)

// Yard origins (top-left cell of each 6×6 yard).
private val YARD_ORIGIN: Map<LudoEngine.Player, Cell> = mapOf(
    LudoEngine.Player.RED to (0 to 0),
    LudoEngine.Player.GREEN to (0 to 9),
    LudoEngine.Player.YELLOW to (9 to 9),
    LudoEngine.Player.BLUE to (9 to 0),
)

private val BASE_OFFSETS = listOf(1 to 1, 1 to 4, 4 to 1, 4 to 4) // within a 6×6 yard

private fun cellOf(owner: LudoEngine.Player, spot: LudoEngine.Spot, tokenId: Int): Cell? = when (spot) {
    LudoEngine.Spot.Base -> {
        val (r, c) = YARD_ORIGIN.getValue(owner)
        val (dr, dc) = BASE_OFFSETS[tokenId]
        (r + dr) to (c + dc)
    }
    is LudoEngine.Spot.Track -> {
        val idx = (owner.startCell + spot.progress) % 52
        MAIN_TRACK[idx]
    }
    is LudoEngine.Spot.Home -> HOME_COLUMNS.getValue(owner)[spot.step.coerceIn(0, 5)]
}

// --- colours ---

private fun LudoEngine.Player.color(): Color = when (this) {
    LudoEngine.Player.RED -> Color(0xFFE53935)
    LudoEngine.Player.GREEN -> Color(0xFF43A047)
    LudoEngine.Player.YELLOW -> Color(0xFFFBC02D)
    LudoEngine.Player.BLUE -> Color(0xFF1E88E5)
}

private val TrackLight = Color(0xFFFFFFFF)
private val TrackDark = Color(0xFFCFD8DC)
private val BoardBg = Color(0xFFECEFF1)

// --- board drawing ---

@Composable
private fun Board(
    state: LudoViewModel.State,
    boardSize: androidx.compose.ui.unit.Dp,
    onSelectToken: (LudoEngine.Token) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cellPx = with(density) { boardSize.toPx() } / 15f
    val flipped = state.flipped

    // Pulsing highlight for the tokens the current player may move.
    val transition = rememberInfiniteTransition(label = "select-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )

    val selectable = state.selectableTokens
    Canvas(
        modifier = modifier
            .background(BoardBg)
            .pointerInput(selectable, flipped, cellPx) {
                detectTapGestures { offset ->
                    val col = (offset.x / cellPx).toInt().coerceIn(0, 14)
                    val row = (offset.y / cellPx).toInt().coerceIn(0, 14)
                    val (r, c) = if (flipped) (14 - row) to (14 - col) else row to col
                    // Find a selectable token sitting on the tapped cell.
                    val hit = state.game.spots.entries.firstOrNull { (token, spot) ->
                        token in selectable && cellOf(token.owner, spot, token.id) == (r to c)
                    }?.key
                    if (hit != null) onSelectToken(hit)
                }
            },
    ) {
        val cell = size.minDimension / 15f
        val tokenGroups = tokenCellGroups(state)

        drawRotate(degrees = if (flipped) 180f else 0f, pivot = center) {
            drawYards(cell)
            drawTrack(cell)
            drawHomeColumns(cell)
            drawCentre(cell)
            drawTokens(cell, tokenGroups, state, pulse)
        }
    }
}

private fun DrawScope.drawYards(cell: Float) {
    for (player in LudoEngine.Player.values()) {
        val (r, c) = YARD_ORIGIN.getValue(player)
        val x = c * cell
        val y = r * cell
        val inset = cell * 0.12f
        drawRoundRect(
            color = player.color().copy(alpha = 0.85f),
            topLeft = Offset(x + inset, y + inset),
            size = Size(cell * 6 - inset * 2, cell * 6 - inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.5f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(x + cell * 1.0f, y + cell * 1.0f),
            size = Size(cell * 4, cell * 4),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.3f),
        )
        // base slot rings
        for ((dr, dc) in BASE_OFFSETS) {
            val cx = (c + dc + 0.5f) * cell
            val cy = (r + dr + 0.5f) * cell
            drawCircle(player.color().copy(alpha = 0.25f), cell * 0.42f, Offset(cx, cy))
        }
    }
}

private fun DrawScope.drawTrack(cell: Float) {
    for ((idx, rc) in MAIN_TRACK.withIndex()) {
        val (r, c) = rc
        val x = c * cell
        val y = r * cell
        val color = when {
            idx in setOf(0, 13, 26, 39) -> startOwner(idx)?.color() ?: TrackLight // start squares tinted
            else -> if ((r + c) % 2 == 0) TrackLight else TrackDark
        }
        drawRect(color, Offset(x, y), Size(cell, cell))
        if (idx in LudoEngine.SAFE_CELLS) {
            // safe marker
            drawCircle(Color(0xFF607D8B).copy(alpha = 0.5f), cell * 0.16f, Offset(x + cell / 2, y + cell / 2))
        }
    }
}

private fun startOwner(idx: Int): LudoEngine.Player? = LudoEngine.Player.values().firstOrNull { it.startCell == idx }

private fun DrawScope.drawHomeColumns(cell: Float) {
    for (player in LudoEngine.Player.values()) {
        for ((step, rc) in HOME_COLUMNS.getValue(player).withIndex()) {
            val (r, c) = rc
            val alpha = 0.45f + 0.1f * step
            drawRect(player.color().copy(alpha = alpha), Offset(c * cell, r * cell), Size(cell, cell))
        }
    }
}

private fun DrawScope.drawCentre(cell: Float) {
    // Four coloured triangles meeting at the very centre cell (7,7).
    val cx = 7.5f * cell
    val cy = 7.5f * cell
    val arms = listOf(
        LudoEngine.Player.RED to listOf(Offset(6 * cell, 7 * cell), Offset(9 * cell, 7 * cell), Offset(cx, cy)),
        LudoEngine.Player.GREEN to listOf(Offset(7 * cell, 6 * cell), Offset(7 * cell, 9 * cell), Offset(cx, cy)),
        LudoEngine.Player.YELLOW to listOf(Offset(6 * cell, 8 * cell), Offset(9 * cell, 8 * cell), Offset(cx, cy)),
        LudoEngine.Player.BLUE to listOf(Offset(8 * cell, 6 * cell), Offset(8 * cell, 9 * cell), Offset(cx, cy)),
    )
    for ((player, pts) in arms) {
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y); lineTo(pts[1].x, pts[1].y); lineTo(pts[2].x, pts[2].y); close()
        }
        drawPath(path, player.color())
    }
}

private fun tokenCellGroups(state: LudoViewModel.State): Map<Cell, List<LudoEngine.Token>> {
    val groups = mutableMapOf<Cell, MutableList<LudoEngine.Token>>()
    for ((token, spot) in state.game.spots) {
        val cell = cellOf(token.owner, spot, token.id) ?: continue
        groups.getOrPut(cell) { mutableListOf() }.add(token)
    }
    return groups
}

private fun DrawScope.drawTokens(
    cell: Float,
    groups: Map<Cell, List<LudoEngine.Token>>,
    state: LudoViewModel.State,
    pulse: Float,
) {
    for ((cellCoord, tokens) in groups) {
        val (r, c) = cellCoord
        val count = tokens.size
        for ((i, token) in tokens.withIndex()) {
            val (dr, dc) = stackOffset(i, count)
            val cx = (c + 0.5f + dc) * cell
            val cy = (r + 0.5f + dr) * cell
            val radius = if (count > 1) cell * 0.26f else cell * 0.36f
            val color = token.owner.color()
            val isSelectable = token in state.selectableTokens
            val isLastMoved = state.lastMove?.token == token
            if (isSelectable) {
                // Pulsing white glow so tappable tokens are obvious.
                drawCircle(Color.White.copy(alpha = 0.9f), radius + cell * (0.10f + 0.06f * pulse), Offset(cx, cy))
                drawCircle(color, radius + cell * 0.04f, Offset(cx, cy))
            } else {
                if (isLastMoved) drawCircle(Color(0xFFECD232), radius + cell * 0.06f, Offset(cx, cy))
                drawCircle(color, radius, Offset(cx, cy))
            }
            drawCircle(Color(0x66000000), radius, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.06f))
        }
    }
}

/** Position offset (in fractions of a cell) for the i-th token among [count] sharing a cell. */
private fun stackOffset(i: Int, count: Int): Pair<Float, Float> = when (count) {
    1 -> 0f to 0f
    2 -> (if (i == 0) -0.18f else 0.18f) to 0f
    else -> { // 3 or 4 → 2×2 grid
        val row = i / 2
        val col = i % 2
        (if (row == 0) -0.18f else 0.18f) to (if (col == 0) -0.18f else 0.18f)
    }
}

// --- side panel ---

@Composable
private fun SidePanel(
    state: LudoViewModel.State,
    onRoll: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val turn = state.turn
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Sizes.sm),
    ) {
        // Header: whose turn + pause.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(Sizes.sm))
                .padding(horizontal = Sizes.sm, vertical = Sizes.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(turn.color())
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("${turn.name.lowercase().replaceFirstChar { it.uppercase() }}'s turn", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(if (state.isBotTurn) "Computer" else "You", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            IconButton(onClick = onPause) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Dice + roll control.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(Sizes.sm))
                .padding(Sizes.md),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                DieFace(value = state.die, rolling = state.rolling)
                when {
                    state.botThinking -> Text("Computer thinking…", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    state.canRoll -> Button(onClick = onRoll) { Text("Roll") }
                    state.phase == LudoEngine.Phase.MOVE && !state.isBotTurn -> Text(
                        "Tap a glowing token",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    else -> Spacer(Modifier.height(20.dp))
                }
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }

        Standings(state, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun DieFace(value: Int?, rolling: Boolean = false) {
    val fg = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val transition = rememberInfiniteTransition(label = "dice")
    val wobble by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(90), repeatMode = RepeatMode.Reverse),
        label = "wobble",
    )
    Column(
        modifier = Modifier
            .size(56.dp)
            .rotate(if (rolling) wobble else 0f)
            .clip(RoundedCornerShape(Sizes.sm))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Sizes.sm))
            .padding(Sizes.xs),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (value == null) {
            Text("?", color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        } else {
            val on = pipSet(value)
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..2) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if ((row to col) in on) fg else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

/** Which of the 3×3 pip positions are filled for a die value. */
private fun pipSet(v: Int): Set<Pair<Int, Int>> = when (v) {
    1 -> setOf(1 to 1)
    2 -> setOf(0 to 0, 2 to 2)
    3 -> setOf(0 to 0, 1 to 1, 2 to 2)
    4 -> setOf(0 to 0, 0 to 2, 2 to 0, 2 to 2)
    5 -> setOf(0 to 0, 0 to 2, 1 to 1, 2 to 0, 2 to 2)
    6 -> setOf(0 to 0, 0 to 2, 1 to 0, 1 to 2, 2 to 0, 2 to 2)
    else -> emptySet()
}

@Composable
private fun Standings(state: LudoViewModel.State, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(Sizes.sm))
            .padding(Sizes.xs),
        verticalArrangement = Arrangement.spacedBy(Sizes.xs),
    ) {
        Text("Standings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        for (player in LudoEngine.Player.values()) {
            val home = LudoEngine.finishedCount(state.game, player)
            val isTurn = player == state.turn && state.phase != LudoEngine.Phase.GAMEOVER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isTurn) player.color().copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(Sizes.xs))
                    .padding(horizontal = Sizes.xs, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
            ) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(player.color()))
                Text(player.name.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("$home/4", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(if (state.seats[player.ordinal] == LudoViewModel.Seat.HUMAN) "You" else "CPU", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}

// --- overlays ---

@Composable
private fun TurnBanner(player: LudoEngine.Player) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.lg))
                .padding(horizontal = Sizes.lg, vertical = Sizes.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(player.color())
                    .border(2.dp, Color.White, CircleShape),
            )
            Text(
                "${player.name.lowercase().replaceFirstChar { it.uppercase() }}'s turn",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GameOverOverlay(
    winner: LudoEngine.Player?,
    seats: List<LudoViewModel.Seat>,
    onNewGame: () -> Unit,
    onExit: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sizes.md)) {
            Text("Game over!", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (winner != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(winner.color()))
                    Text("${winner.name.lowercase().replaceFirstChar { it.uppercase() }} wins", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.md)) {
                Button(onClick = onNewGame) { Text("New game") }
                Button(onClick = onExit, colors = ButtonDefaults.buttonColors()) { Text("Exit") }
            }
        }
    }
}

@Composable
private fun NewGameDialog(onDismiss: () -> Unit, onStart: (List<LudoViewModel.Seat>) -> Unit) {
    var color by remember { mutableStateOf(LudoEngine.Player.RED) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.md))
                .padding(Sizes.lg),
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Text("New game", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("Pick your colour", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.md)) {
                for (player in LudoEngine.Player.values()) {
                    ColorChoice(
                        color = player.color(),
                        selected = color == player,
                        onClick = { color = player },
                    )
                }
            }
            Text(
                "You play ${color.name.lowercase().replaceFirstChar { it.uppercase() }}; the other three colours are computer-controlled.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(Sizes.sm))
                Button(onClick = {
                    val seats = LudoEngine.Player.values().map { if (it == color) LudoViewModel.Seat.HUMAN else LudoViewModel.Seat.BOT }
                    onStart(seats)
                }) { Text("Start") }
            }
        }
    }
}

@Composable
private fun ColorChoice(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(width = if (selected) 3.dp else 1.dp, color = if (selected) Color.White else Color.Black.copy(alpha = 0.2f), shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

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

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape")
@Composable
private fun LudoScreenLandscapePreview() {
    MinigumTheme {
        LudoScreen(navController = rememberNavController())
    }
}
