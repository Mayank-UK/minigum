package com.minigum.ui.screens.chess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Holds chess game state and drives both human input and the AI opponent.
 * Structure mirrors [com.minigum.ui.screens.sudoku.SudokuViewModel]:
 * a single [State] in a [MutableStateFlow] with a [Phase] enum. Moves are
 * applied instantly to the board, but [animating] stays true for one slide
 * duration so the UI can animate the piece, after which a "X's turn" banner
 * ([turnBanner]) is shown briefly.
 */
@HiltViewModel
class ChessViewModel @Inject constructor() : ViewModel() {

    enum class Phase { IDLE, PLAYING, GAMEOVER }

    /** Who (and how) the human(s) play. */
    sealed class Mode {
        /** Two humans sharing the device. */
        object PassPlay : Mode()
        /** One human vs the computer. */
        data class VsComputer(val human: ChessEngine.Side, val strength: ChessAi.AiStrength) : Mode()
    }

    /** A side-panel move record. */
    data class HistoryEntry(val ply: Int, val side: ChessEngine.Side, val text: String, val captured: ChessEngine.PieceType?)

    data class State(
        val phase: Phase = Phase.IDLE,
        val mode: Mode = Mode.VsComputer(ChessEngine.Side.WHITE, ChessAi.AiStrength.MEDIUM),
        val position: ChessEngine.Position = ChessEngine.startingPosition(),
        val selected: Int? = null,
        val legalForSelected: List<ChessEngine.Move> = emptyList(),
        val lastMove: ChessEngine.Move? = null,
        val history: List<HistoryEntry> = emptyList(),
        val capturedByWhite: List<ChessEngine.PieceType> = emptyList(),
        val capturedByBlack: List<ChessEngine.PieceType> = emptyList(),
        val status: ChessEngine.Status = ChessEngine.Status.ONGOING,
        val statusText: String? = null,
        private val positionCounts: Map<String, Int> = emptyMap(),
        val flipped: Boolean = false,
        val aiThinking: Boolean = false,
        val promotionPending: ChessEngine.Move? = null,
        val paused: Boolean = false,
        /** True while the last move is sliding to its destination (input gated). */
        val animating: Boolean = false,
        /** When set, a "X's turn" banner is shown (input gated). */
        val turnBanner: ChessEngine.Side? = null,
        /** Total plies applied; used to invalidate stale AI results. */
        val ply: Int = 0,
    ) {
        /** Squares the currently-selected piece may move to. */
        val legalTargets: Set<Int> get() = legalForSelected.map { it.to }.toSet()
        /** Subset of [legalTargets] that capture a piece. */
        val captureTargets: Set<Int> get() = legalForSelected.filter { it.captured != null || it.isEnPassant }.map { it.to }.toSet()
        /** Threefold-repetition counts, exposed read-only. */
        val repetitionCounts: Map<String, Int> get() = positionCounts
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var aiJob: Job? = null
    private var bannerJob: Job? = null

    /** Start a fresh game with [mode] (defaults to vs Computer, medium). */
    fun startNewGame(
        mode: Mode = Mode.VsComputer(ChessEngine.Side.WHITE, ChessAi.AiStrength.MEDIUM),
    ) {
        aiJob?.cancel()
        bannerJob?.cancel()
        val position = ChessEngine.startingPosition()
        _state.update {
            State(
                phase = Phase.PLAYING,
                mode = mode,
                position = position,
                positionCounts = mapOf(position.key() to 1),
                flipped = mode is Mode.VsComputer && mode.human == ChessEngine.Side.BLACK,
                turnBanner = ChessEngine.Side.WHITE,
                ply = 0,
            )
        }
        bannerJob = viewModelScope.launch {
            delay(TURN_BANNER_MS)
            _state.update { if (it.turnBanner == ChessEngine.Side.WHITE) it.copy(turnBanner = null) else it }
            maybeMoveAi()
        }
    }

    fun pauseGame() {
        aiJob?.cancel()
        bannerJob?.cancel()
        _state.update {
            if (it.phase == Phase.PLAYING)
                it.copy(paused = true, aiThinking = false, animating = false, turnBanner = null)
            else it
        }
    }

    fun resumeGame() {
        _state.update { it.copy(paused = false) }
        maybeMoveAi()
    }

    fun toggleFlip() {
        _state.update { it.copy(flipped = !it.flipped) }
    }

    /** Human taps a square: select a piece, move it, or clear the selection. */
    fun selectSquare(idx: Int) {
        var played: ChessEngine.Move? = null
        _state.update { current ->
            if (!inputAllowed(current) || current.promotionPending != null) return@update current
            val legals = current.legalForSelected

            // Is this square a legal destination for the selected piece?
            val candidates = legals.filter { it.to == idx }
            if (candidates.isNotEmpty()) {
                val mover = current.position.board[current.selected ?: idx]
                if (mover?.type == ChessEngine.PieceType.PAWN && candidates.any { it.promotion != null }) {
                    return@update current.copy(promotionPending = ChessEngine.Move(current.selected!!, idx))
                }
                played = candidates.first()
                return@update applyMove(current, played!!).copy(animating = true)
            }

            // Otherwise treat the tap as a selection.
            val piece = current.position.board[idx]
            if (piece != null && piece.side == current.position.sideToMove) {
                current.copy(selected = idx, legalForSelected = ChessEngine.legalMovesFrom(current.position, idx))
            } else {
                current.copy(selected = null, legalForSelected = emptyList())
            }
        }
        if (played != null) viewModelScope.launch { announceTurn(); maybeMoveAi() }
    }

    /** Resolves a deferred promotion. */
    fun confirmPromotion(type: ChessEngine.PieceType) {
        var played: ChessEngine.Move? = null
        _state.update { current ->
            val pending = current.promotionPending ?: return@update current
            if (!inputAllowed(current)) return@update current.copy(promotionPending = null)
            val move = current.legalForSelected.firstOrNull {
                it.from == pending.from && it.to == pending.to && it.promotion == type
            } ?: ChessEngine.legalMovesFrom(current.position, pending.from)
                .firstOrNull { it.to == pending.to && it.promotion == type }
            if (move == null) return@update current.copy(promotionPending = null, selected = null, legalForSelected = emptyList())
            played = move
            applyMove(current.copy(promotionPending = null, selected = null, legalForSelected = emptyList()), move).copy(animating = true)
        }
        if (played != null) viewModelScope.launch { announceTurn(); maybeMoveAi() }
    }

    fun cancelPromotion() {
        _state.update { it.copy(promotionPending = null) }
    }

    override fun onCleared() {
        super.onCleared()
        aiJob?.cancel()
        bannerJob?.cancel()
    }

    // --- internals ---

    private fun inputAllowed(s: State): Boolean =
        s.phase == Phase.PLAYING && !s.paused && !s.aiThinking && !s.animating &&
            s.turnBanner == null && isHumanTurn(s)

    private fun isHumanTurn(s: State): Boolean = when (s.mode) {
        Mode.PassPlay -> true
        is Mode.VsComputer -> s.position.sideToMove == s.mode.human
    }

    /** Hold for the slide, then flash a turn banner for the side now to move. */
    private suspend fun announceTurn() {
        if (_state.value.phase == Phase.GAMEOVER) {
            _state.update { it.copy(animating = false, turnBanner = null) }
            return
        }
        delay(MOVE_SLIDE_MS) // let the piece finish sliding
        val side = _state.value.position.sideToMove
        _state.update { it.copy(animating = false, turnBanner = side) }
        delay(TURN_BANNER_MS)
        _state.update { if (it.turnBanner == side) it.copy(turnBanner = null) else it }
    }

    /** Apply a fully-resolved move to [current] and return the resulting state. */
    private fun applyMove(current: State, move: ChessEngine.Move): State {
        val before = current.position
        val mover = before.board[move.from]!!
        val next = ChessEngine.makeMove(before, move)

        val counts = HashMap(current.repetitionCounts)
        counts[next.key()] = (counts[next.key()] ?: 0) + 1

        val engineStatus = ChessEngine.status(next)
        val isRepetition = counts.getValue(next.key()) >= 3 &&
            engineStatus != ChessEngine.Status.CHECKMATE &&
            engineStatus != ChessEngine.Status.STALEMATE

        val phase = if (engineStatus == ChessEngine.Status.CHECKMATE ||
            engineStatus == ChessEngine.Status.STALEMATE ||
            engineStatus == ChessEngine.Status.DRAW_INSUFFICIENT ||
            engineStatus == ChessEngine.Status.DRAW_FIFTY_MOVE ||
            isRepetition
        ) Phase.GAMEOVER else Phase.PLAYING

        val statusText = statusText(engineStatus, isRepetition, next.sideToMove)

        val capturedByWhite = if (move.captured != null && mover.side == ChessEngine.Side.WHITE) current.capturedByWhite + move.captured else current.capturedByWhite
        val capturedByBlack = if (move.captured != null && mover.side == ChessEngine.Side.BLACK) current.capturedByBlack + move.captured else current.capturedByBlack

        return current.copy(
            position = next,
            phase = phase,
            selected = null,
            legalForSelected = emptyList(),
            lastMove = move,
            history = current.history + HistoryEntry(current.ply, mover.side, moveText(move, mover), move.captured),
            capturedByWhite = capturedByWhite,
            capturedByBlack = capturedByBlack,
            status = engineStatus,
            statusText = statusText,
            positionCounts = counts,
            ply = current.ply + 1,
            promotionPending = null,
        )
    }

    /** If it is the computer's turn, compute and play its move off the UI thread. */
    private fun maybeMoveAi() {
        val s = _state.value
        if (s.phase != Phase.PLAYING || s.paused) return
        val mode = s.mode as? Mode.VsComputer ?: return
        if (s.position.sideToMove == mode.human) return // human's turn
        if (s.aiThinking) return
        // Don't move while a piece is sliding or the turn banner is up — the
        // proper caller re-triggers us once those clear.
        if (s.animating || s.turnBanner != null) return
        val expectedPly = s.ply
        val strength = mode.strength

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _state.update { it.copy(aiThinking = true) }
            delay(AI_THINK_DELAY_MS)
            val posSnapshot = _state.value.position
            val move = withContext(Dispatchers.Default) { ChessAi.bestMove(posSnapshot, strength) }
            val after = _state.value
            if (after.ply != expectedPly || after.position != posSnapshot) {
                _state.update { it.copy(aiThinking = false) }
                return@launch
            }
            _state.update { applyMove(it, move).copy(aiThinking = false, animating = true) }
            announceTurn()
        }
    }

