package com.minigum.ui.screens.chess

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
fun ChessScreen(
    navController: NavController,
    viewModel: ChessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }
    var showNewGame by remember { mutableStateOf(false) }

    LockLandscapeImmersive()

    // Pause the game (and stop AI) while any overlay is up — same idea as the other games.
    val overlayOpen = showHowTo || showPause || showNewGame ||
        state.phase == ChessViewModel.Phase.GAMEOVER || state.promotionPending != null
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) viewModel.pauseGame() else viewModel.resumeGame()
    }

    BackHandler {
        when {
            showPause -> showPause = false
            showNewGame -> { showNewGame = false }
            showHowTo -> navController.navigateUp()
            state.phase == ChessViewModel.Phase.GAMEOVER -> navController.navigateUp()
            else -> showPause = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Sizes.md),
            horizontalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            // Board area: the square board is centred within whatever space is left
            // after the side panel, sized to the shorter available dimension.
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                BoardArea(state, onSelect = viewModel::selectSquare)
            }

            SidePanel(
                state = state,
                onPause = { showPause = true },
                modifier = Modifier.width(SidePanelWidth),
            )
        }

        if (state.promotionPending != null) {
            PromotionPicker(state, onPick = viewModel::confirmPromotion)
        }

        state.turnBanner?.let { side -> TurnBanner(side) }

        if (state.phase == ChessViewModel.Phase.GAMEOVER) {
            GameOverOverlay(
                statusText = state.statusText ?: "Game over",
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
            onStart = { mode ->
                showNewGame = false
                viewModel.startNewGame(mode)
            },
        )
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Chess",
            instructions = chessInstructions,
            onContinue = {
                showHowTo = false
                if (state.phase == ChessViewModel.Phase.IDLE) viewModel.startNewGame()
            },
        )
    }
}

private val SidePanelWidth = 228.dp

private val chessInstructions = listOf(
    "White moves first. Tap one of your pieces to select it, then tap a highlighted square to move.",
    "Dots mark legal moves; a ring marks a capture. You cannot make a move that leaves your own king in check.",
    "Pawns move forward one square (two from their start rank) and capture diagonally. They promote on the back rank — pick a piece when asked.",
    "Knights move in an L, bishops diagonally, rooks orthogonally, the queen any direction, the king one square.",
    "Castle by moving the king two squares toward a rook (king/rook unmoved, path clear, not through check).",
    "Capture a pawn en passant when it double-steps beside yours. Checkmate the enemy king to win; stalemate or no material is a draw.",
    "Tap the pause button for Undo, Flip board, and New game, or to switch between pass-and-play and vs Computer.",
)

// --- board ---

@Composable
private fun BoardArea(state: ChessViewModel.State, onSelect: (Int) -> Unit) {
    BoxWithConstraints {
        val boardSize = minOf(maxWidth, maxHeight)
        val sqDp = boardSize / 8f

        // Animate a slide whenever the last move changes.
        val lastMove = state.lastMove
        val slide = remember { Animatable(1f) }
        LaunchedEffect(lastMove) {
            if (lastMove != null) {
                slide.snapTo(0f)
                slide.animateTo(1f, tween(ChessViewModel.MOVE_SLIDE_MS.toInt()))
            }
        }
        val animating = slide.value < 1f && lastMove != null

        // Squares whose piece is drawn as a sliding overlay instead.
        val hidden = remember(animating, lastMove) {
            if (!animating || lastMove == null) emptySet()
            else buildSet {
                add(lastMove.to)
                if (lastMove.isCastle) {
                    val r = ChessEngine.rank(lastMove.to)
                    val kingside = ChessEngine.file(lastMove.to) == 6
                    add(ChessEngine.square(r, if (kingside) 5 else 3)) // rook destination
                }
            }
        }

        Box(
            modifier = Modifier
                .size(boardSize)
                .clip(RoundedCornerShape(Sizes.xs))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(Sizes.xs)),
        ) {
            Board(state, hidden, onSelect)

            if (animating && lastMove != null) {
                state.position.board[lastMove.to]?.let { piece ->
                    SlidingPiece(piece, sqDp, lastMove.from, lastMove.to, slide.value, state.flipped)
                }
                if (lastMove.isCastle) {
                    val r = ChessEngine.rank(lastMove.to)
                    val kingside = ChessEngine.file(lastMove.to) == 6
                    val rookFrom = ChessEngine.square(r, if (kingside) 7 else 0)
                    val rookTo = ChessEngine.square(r, if (kingside) 5 else 3)
                    state.position.board[rookTo]?.let { rook ->
                        SlidingPiece(rook, sqDp, rookFrom, rookTo, slide.value, state.flipped)
                    }
                }
            }
        }
    }
}

