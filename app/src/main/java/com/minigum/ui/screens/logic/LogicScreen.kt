package com.minigum.ui.screens.logic

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.minigum.ui.screens.logic.LogicViewModel.Block
import com.minigum.ui.screens.logic.LogicViewModel.BlockType
import com.minigum.ui.screens.logic.LogicViewModel.Connection
import com.minigum.ui.screens.logic.LogicViewModel.GameState
import com.minigum.ui.screens.logic.LogicViewModel.Socket
import com.minigum.ui.screens.logic.LogicViewModel.WORLD

@Composable
fun LogicScreen(
    navController: NavController,
    viewModel: LogicViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showHowTo by remember { mutableStateOf(true) }
    var showPause by remember { mutableStateOf(false) }
    var editBlock by remember { mutableStateOf<Block?>(null) }
    var paletteExpanded by remember { mutableStateOf(false) }
    var hintVisible by remember { mutableStateOf(false) }

    LockLandscapeImmersive()

    BackHandler(enabled = !showHowTo) {
        when {
            showPause -> showPause = false
            editBlock != null -> editBlock = null
            state.pendingFrom != null -> viewModel.cancelWire()
            paletteExpanded -> paletteExpanded = false
            else -> showPause = true
        }
    }

    LaunchedEffect(state.pendingFrom) {
        if (state.pendingFrom != null) { hintVisible = true; delay(2000); hintVisible = false }
        else hintVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val vw = constraints.maxWidth.toFloat()
            val vh = constraints.maxHeight.toFloat()
            val density = LocalDensity.current.density
            val scale = remember(vw, vh) { min(vw / WORLD.w, vh / WORLD.h) }
            var pan by remember(state.problemIndex, vw, vh) {
                mutableStateOf(Offset((vw - WORLD.w * scale) / 2f, (vh - WORLD.h * scale) / 2f))
            }
            val wiring = state.pendingFrom

            fun worldToScreen(p: Offset): Offset = Offset(p.x * scale + pan.x, p.y * scale + pan.y)

            // Background: grid + arrows. Pan by dragging empty space; tap an arrow to delete it.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.problemIndex) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            pan += drag
                        }
                    }
                    .pointerInput(state.connections) {
                        detectTapGestures { tap ->
                            val hit = state.connections.firstOrNull { c ->
                                val from = state.blocks.firstOrNull { it.id == c.from } ?: return@firstOrNull false
                                val to = state.blocks.firstOrNull { it.id == c.to } ?: return@firstOrNull false
                                val a = worldToScreen(outputSocketWorld(from, c.socket, viewModel))
                                val b = worldToScreen(Offset(to.x, to.y + LogicViewModel.BLOCK_H / 2f))
                                distToSegment(tap, a, b) < 30f
                            }
                            if (hit != null) viewModel.deleteConnection(hit.id)
                        }
                    },
            ) {
                drawGrid(scale, pan)
                for (c in state.connections) {
                    val from = state.blocks.firstOrNull { it.id == c.from } ?: continue
                    val to = state.blocks.firstOrNull { it.id == c.to } ?: continue
                    val a = worldToScreen(outputSocketWorld(from, c.socket, viewModel))
                    val b = worldToScreen(Offset(to.x, to.y + LogicViewModel.BLOCK_H / 2f))
                    drawLine(
                        color = if (wiring?.first == c.from) Color(0xFF81C784) else Color(0xFF4FC3F7),
                        start = a, end = b,
                        strokeWidth = if (wiring?.first == c.from) 7f else 5f,
                    )
                }
                wiring?.let { (srcId, socket) ->
                    val src = state.blocks.firstOrNull { it.id == srcId } ?: return@let
                    drawLine(
                        color = Color(0xFFFFB74D),
                        start = worldToScreen(outputSocketWorld(src, socket, viewModel)),
                        end = Offset(size.width / 2f, size.height / 2f),
                        strokeWidth = 5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
                    )
                }
            }

            // Blocks.
            state.blocks.forEach { block ->
                val pos = worldToScreen(Offset(block.x, block.y))
                val wDp = (LogicViewModel.BLOCK_W * scale) / density
                val hDp = (LogicViewModel.BLOCK_H * scale) / density
                BlockView(
                    block = block,
                    selected = wiring?.first == block.id,
                    widthDp = wDp,
                    heightDp = hDp,
                    scale = scale,
                    offsetPx = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                    socketsOut = viewModel.socketsOut(block.type),
                    label = viewModel.blockLabel(block),
                    onTap = {
                        if (wiring != null) viewModel.completeWire(block.id)
                        else if (block.type != BlockType.START && block.type != BlockType.END) editBlock = block
                    },
                    onLongPress = { viewModel.deleteBlock(block.id) },
                    onMove = { dx, dy -> viewModel.moveBlock(block.id, dx / scale, dy / scale) },
                    onSocketTap = { socket -> viewModel.beginWire(block.id, socket) },
                )
            }

            // Header.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Sizes.sm)
                    .width(280.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(Sizes.lg))
                    .padding(Sizes.sm),
                verticalArrangement = Arrangement.spacedBy(Sizes.xxs),
            ) {
                Text("Puzzle ${state.problemIndex + 1}/${viewModel.problemCount} — ${viewModel.problem.title}", color = Color.White, fontWeight = FontWeight.Bold)
                Text(viewModel.problem.statement, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }

            // Top-right controls.
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(Sizes.xxs)) {
                IconButton(onClick = { viewModel.previousProblem() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                }
                IconButton(onClick = { viewModel.nextProblem() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                }
                IconButton(onClick = { showPause = true }) { Icon(Icons.Filled.Pause, contentDescription = "Pause") }
            }

            if (hintVisible) {
                Text(
                    "Tap a block's right socket, then tap the next block to connect them",
                    color = Color.White, fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Sizes.xxl * 3)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(Sizes.lg))
                        .padding(horizontal = Sizes.md, vertical = Sizes.sm),
                )
            }

            // Bottom: expandable block menu + check.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = Sizes.sm, vertical = Sizes.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Sizes.xs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Sizes.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { paletteExpanded = !paletteExpanded }, modifier = Modifier.weight(1f)) {
                        Icon(if (paletteExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(Sizes.xs))
                        Text(if (paletteExpanded) "Hide blocks" else "Add blocks")
                    }
                    Button(onClick = { viewModel.judge() }) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(Sizes.xs))
                        Text("Check")
                    }
                }
                if (paletteExpanded) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                        items(viewModel.problem.palette.size) { i ->
                            val type = viewModel.problem.palette[i]
                            OutlinedButton(onClick = {
                                // Place at the centre, staggered so new blocks don't fully overlap.
                                val placed = state.blocks.size
                                val off = (placed % 5) * 30f - 60f
                                viewModel.addBlockAt(type, WORLD.w / 2f + off, WORLD.h / 2f + off)
                                paletteExpanded = false
                            }) { Text(type.label) }
                        }
                    }
                }
            }

            if (state.judgeState != LogicViewModel.JudgeState.Idle) {
                VerdictDialog(
                    state = state.judgeState,
                    correct = state.judgeCorrect,
                    message = state.judgeMessage,
                    isLast = state.problemIndex >= viewModel.problemCount - 1,
                    onDismiss = viewModel::clearJudge,
                    onNext = { viewModel.clearJudge(); viewModel.nextProblem() },
                )
            }

            if (showPause) {
                GamePauseOverlay(
                    onResume = { showPause = false },
                    onHowToPlay = { showPause = false; showHowTo = true },
                    onExit = { navController.navigateUp() },
                    settings = {
                        Button(onClick = { viewModel.resetFlow(); showPause = false }) { Text("Reset flow") }
                    },
                )
            }
        }
    }

    editBlock?.let { block ->
        BlockEditDialog(
            block = block,
            variables = viewModel.problem.variables,
            onDismiss = { editBlock = null },
            onSave = { params -> viewModel.updateBlockParams(block.id, params); editBlock = null },
            onDelete = { viewModel.deleteBlock(block.id); editBlock = null },
        )
    }

    if (showHowTo) {
        HowToPlayDialog(
            title = "How to play — Logic",
            instructions = logicInstructions,
            onContinue = { showHowTo = false },
        )
    }
}

