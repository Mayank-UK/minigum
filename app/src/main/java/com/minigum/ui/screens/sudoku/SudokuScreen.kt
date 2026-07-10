package com.minigum.ui.screens.sudoku

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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
import com.minigum.ui.components.HowToPlayDialog
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes

@Composable
fun SudokuScreen(
    navController: NavController,
    viewModel: SudokuViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showHowTo by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    LockPortraitImmersive()

    val overlayOpen = showHowTo || showMenu || state.phase == SudokuViewModel.Phase.GENERATING || state.phase == SudokuViewModel.Phase.COMPLETE
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) viewModel.pauseGame() else viewModel.resumeGame()
    }

    BackHandler {
        when {
            showMenu -> showMenu = false
            showHowTo -> navController.navigateUp()
            state.phase == SudokuViewModel.Phase.COMPLETE -> navController.navigateUp()
            else -> { showMenu = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Sizes.md),
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Hud(state, onMenu = { showMenu = true })

            Board(state, onSelect = viewModel::selectCell)

            Keypad(state, onDigit = viewModel::enterDigit)
            ActionBar(state, viewModel)
        }

        if (state.phase == SudokuViewModel.Phase.GENERATING) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(Sizes.md))
                    Text("Generating puzzle…", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        if (showMenu) {
            MenuOverlay(
                onNewPuzzle = { showMenu = false; viewModel.startNewGame() },
                onHowToPlay = { showMenu = false; showHowTo = true },
                onExit = { navController.navigateUp() },
            )
        }

        if (state.phase == SudokuViewModel.Phase.COMPLETE) {
            CompleteOverlay(
                seconds = state.elapsedSeconds,
                best = state.bestSeconds,
                mistakes = state.mistakes,
                hintsUsed = state.hintsUsed,
                onNewGame = { viewModel.startNewGame() },
                onExit = { navController.navigateUp() },
            )
        }
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Sudoku",
            instructions = sudokuInstructions,
            onContinue = {
                showHowTo = false
                if (state.phase == SudokuViewModel.Phase.IDLE) viewModel.startNewGame()
            },
        )
    }
}

private val sudokuInstructions = listOf(
    "Fill every row, column, and 3×3 box with the digits 1–9, no repeats.",
    "Tap a cell to select it, then tap a digit on the keypad to place it. Tap the same digit again to erase.",
    "Cells that clash with a duplicate (row, column, or box) turn red — fix those first.",
    "Turn on Notes to jot small candidate digits in a cell; placing a final digit clears its notes.",
    "Use Erase, Undo, and Hint any time. Hints reveal one correct cell.",
    "When the grid is full and every cell is valid, you win — your time is saved.",
)

