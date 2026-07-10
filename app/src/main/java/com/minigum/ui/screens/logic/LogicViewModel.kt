package com.minigum.ui.screens.logic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Logic — a flowchart-building puzzle game.
 *
 * The player drags control-flow blocks (Start, Read, Assign, If, While, Output, End) onto a
 * canvas, connects them with arrows in the order the algorithm should run, and sets each
 * block's action from menus. There is no interpreter: when the player taps Check, the whole
 * flow is serialized to text and an AI tutor (free Gemini API) reads it and judges whether it
 * correctly solves the problem statement.
 */
@HiltViewModel
class LogicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ---- Domain model -----------------------------------------------------

    /** Control-flow block kinds. START/END are fixed per problem. */
    enum class BlockType(val label: String) {
        START("Start"), READ("Read"), ASSIGN("Set"), IF("If"), WHILE("While"), OUTPUT("Output"), END("End")
    }

    /** One value reference: a named variable or a numeric constant. */
    data class Operand(val isVariable: Boolean, val variable: String = "", val constant: Long = 0) {
        fun render(): String = if (isVariable) variable else constant.toString()
    }

    enum class BinOp(val symbol: String) { PLUS("+"), MINUS("-"), MUL("×"), DIV("/") }

    enum class Compare(val symbol: String) { LT("<"), LE("≤"), EQ("="), GE("≥"), GT(">") }

    /** A right-hand-side expression: a single operand, or two operands joined by an operator. */
    data class Expr(val left: Operand, val op: BinOp? = null, val right: Operand? = null) {
        fun render(): String = if (op == null || right == null) left.render() else "${left.render()} ${op.symbol} ${right.render()}"
    }

    data class Condition(val left: Operand, val cmp: Compare, val right: Operand) {
        fun render(): String = "${left.render()} ${cmp.symbol} ${right.render()}"
    }

    /** The structured action stored in a block. */
    sealed class Params {
        object Start : Params()
        data class Read(val variable: String) : Params()
        data class Assign(val target: String, val rhs: Expr) : Params()
        data class If(val cond: Condition) : Params()
        data class While(val cond: Condition) : Params()
        data class Output(val value: Operand) : Params()
        object End : Params()
    }

    data class Block(
        val id: String,
        val type: BlockType,
        val x: Float,
        val y: Float,
        val params: Params,
    )

    /** An outgoing socket on a block. */
    enum class Socket(val label: String) { NEXT("next"), YES("yes"), NO("no"), BODY("body"), EXIT("exit") }

    /** An arrow from a block's [socket] to another block (each block has a single entry). */
    data class Connection(val id: String, val from: String, val socket: Socket, val to: String)

    data class Problem(
        val id: Int,
        val title: String,
        val statement: String,                 // the problem given to the player (and AI)
        val inputVar: String,                  // the variable holding the input
        val outputDescription: String,         // what the Output block should produce
        val variables: List<String>,           // variables the player may use
        val palette: List<BlockType>,
        val maxBlocks: Int = 16,
    )

    enum class JudgeState { Idle, Loading, Done, Error }

    data class GameState(
        val blocks: List<Block> = emptyList(),
        val connections: List<Connection> = emptyList(),
        val problemIndex: Int = 0,
        val pendingFrom: Pair<String, Socket>? = null, // selected source socket, for wiring
        val judgeState: JudgeState = JudgeState.Idle,
        val judgeCorrect: Boolean? = null,
        val judgeMessage: String? = null,
    )

    private val _state = MutableStateFlow(loadInitial())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val problems: List<Problem> = buildProblems()
    private var idCounter = 0
    private fun newId(prefix: String): String = "${prefix}_${idCounter++}"

    val problem: Problem get() = problems[_state.value.problemIndex.coerceIn(problems.indices)]
    val problemCount: Int get() = problems.size

    // ---- Block actions ----------------------------------------------------

    /** Output sockets a block exposes, in display order. */
    fun socketsOut(type: BlockType): List<Socket> = when (type) {
        BlockType.START, BlockType.READ, BlockType.ASSIGN, BlockType.OUTPUT -> listOf(Socket.NEXT)
        BlockType.IF -> listOf(Socket.YES, Socket.NO)
        BlockType.WHILE -> listOf(Socket.BODY, Socket.EXIT)
        BlockType.END -> emptyList()
    }

    /** Drop a [type] block at [wx],[wy] with default parameters. */
    fun addBlockAt(type: BlockType, wx: Float, wy: Float) {
        _state.update { current ->
            if (current.blocks.count { it.type != BlockType.START && it.type != BlockType.END } >= problem.maxBlocks) return@update current
            val params = when (type) {
                BlockType.READ -> Params.Read(problem.variables.first())
                BlockType.ASSIGN -> Params.Assign(problem.variables.first(), Expr(Operand(true, problem.variables.first())))
                BlockType.IF -> Params.If(Condition(Operand(true, problem.variables.first()), Compare.LE, Operand(false, constant = 0)))
                BlockType.WHILE -> Params.While(Condition(Operand(true, problem.variables.first()), Compare.LE, Operand(false, constant = 0)))
                BlockType.OUTPUT -> Params.Output(Operand(true, problem.variables.first()))
                else -> return@update current // START/END are fixed, not addable
            }
            val block = Block(newId("b"), type, x = wx, y = wy, params = params)
            current.copy(blocks = current.blocks + block)
        }
    }

    fun moveBlock(id: String, dx: Float, dy: Float) {
        _state.update { current ->
            current.copy(
                blocks = current.blocks.map { if (it.id == id) it.copy(x = (it.x + dx).coerceIn(0f, WORLD.w), y = (it.y + dy).coerceIn(0f, WORLD.h)) else it },
            )
        }
    }

    fun updateBlockParams(id: String, params: Params) {
        _state.update { current ->
            current.copy(blocks = current.blocks.map { if (it.id == id) it.copy(params = params) else it })
        }
    }

    fun deleteBlock(id: String) {
        _state.update { current ->
            val block = current.blocks.firstOrNull { it.id == id } ?: return@update current
            if (block.type == BlockType.START || block.type == BlockType.END) return@update current
            current.copy(
                blocks = current.blocks.filterNot { it.id == id },
                connections = current.connections.filterNot { it.from == id || it.to == id },
                pendingFrom = if (current.pendingFrom?.first == id) null else current.pendingFrom,
            )
        }
    }

    // ---- Wiring -----------------------------------------------------------

    fun beginWire(fromBlock: String, socket: Socket) {
        _state.update { current ->
            val key = fromBlock to socket
            current.copy(pendingFrom = if (current.pendingFrom == key) null else key)
        }
    }

    fun cancelWire() {
        _state.update { it.copy(pendingFrom = null) }
    }

    /** Complete an arrow into [toBlock] from the pending source socket. */
    fun completeWire(toBlock: String) {
        _state.update { current ->
            val (from, socket) = current.pendingFrom ?: return@update current
            if (toBlock == from) return@update current.copy(pendingFrom = null)
            if (toBlock == BlockIds.START) return@update current.copy(pendingFrom = null) // START has no entry
            // Each block has a single entry: drop any existing arrow into the target.
            val replaced = current.connections.filterNot { it.to == toBlock }
            val conn = Connection(newId("c"), from, socket, toBlock)
            current.copy(connections = replaced + conn, pendingFrom = null)
        }
    }

    fun deleteConnection(id: String) {
        _state.update { it.copy(connections = it.connections.filterNot { c -> c.id == id }) }
    }

    fun resetFlow() {
        _state.value = buildStateFor(problem, _state.value.problemIndex)
    }

    // ---- AI judge ---------------------------------------------------------

    fun judge() {
        val current = _state.value
        val p = problem
        _state.update { it.copy(judgeState = JudgeState.Loading, judgeCorrect = null, judgeMessage = null) }
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    AiChecker.judge(AiChecker.BUNDLED_KEY, p, current.blocks, current.connections)
                }
                val (correct, message) = parseVerdict(raw)
                _state.update { it.copy(judgeState = JudgeState.Done, judgeCorrect = correct, judgeMessage = message) }
            } catch (t: Throwable) {
                _state.update { it.copy(judgeState = JudgeState.Error, judgeCorrect = null, judgeMessage = t.message ?: "AI request failed") }
            }
        }
    }

    fun clearJudge() {
        _state.update { it.copy(judgeState = JudgeState.Idle, judgeCorrect = null, judgeMessage = null) }
    }

    private fun parseVerdict(raw: String): Pair<Boolean?, String> {
        val first = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val upper = first.uppercase().replace(":", "").trim()
        val correct = when {
            upper.startsWith("CORRECT") -> true
            upper.startsWith("INCORRECT") -> false
            else -> null
        }
        // Keep the explanation (everything), stripping the leading verdict word for tidiness.
        val message = raw.trim()
        return correct to message
    }

    fun loadProblem(index: Int) {
        _state.value = buildStateFor(problems[index.coerceIn(problems.indices)], index)
    }

    fun nextProblem() = loadProblem((_state.value.problemIndex + 1).coerceIn(problems.indices))
    fun previousProblem() = loadProblem((_state.value.problemIndex - 1).coerceIn(problems.indices))

    // ---- Serialization for the AI ----------------------------------------

    /** Human-readable label for a block, e.g. "If: i ≤ N". */
    fun blockLabel(block: Block): String = when (val p = block.params) {
        is Params.Start -> "Start"
        is Params.End -> "End"
        is Params.Read -> "Read ${p.variable}"
        is Params.Assign -> "Set ${p.target} = ${p.rhs.render()}"
        is Params.If -> "If ${p.cond.render()}"
        is Params.While -> "While ${p.cond.render()}"
        is Params.Output -> "Output ${p.value.render()}"
    }

    // ---- Setup ------------------------------------------------------------

    private fun loadInitial(): GameState = buildStateFor(buildProblems().first(), 0)

    private fun buildStateFor(p: Problem, index: Int): GameState {
        val start = Block(BlockIds.START, BlockType.START, x = WORLD.pad, y = WORLD.cy, params = Params.Start)
        val end = Block(BlockIds.END, BlockType.END, x = WORLD.w - WORLD.pad, y = WORLD.cy, params = Params.End)
        return GameState(blocks = listOf(start, end), connections = emptyList(), problemIndex = index)
    }

    private fun buildProblems(): List<Problem> = listOf(
        Problem(
            id = 1,
            title = "Factorial",
            statement = "Given an input N, compute N factorial (1×2×…×N). Output the result.",
            inputVar = "N",
            outputDescription = "N factorial",
            variables = listOf("N", "result", "i"),
            palette = listOf(BlockType.READ, BlockType.ASSIGN, BlockType.WHILE, BlockType.IF, BlockType.OUTPUT),
        ),
        Problem(
            id = 2,
            title = "Sum 1 to N",
            statement = "Given an input N, compute the sum of all integers from 1 to N. Output the result.",
            inputVar = "N",
            outputDescription = "1 + 2 + ... + N",
            variables = listOf("N", "sum", "i"),
            palette = listOf(BlockType.READ, BlockType.ASSIGN, BlockType.WHILE, BlockType.IF, BlockType.OUTPUT),
        ),
        Problem(
            id = 3,
            title = "Maximum of two",
            statement = "Given two inputs A and B, output the larger of the two.",
            inputVar = "A",
            outputDescription = "the larger of A and B",
            variables = listOf("A", "B"),
            palette = listOf(BlockType.READ, BlockType.ASSIGN, BlockType.IF, BlockType.OUTPUT),
        ),
        Problem(
            id = 4,
            title = "Even or odd",
            statement = "Given an input N, output 1 if N is even, and 0 if N is odd.",
            inputVar = "N",
            outputDescription = "1 if N is even, else 0",
            variables = listOf("N"),
            palette = listOf(BlockType.READ, BlockType.ASSIGN, BlockType.IF, BlockType.WHILE, BlockType.OUTPUT),
        ),
        Problem(
            id = 5,
            title = "Absolute value",
            statement = "Given an input N (which may be negative), output its absolute value (N if N ≥ 0, otherwise −N).",
            inputVar = "N",
            outputDescription = "absolute value of N",
            variables = listOf("N"),
            palette = listOf(BlockType.READ, BlockType.ASSIGN, BlockType.IF, BlockType.OUTPUT),
        ),
    )

    object WORLD {
        const val w = 1400f
        const val h = 1000f
        const val cy = h / 2f
        const val pad = 120f
    }

    /** Fixed block ids. */
    object BlockIds {
        const val START = "start"
        const val END = "end"
    }

    companion object {
        const val BLOCK_W = 150f
        const val BLOCK_H = 76f
    }
}