private val logicInstructions = listOf(
    "Read the problem at the top — you'll build a flowchart that solves it.",
    "Tap 'Add blocks' to open the menu, then tap a block (Read, Set, If, While, Output) to drop it on the canvas.",
    "Drag any block to move it. Drag empty space to scroll around.",
    "Tap a block to open it and set what it does (pick variables and operations from menus).",
    "Connect blocks: tap a block's right-side socket, then tap the block that should come next.",
    "Tap an arrow to delete it. Long-press a block to delete it.",
    "Tap Check — the AI reads your whole flow and tells you if it correctly solves the problem.",
)

// ---- Block view --------------------------------------------------------

@Composable
private fun BlockView(
    block: Block,
    selected: Boolean,
    widthDp: Float,
    heightDp: Float,
    scale: Float,
    offsetPx: IntOffset,
    socketsOut: List<Socket>,
    label: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onSocketTap: (Socket) -> Unit,
) {
    val isFixed = block.type == BlockType.START || block.type == BlockType.END
    val bodyColor = when (block.type) {
        BlockType.START -> Color(0xFF66BB6A)
        BlockType.END -> Color(0xFFEF5350)
        BlockType.IF, BlockType.WHILE -> Color(0xFFFFB74D)
        BlockType.READ, BlockType.OUTPUT -> Color(0xFF4FC3F7)
        BlockType.ASSIGN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (block.type == BlockType.ASSIGN) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF212121)

    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(width = widthDp.dp, height = heightDp.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color(0xFFFFB74D) else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(Sizes.sm),
                )
                .background(bodyColor, RoundedCornerShape(Sizes.sm))
                .then(if (isFixed) Modifier else Modifier.pointerInput(block.id, scale) {
                    detectDragGestures { change, drag -> change.consume(); onMove(drag.x, drag.y) }
                })
                .then(if (isFixed) Modifier else Modifier.pointerInput(block.id) {
                    detectTapGestures(onLongPress = { onLongPress() }, onTap = { onTap() })
                }),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = textColor, fontSize = (heightDp * 0.22f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }

        // Output sockets on the right edge.
        socketsOut.forEachIndexed { index, socket ->
            val n = socketsOut.size
            val fraction = (index + 1f) / (n + 1f)
            val yOffset = heightDp * (fraction - 0.5f)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 8.dp, y = yOffset.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(socket.label, color = Color.White, fontSize = 9.sp)
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(0xFF4FC3F7), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { onSocketTap(socket) },
                )
            }
        }
    }
}