@Composable
private fun Hud(state: SudokuViewModel.State, onMenu: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(Sizes.lg))
                .padding(horizontal = Sizes.md, vertical = Sizes.sm),
            horizontalArrangement = Arrangement.spacedBy(Sizes.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(state.elapsedSeconds), color = Color.White, fontWeight = FontWeight.Bold)
            Text("✗ ${state.mistakes}", color = if (state.mistakes > 0) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.85f))
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun Board(state: SudokuViewModel.State, onSelect: (Int) -> Unit) {
    val selected = state.selected
    val selValue = cellValue(state, selected)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 9f
            val thin = cell * 0.03f
            val thick = cell * 0.10f
            val line = Color(0xFF9E9E9E)
            for (i in 0..9) {
                val w = if (i % 3 == 0) thick else thin
                drawLine(line, Offset(i * cell, 0f), Offset(i * cell, size.height), strokeWidth = w)
                drawLine(line, Offset(0f, i * cell), Offset(size.width, i * cell), strokeWidth = w)
            }
        }
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until 9) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until 9) {
                        val cell = row * 9 + col
                        Cell(
                            value = cellValue(state, cell),
                            isGiven = state.givens[cell] != 0,
                            isHint = cell in state.hints,
                            notes = notesFor(state.notes, cell),
                            isSelected = cell == selected,
                            isPeer = isPeer(cell, selected),
                            sameNumber = selValue != 0 && cellValue(state, cell) == selValue,
                            isConflict = cell in state.conflicts,
                            modifier = Modifier.weight(1f),
                            onSelect = { onSelect(cell) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    value: Int,
    isGiven: Boolean,
    isHint: Boolean,
    notes: Set<Int>,
    isSelected: Boolean,
    isPeer: Boolean,
    sameNumber: Boolean,
    isConflict: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        isConflict -> Color(0xFFEF9A9A)
        sameNumber -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isPeer -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(bg)
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        if (value != 0) {
            val color = when {
                isConflict -> Color(0xFFC62828)
                isGiven -> MaterialTheme.colorScheme.onBackground
                isHint -> Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.primary
            }
            Text("$value", color = color, fontSize = 22.sp, fontWeight = if (isGiven) FontWeight.Black else FontWeight.SemiBold)
        } else if (notes.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until 3) {
                            val d = r * 3 + c + 1
                            Text(if (d in notes) "$d" else "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Keypad(state: SudokuViewModel.State, onDigit: (Int) -> Unit) {
    val counts = remember(state.entries, state.givens) { digitCounts(state.entries, state.givens) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sizes.xs),
    ) {
        for (d in 1..9) {
            val remaining = 9 - counts[d - 1]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (remaining == 0) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(Sizes.sm),
                    )
                    .clickable { onDigit(d) }
                    .padding(vertical = Sizes.xs),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$d", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (remaining == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onBackground)
                    Text("$remaining", fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun ActionBar(state: SudokuViewModel.State, viewModel: SudokuViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
    ) {
        ActionButton("Undo", enabled = state.undoStack.isNotEmpty(), onClick = viewModel::undo, modifier = Modifier.weight(1f))
        ActionButton("Erase", enabled = true, onClick = viewModel::erase, modifier = Modifier.weight(1f))
        ActionButton(if (state.notesMode) "Notes ✓" else "Notes", enabled = true, onClick = viewModel::toggleNotesMode, modifier = Modifier.weight(1f), highlighted = state.notesMode)
        ActionButton("Hint", enabled = state.phase == SudokuViewModel.Phase.PLAYING, onClick = viewModel::hint, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        highlighted -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(Sizes.sm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Sizes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MenuOverlay(
    onNewPuzzle: () -> Unit,
    onHowToPlay: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(Sizes.lg).fillMaxWidth(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Button(onClick = onNewPuzzle, modifier = Modifier.fillMaxWidth()) { Text("New puzzle") }
            Button(onClick = onHowToPlay, modifier = Modifier.fillMaxWidth()) { Text("How to play") }
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Exit to home") }
        }
    }
}

@Composable
private fun CompleteOverlay(
    seconds: Long,
    best: Long?,
    mistakes: Int,
    hintsUsed: Int,
    onNewGame: () -> Unit,
    onExit: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1B5E20).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Sizes.xs),
        ) {
            Text("Solved! 🎉", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Sizes.sm))
            Text("Time: ${formatTime(seconds)}", color = Color.White, style = MaterialTheme.typography.titleLarge)
            best?.let { Text("Best: ${formatTime(it)}", color = Color.White.copy(alpha = 0.85f)) }
            Text("Mistakes: $mistakes · Hints: $hintsUsed", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Sizes.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Sizes.md)) {
                Button(onClick = onNewGame) { Text("New puzzle") }
                Button(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

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

// --- helpers ---

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun cellValue(state: SudokuViewModel.State, cell: Int): Int =
    state.entries[cell].let { if (it != 0) it else state.givens[cell] }

private fun isPeer(a: Int, b: Int): Boolean {
    if (a == b) return false
    val ra = a / 9; val ca = a % 9
    val rb = b / 9; val cb = b % 9
    if (ra == rb || ca == cb) return true
    return ra / 3 == rb / 3 && ca / 3 == cb / 3
}

private fun notesFor(notes: BooleanArray, cell: Int): Set<Int> {
    val set = mutableSetOf<Int>()
    for (d in 1..9) if (notes[cell * 9 + d - 1]) set.add(d)
    return set
}

private fun digitCounts(entries: IntArray, givens: IntArray): IntArray {
    val counts = IntArray(9)
    for (i in entries.indices) {
        val v = if (entries[i] != 0) entries[i] else givens[i]
        if (v in 1..9) counts[v - 1]++
    }
    return counts
}

@Preview(showBackground = true, device = "spec:width=800dp,height=1280dp,dpi=240,orientation=portrait")
@Composable
private fun SudokuScreenPortraitPreview() {
    MinigumTheme {
        SudokuScreen(navController = rememberNavController())
    }
}