/** Engine square index → on-screen (row, col) given the flip state. */
private fun displayIndex(sq: Int, flipped: Boolean): Pair<Int, Int> {
    val r = sq / 8
    val f = sq % 8
    return if (flipped) (r to 7 - f) else ((7 - r) to f)
}

@Composable
private fun SlidingPiece(
    piece: ChessEngine.Piece,
    sqDp: androidx.compose.ui.unit.Dp,
    from: Int,
    to: Int,
    progress: Float,
    flipped: Boolean,
) {
    val (fr, fc) = displayIndex(from, flipped)
    val (tr, tc) = displayIndex(to, flipped)
    val fromX = sqDp * (fc + 0.5f)
    val fromY = sqDp * (fr + 0.5f)
    val toX = sqDp * (tc + 0.5f)
    val toY = sqDp * (tr + 0.5f)
    val x = lerp(fromX, toX, progress) - sqDp / 2f
    val y = lerp(fromY, toY, progress) - sqDp / 2f
    Box(modifier = Modifier.size(sqDp).offset(x = x, y = y)) {
        PieceView(piece)
    }
}

@Composable
private fun Board(state: ChessViewModel.State, hidden: Set<Int>, onSelect: (Int) -> Unit) {
    val ranks = if (state.flipped) (0..7).toList() else (7 downTo 0).toList()
    val files = if (state.flipped) (7 downTo 0).toList() else (0..7).toList()
    val targets = state.legalTargets
    val captures = state.captureTargets
    val checkSq = remember(state.position, state.status) {
        if (state.status == ChessEngine.Status.CHECK || state.status == ChessEngine.Status.CHECKMATE) {
            ChessEngine.kingSquare(state.position, state.position.sideToMove)
        } else -1
    }
    val lastFrom = state.lastMove?.from
    val lastTo = state.lastMove?.to

    Column(Modifier.fillMaxSize()) {
        for ((rankIndex, rank) in ranks.withIndex()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for ((fileIndex, file) in files.withIndex()) {
                    val sq = ChessEngine.square(rank, file)
                    Square(
                        piece = state.position.board[sq],
                        hidePiece = sq in hidden,
                        isLight = (rank + file) % 2 != 0,
                        isSelected = sq == state.selected,
                        isLastMove = sq == lastFrom || sq == lastTo,
                        isCheck = sq == checkSq,
                        isLegalTarget = sq in targets,
                        isCaptureTarget = sq in captures,
                        fileLabel = if (rankIndex == ranks.lastIndex) ('a' + file) else null,
                        rankLabel = if (fileIndex == 0) (rank + 1).toString() else null,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(sq) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Square(
    piece: ChessEngine.Piece?,
    hidePiece: Boolean,
    isLight: Boolean,
    isSelected: Boolean,
    isLastMove: Boolean,
    isCheck: Boolean,
    isLegalTarget: Boolean,
    isCaptureTarget: Boolean,
    fileLabel: Char?,
    rankLabel: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val base = if (isLight) LightSquare else DarkSquare
    val bg = when {
        isSelected -> SelectedSquare
        isLastMove -> LastMoveSquare
        isCheck -> CheckSquare
        else -> base
    }
    val labelColor = if (isLight) DarkSquare else LightSquare
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        if (rankLabel != null) {
            Text(
                rankLabel,
                color = labelColor,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(1.dp),
            )
        }
        if (fileLabel != null) {
            Text(
                fileLabel.toString(),
                color = labelColor,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(1.dp),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLegalTarget && (piece == null || hidePiece)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(LegalDot),
                )
            }
            if (piece != null && !hidePiece) {
                if (isCaptureTarget) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(CircleShape)
                            .border(width = 3.dp, color = LegalRing, shape = CircleShape),
                    )
                }
                PieceView(piece)
            }
        }
    }
}

@Composable
private fun PieceView(piece: ChessEngine.Piece) {
    val main = if (piece.side == ChessEngine.Side.WHITE) WhitePiece else BlackPiece
    val outline = if (piece.side == ChessEngine.Side.WHITE) OutlineDark else OutlineLight
    // Outline drawn in four offsets behind the glyph, so the piece stays legible on both square colours.
    Box(contentAlignment = Alignment.Center) {
        for ((dx, dy) in outlineOffsets) {
            Text(
                piece.type.glyph.toString(),
                color = outline,
                fontSize = 30.sp,
                modifier = Modifier.offset(x = dx.dp, y = dy.dp),
            )
        }
        Text(piece.type.glyph.toString(), color = main, fontSize = 30.sp)
    }
}

private val outlineOffsets = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

// --- side panel ---

@Composable
private fun SidePanel(
    state: ChessViewModel.State,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWhite = state.position.sideToMove == ChessEngine.Side.WHITE
    val turnLabel = when {
        state.aiThinking -> "Computer thinking…"
        state.phase == ChessViewModel.Phase.GAMEOVER -> state.statusText ?: "Game over"
        isWhite -> "White to move"
        else -> "Black to move"
    }
    val showTurnDot = state.phase == ChessViewModel.Phase.PLAYING && !state.aiThinking
    val checkHint = if (state.status == ChessEngine.Status.CHECK) "Check!" else null

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Sizes.sm),
    ) {
        // Header: turn + mode, with a persistent pause button.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(Sizes.sm))
                .padding(horizontal = Sizes.sm, vertical = Sizes.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
        ) {
            if (showTurnDot) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isWhite) WhitePiece else BlackPiece)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    turnLabel,
                    color = if (checkHint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(modeLabel(state.mode), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            IconButton(onClick = onPause) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        CapturedRow(capturedByWhite = state.capturedByWhite, capturedByBlack = state.capturedByBlack)

        MoveHistory(state.history, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

private fun modeLabel(mode: ChessViewModel.Mode): String = when (mode) {
    ChessViewModel.Mode.PassPlay -> "2 players · pass-and-play"
    is ChessViewModel.Mode.VsComputer -> "vs Computer · ${mode.strength.name.lowercase().replaceFirstChar { it.uppercase() }}"
}

@Composable
private fun CapturedRow(capturedByWhite: List<ChessEngine.PieceType>, capturedByBlack: List<ChessEngine.PieceType>) {
    val diff = (capturedByWhite.sumOf { it.value } - capturedByBlack.sumOf { it.value }) / 100
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(Sizes.sm))
            .padding(Sizes.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapturedSide(label = "White", pieces = capturedByWhite, sideColor = ChessEngine.Side.WHITE, scoreDelta = if (diff > 0) diff else null)
        CapturedSide(label = "Black", pieces = capturedByBlack, sideColor = ChessEngine.Side.BLACK, scoreDelta = if (diff < 0) -diff else null)
    }
}

@Composable
private fun CapturedSide(
    label: String,
    pieces: List<ChessEngine.PieceType>,
    sideColor: ChessEngine.Side,
    scoreDelta: Int?,
) {
    val color = if (sideColor == ChessEngine.Side.WHITE) WhitePiece else BlackPiece
    Column(horizontalAlignment = Alignment.Start) {
        Text("$label${scoreDelta?.let { "  +$it" } ?: ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(
            pieces.sortedByDescending { it.value }.joinToString("") { it.glyph.toString() },
            color = color,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun MoveHistory(history: List<ChessViewModel.HistoryEntry>, modifier: Modifier = Modifier) {
    val rows = remember(history) {
        val out = mutableListOf(Triple(0, "", ""))
        var moveNo = 0
        var white: String? = null
        for (e in history) {
            val no = e.ply / 2 + 1
            if (e.side == ChessEngine.Side.WHITE) {
                moveNo = no
                white = e.text
            } else {
                out.add(Triple(if (white != null) moveNo else no, white ?: "", e.text))
                white = null
            }
        }
        if (white != null) out.add(Triple(moveNo, white!!, ""))
        out.drop(1) // remove the seed row
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(Sizes.sm))
            .padding(Sizes.xs),
    ) {
        Text("Moves", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(Sizes.xxs))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text("${row.first}.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(26.dp))
                    Text(row.second, color = WhitePiece, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(row.third, color = BlackPiece, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
            if (rows.isEmpty()) {
                item { Text("Moves will appear here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
            }
        }
    }
}

// --- overlays ---

@Composable
private fun TurnBanner(side: ChessEngine.Side) {
    val dot = if (side == ChessEngine.Side.WHITE) Color.White else Color(0xFF2B2B2B)
    val label = if (side == ChessEngine.Side.WHITE) "White" else "Black"
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)),
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
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(
                "$label to move",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PromotionPicker(state: ChessViewModel.State, onPick: (ChessEngine.PieceType) -> Unit) {
    val pending = state.promotionPending ?: return
    val mover = state.position.board[pending.from]
    val side = mover?.side ?: ChessEngine.Side.WHITE
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
            for (type in listOf(ChessEngine.PieceType.QUEEN, ChessEngine.PieceType.ROOK, ChessEngine.PieceType.BISHOP, ChessEngine.PieceType.KNIGHT)) {
                val color = if (side == ChessEngine.Side.WHITE) WhitePiece else BlackPiece
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.sm))
                        .clickable { onPick(type) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(type.glyph.toString(), color = color, fontSize = 34.sp)
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(statusText: String, onNewGame: () -> Unit, onExit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Text(statusText, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.md)) {
                Button(onClick = onNewGame) { Text("New game") }
                Button(onClick = onExit, colors = ButtonDefaults.buttonColors()) { Text("Exit") }
            }
        }
    }
}

@Composable
private fun NewGameDialog(
    onDismiss: () -> Unit,
    onStart: (ChessViewModel.Mode) -> Unit,
) {
    var vsComputer by remember { mutableStateOf(true) }
    var playWhite by remember { mutableStateOf(true) }
    var strength by remember { mutableStateOf(ChessAi.AiStrength.MEDIUM) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.md))
                .padding(Sizes.lg),
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Text("New game", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

            Text("Opponent", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                FilterChip(selected = !vsComputer, onClick = { vsComputer = false }, label = { Text("2 Players") })
                FilterChip(selected = vsComputer, onClick = { vsComputer = true }, label = { Text("Computer") })
            }

            if (vsComputer) {
                Text("You play", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                    FilterChip(selected = playWhite, onClick = { playWhite = true }, label = { Text("White") })
                    FilterChip(selected = !playWhite, onClick = { playWhite = false }, label = { Text("Black") })
                }
                Text("Difficulty", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                    ChessAi.AiStrength.values().forEach { s ->
                        FilterChip(selected = strength == s, onClick = { strength = s }, label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(Sizes.sm))
                Button(onClick = {
                    val mode = if (vsComputer) {
                        ChessViewModel.Mode.VsComputer(
                            human = if (playWhite) ChessEngine.Side.WHITE else ChessEngine.Side.BLACK,
                            strength = strength,
                        )
                    } else ChessViewModel.Mode.PassPlay
                    onStart(mode)
                }) { Text("Start") }
            }
        }
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

// --- colours (classic light/dark board) ---
private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val SelectedSquare = Color(0xFFECD232)
private val LastMoveSquare = Color(0xFFD6C26B)
private val CheckSquare = Color(0xFFEF5350)
private val LegalDot = Color(0x55000000)
private val LegalRing = Color(0x66000000)
private val WhitePiece = Color(0xFFFFFFFF)
private val BlackPiece = Color(0xFF2B2B2B)
private val OutlineDark = Color(0xCC000000)
private val OutlineLight = Color(0xCCFFFFFF)

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape")
@Composable
private fun ChessScreenLandscapePreview() {
    MinigumTheme {
        ChessScreen(navController = rememberNavController())
    }
}