// ---- Verdict dialog ----------------------------------------------------

@Composable
private fun VerdictDialog(
    state: LogicViewModel.JudgeState,
    correct: Boolean?,
    message: String?,
    isLast: Boolean,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.md))
                .padding(Sizes.lg),
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            when (state) {
                LogicViewModel.JudgeState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(Sizes.md))
                    Text("Checking your flow…")
                }
                LogicViewModel.JudgeState.Error -> {
                    Text("Couldn't check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(message ?: "Something went wrong.", color = MaterialTheme.colorScheme.error)
                }
                LogicViewModel.JudgeState.Done -> {
                    val ok = correct == true
                    Text(if (ok) "✓ Correct!" else "✗ Not quite", color = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(message ?: "", color = MaterialTheme.colorScheme.onSurface)
                }
                LogicViewModel.JudgeState.Idle -> {}
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                if (state == LogicViewModel.JudgeState.Done && correct == true && !isLast) {
                    Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Next problem") }
                }
            }
        }
    }
}

// ---- Block edit dialog (menu-driven parameters) -----------------------

@Composable
private fun BlockEditDialog(
    block: Block,
    variables: List<String>,
    onDismiss: () -> Unit,
    onSave: (LogicViewModel.Params) -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Sizes.md))
                .padding(Sizes.lg),
            verticalArrangement = Arrangement.spacedBy(Sizes.md),
        ) {
            Text("Edit ${block.type.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            when (val p = block.params) {
                is LogicViewModel.Params.Read -> {
                    var v by remember { mutableStateOf(p.variable) }
                    LabeledDropdown(label = "Into variable", selected = v, options = variables) { v = it }
                    SaveDeleteRow(onSave = { onSave(LogicViewModel.Params.Read(v)) }, onDelete = onDelete)
                }
                is LogicViewModel.Params.Output -> {
                    var op by remember { mutableStateOf(p.value) }
                    OperandPicker(label = "Output", operand = op, variables = variables) { op = it }
                    SaveDeleteRow(onSave = { onSave(LogicViewModel.Params.Output(op)) }, onDelete = onDelete)
                }
                is LogicViewModel.Params.Assign -> {
                    var target by remember { mutableStateOf(p.target) }
                    var left by remember { mutableStateOf(p.rhs.left) }
                    var op by remember { mutableStateOf(p.rhs.op) }
                    var right by remember { mutableStateOf(p.rhs.right ?: LogicViewModel.Operand(false, constant = 0)) }
                    LabeledDropdown(label = "Set variable", selected = target, options = variables) { target = it }
                    OperandPicker(label = "From", operand = left, variables = variables) { left = it }
                    LabeledDropdown(
                        label = "Operation",
                        selected = op?.symbol ?: "—",
                        options = listOf("—") + LogicViewModel.BinOp.entries.map { it.symbol },
                    ) { picked ->
                        op = LogicViewModel.BinOp.entries.firstOrNull { it.symbol == picked }
                    }
                    if (op != null) {
                        OperandPicker(label = "And", operand = right, variables = variables) { right = it }
                    }
                    SaveDeleteRow(
                        onSave = {
                            val rhs = if (op == null) LogicViewModel.Expr(left) else LogicViewModel.Expr(left, op, right)
                            onSave(LogicViewModel.Params.Assign(target, rhs))
                        },
                        onDelete = onDelete,
                    )
                }
                is LogicViewModel.Params.If, is LogicViewModel.Params.While -> {
                    val cond = (p as? LogicViewModel.Params.If)?.cond ?: (p as LogicViewModel.Params.While).cond
                    var left by remember { mutableStateOf(cond.left) }
                    var cmp by remember { mutableStateOf(cond.cmp) }
                    var right by remember { mutableStateOf(cond.right) }
                    OperandPicker(label = "If / while", operand = left, variables = variables) { left = it }
                    LabeledDropdown(
                        label = "Compare",
                        selected = cmp.symbol,
                        options = LogicViewModel.Compare.entries.map { it.symbol },
                    ) { picked -> cmp = LogicViewModel.Compare.entries.first { it.symbol == picked } }
                    OperandPicker(label = "With", operand = right, variables = variables) { right = it }
                    val buildCond = LogicViewModel.Condition(left, cmp, right)
                    val result: LogicViewModel.Params = if (p is LogicViewModel.Params.If) LogicViewModel.Params.If(buildCond) else LogicViewModel.Params.While(buildCond)
                    SaveDeleteRow(onSave = { onSave(result) }, onDelete = onDelete)
                }
                else -> {} // Start/End not editable
            }
        }
    }
}