    companion object {
        /** Slide duration for a moving piece (matched by the UI's animation). */
        const val MOVE_SLIDE_MS = 220L
        /** How long the "X's turn" banner is shown. */
        const val TURN_BANNER_MS = 1000L
        /** Visible pause before the AI moves, so its reply is easy to follow. */
        private const val AI_THINK_DELAY_MS = 650L
    }

    private fun moveText(move: ChessEngine.Move, mover: ChessEngine.Piece): String {
        val sym = if (mover.type == ChessEngine.PieceType.PAWN) "" else mover.type.glyph.toString()
        val sep = if (move.captured != null) "×" else "–"
        val promo = move.promotion?.let { "=${it.glyph}" } ?: ""
        val castle = when {
            move.isCastle && ChessEngine.file(move.to) == 6 -> "O-O"
            move.isCastle && ChessEngine.file(move.to) == 2 -> "O-O-O"
            else -> "$sym${ChessEngine.squareName(move.from)}$sep${ChessEngine.squareName(move.to)}$promo"
        }
        return castle
    }

    private fun statusText(status: ChessEngine.Status, repetition: Boolean, sideToMove: ChessEngine.Side): String? = when (status) {
        ChessEngine.Status.CHECKMATE -> "Checkmate — ${sideToMove.opponent().label()} wins"
        ChessEngine.Status.STALEMATE -> "Stalemate — draw"
        ChessEngine.Status.DRAW_INSUFFICIENT -> "Draw — insufficient material"
        ChessEngine.Status.DRAW_FIFTY_MOVE -> "Draw — 50-move rule"
        ChessEngine.Status.CHECK -> "Check!"
        ChessEngine.Status.ONGOING -> if (repetition) "Draw — threefold repetition" else null
    }

    private fun ChessEngine.Side.label(): String = if (this == ChessEngine.Side.WHITE) "White" else "Black"
    private fun ChessEngine.Side.opponent(): ChessEngine.Side = if (this == ChessEngine.Side.WHITE) ChessEngine.Side.BLACK else ChessEngine.Side.WHITE
}