@Composable
private fun SaveDeleteRow(onSave: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete block") }
        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
    }
}

/** Pick a variable or a typed-in constant. */
@Composable
private fun OperandPicker(
    label: String,
    operand: LogicViewModel.Operand,
    variables: List<String>,
    onChange: (LogicViewModel.Operand) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Sizes.xs)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sizes.sm)) {
            LabeledDropdown(
                label = "Type",
                selected = if (operand.isVariable) "Variable" else "Number",
                options = listOf("Variable", "Number"),
            ) { picked -> onChange(operand.copy(isVariable = picked == "Variable")) }
            if (operand.isVariable) {
                LabeledDropdown(label = "Name", selected = operand.variable, options = variables) { v ->
                    onChange(operand.copy(variable = v))
                }
            } else {
                OutlinedTextField(
                    value = operand.constant.toString(),
                    onValueChange = { s -> s.toLongOrNull()?.let { onChange(operand.copy(constant = it)) } },
                    label = { Text("Number") },
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                )
            }
        }
    }
}

@Composable
private fun LabeledDropdown(label: String, selected: String, options: List<String>, onPick: (String) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(selected) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onPick(opt); open = false })
                }
            }
        }
    }
}

// ---- Geometry helpers --------------------------------------------------

private fun outputSocketWorld(block: Block, socket: Socket, vm: LogicViewModel): Offset {
    val sockets = vm.socketsOut(block.type)
    val idx = sockets.indexOf(socket).coerceAtLeast(0)
    val n = sockets.size.coerceAtLeast(1)
    val fraction = (idx + 1f) / (n + 1f)
    return Offset(block.x + LogicViewModel.BLOCK_W, block.y + LogicViewModel.BLOCK_H * fraction)
}

private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return kotlin.math.hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return kotlin.math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(scale: Float, pan: Offset) {
    val step = 80f * scale
    if (step <= 0f) return
    var x = pan.x % step
    while (x < size.width) { drawLine(Color.White.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f); x += step }
    var y = pan.y % step
    while (y < size.height) { drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += step }
}

// ---- Lifecycle ---------------------------------------------------------

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
    while (ctx is android.content.ContextWrapper) { if (ctx is Activity) return ctx; ctx = ctx.baseContext }
    return null
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape")
@Composable
private fun LogicScreenPreview() {
    MinigumTheme { LogicScreen(navController = rememberNavController()) }
}
