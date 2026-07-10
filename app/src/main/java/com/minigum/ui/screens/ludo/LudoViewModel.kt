package com.minigum.ui.screens.ludo

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
import javax.inject.Inject
import kotlin.random.Random

/**
 * Drives a Ludo game: an animated dice roll, step-by-step token movement,
 * turn order, extra turns (6 / capture), the three-sixes forfeit, no-move
 * passes, and bot seats. Structure mirrors [ChessViewModel]: one [State] in a
 * [MutableStateFlow]; bots run on [Dispatchers.Default] with visible delays so
 * their rolls and moves are easy to follow.
 */
@HiltViewModel
class LudoViewModel @Inject constructor() : ViewModel() {

    enum class Seat { HUMAN, BOT }

    data class State(
        val game: LudoEngine.GameState = LudoEngine.initialState(),
        /** One seat per colour, in [LudoEngine.Player] order. */
        val seats: List<Seat> = listOf(Seat.HUMAN, Seat.BOT, Seat.BOT, Seat.BOT),
        val legalMoves: List<LudoEngine.Move> = emptyList(),
        val lastMove: LudoEngine.Move? = null,
        val rolling: Boolean = false,
        val animating: Boolean = false,
        val botThinking: Boolean = false,
        val message: String? = null,
        val paused: Boolean = false,
        val flipped: Boolean = false,
        /** When set, a "X's turn" banner is shown and play is paused for [TURN_BANNER_MS]. */
        val turnBanner: LudoEngine.Player? = null,
    ) {
        val phase: LudoEngine.Phase get() = game.phase
        val die: Int? get() = game.die
        val turn: LudoEngine.Player get() = game.turn
        val selectableTokens: Set<LudoEngine.Token> get() = legalMoves.map { it.token }.toSet()
        val isBotTurn: Boolean get() = seats[game.turn.ordinal] == Seat.BOT
        val busy: Boolean get() = rolling || animating || botThinking || turnBanner != null
        val canRoll: Boolean get() = phase == LudoEngine.Phase.ROLL && !isBotTurn && !busy
        val canSelect: Boolean get() = phase == LudoEngine.Phase.MOVE && !isBotTurn && !busy
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var botJob: Job? = null
    private var bannerJob: Job? = null

    fun startNewGame(seats: List<Seat>) {
        botJob?.cancel()
        bannerJob?.cancel()
        _state.update { State(game = LudoEngine.initialState(), seats = seats) }
        beginTurn(LudoEngine.Player.RED)
        maybeDriveBots()
    }

    /** Show a "X's turn" banner and gate play for [TURN_BANNER_MS]. */
    private fun beginTurn(player: LudoEngine.Player) {
        bannerJob?.cancel()
        _state.update { it.copy(turnBanner = player) }
        bannerJob = viewModelScope.launch {
            delay(TURN_BANNER_MS)
            _state.update { if (it.turnBanner == player) it.copy(turnBanner = null) else it }
        }
    }

    fun toggleFlip() {
        _state.update { it.copy(flipped = !it.flipped) }
    }

    /** Human taps the Roll button → animate the dice, then resolve the roll. */
    fun rollDice() {
        if (!_state.value.canRoll) return
        viewModelScope.launch {
            doRoll()
            maybeDriveBots()
        }
    }

    /** Human taps one of their movable tokens → animate it walking to its target. */
    fun selectToken(token: LudoEngine.Token) {
        val s = _state.value
        if (!s.canSelect) return
        val move = s.legalMoves.firstOrNull { it.token == token } ?: return
        viewModelScope.launch {
            animateAndApply(move)
            maybeDriveBots()
        }
    }

    fun pauseGame() {
        botJob?.cancel()
        _state.update {
            if (it.phase != LudoEngine.Phase.GAMEOVER)
                it.copy(paused = true, botThinking = false)
            else it
        }
    }

    fun resumeGame() {
        _state.update { it.copy(paused = false) }
        maybeDriveBots()
    }

    override fun onCleared() {
        super.onCleared()
        botJob?.cancel()
    }

    // --- dice ---

    /** Cycles random faces, then settles on a real roll and resolves it. */
    private suspend fun doRoll() {
        val real = Random.nextInt(1, 7)
        val prevTurn = _state.value.turn
        _state.update { it.copy(rolling = true, message = null) }
        repeat(ROLL_ANIM_TICKS) {
            _state.update { s -> s.copy(game = s.game.copy(die = Random.nextInt(1, 7))) }
            delay(ROLL_ANIM_DELAY)
        }
        commitRoll(real)
        _state.update { it.copy(rolling = false) }

        // If the roll forfeited/passed the turn, announce the next player.
        val afterRoll = _state.value
        if (afterRoll.phase != LudoEngine.Phase.GAMEOVER && afterRoll.turn != prevTurn) beginTurn(afterRoll.turn)

        // For a human, auto-play when only one move is possible.
        val s = _state.value
        if (s.phase == LudoEngine.Phase.MOVE && s.legalMoves.size == 1 && !s.isBotTurn && !s.paused) {
            animateAndApply(s.legalMoves.first())
        }
    }

    /** Resolve a settled die value: three-sixes forfeit, no-move pass, or await a move. */
    private fun commitRoll(die: Int) {
        _state.update { current ->
            val cs = if (die == 6) current.game.consecutiveSixes + 1 else 0

            // Three sixes in a row forfeit the turn. Keep the die shown so the
            // player can see what was rolled before the turn passes.
            if (die == 6 && cs == 3) {
                val g = current.game.copy(
                    turn = current.game.turn.next,
                    die = die,
                    consecutiveSixes = 0,
                    phase = LudoEngine.Phase.ROLL,
                )
                return@update current.copy(game = g, legalMoves = emptyList(), message = "Three 6s — turn passed")
            }

            val rolled = current.game.copy(die = die, consecutiveSixes = cs)
            val legal = LudoEngine.legalMoves(rolled, die)

            if (legal.isEmpty()) {
                val g = rolled.copy(turn = rolled.turn.next, die = die, consecutiveSixes = 0, phase = LudoEngine.Phase.ROLL)
                return@update current.copy(game = g, legalMoves = emptyList(), message = "No moves — turn passed")
            }

            current.copy(game = rolled.copy(phase = LudoEngine.Phase.MOVE), legalMoves = legal, message = null)
        }
    }

    // --- moving ---

    /** Walk the token one cell at a time to its destination, then resolve captures + turn. */
    private suspend fun animateAndApply(move: LudoEngine.Move) {
        _state.update { it.copy(animating = true) }

        val start = _state.value.game.spots[move.token] ?: move.from
        val die = _state.value.game.die ?: 1
        val fromAdvance = LudoEngine.advance(start)

        if (start is LudoEngine.Spot.Base) {
            delay(STEP_DELAY * 2) // a brief hop out of the yard
        } else {
            for (step in 1..die) {
                val intermediate = LudoEngine.spotForAdvance(fromAdvance + step)
                delay(STEP_DELAY)
                _state.update { cur ->
                    cur.copy(game = cur.game.copy(spots = cur.game.spots + (move.token to intermediate)))
                }
            }
        }

        val prevTurn = _state.value.turn
        _state.update { current -> commitApplyMove(current, move) }
        _state.update { it.copy(animating = false) }

        // If the move passed the turn to another player, announce them.
        val afterMove = _state.value
        if (afterMove.phase != LudoEngine.Phase.GAMEOVER && afterMove.turn != prevTurn) beginTurn(afterMove.turn)
    }

    /** Apply a finished move: captures, win, extra-turn vs. turn-pass. Pure (no side effects). */
    private fun commitApplyMove(current: State, move: LudoEngine.Move): State {
        val movedGame = LudoEngine.applyMove(current.game, move)
        val mover = move.token.owner
        val dieWasSix = current.game.die == 6
        val captured = move.captures.isNotEmpty()

        val newGame = when {
            LudoEngine.hasWon(movedGame, mover) ->
                movedGame.copy(phase = LudoEngine.Phase.GAMEOVER, winners = movedGame.winners + mover, die = null)
            dieWasSix || captured ->
                movedGame.copy(phase = LudoEngine.Phase.ROLL) // extra turn; keep die shown
            else ->
                // Turn passes; keep the die shown until the next player rolls.
                movedGame.copy(turn = movedGame.turn.next, consecutiveSixes = 0, phase = LudoEngine.Phase.ROLL)
        }

        val msg = when {
            newGame.phase == LudoEngine.Phase.GAMEOVER -> null
            captured && dieWasSix -> "Captured! And a 6 — roll again"
            captured -> "Captured! Roll again"
            dieWasSix -> "Rolled a 6 — roll again"
            else -> null
        }
        return current.copy(game = newGame, legalMoves = emptyList(), lastMove = move, message = msg)
    }

    // --- bots ---

    /** If it's a bot's turn, roll and move (animated, with pauses) until a human must act or the game ends. */
    private fun maybeDriveBots() {
        val s = _state.value
        if (s.paused || s.phase == LudoEngine.Phase.GAMEOVER) return
        if (!s.isBotTurn) return

        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (true) {
                val cur = _state.value
                if (cur.paused || cur.phase == LudoEngine.Phase.GAMEOVER || !cur.isBotTurn) break
                if (cur.turnBanner != null) { delay(120); continue } // wait for the turn banner
                when (cur.phase) {
                    LudoEngine.Phase.ROLL -> {
                        _state.update { it.copy(botThinking = true) }
                        doRoll()
                        _state.update { it.copy(botThinking = false) }
                    }
                    LudoEngine.Phase.MOVE -> {
                        _state.update { it.copy(botThinking = true) }
                        delay(BOT_MOVE_DELAY)
                        val cur2 = _state.value
                        val move = LudoBot.chooseMove(cur2.game, cur2.legalMoves)
                        animateAndApply(move)
                        _state.update { it.copy(botThinking = false) }
                    }
                    LudoEngine.Phase.GAMEOVER -> break
                }
            }
        }
    }

    companion object {
        private const val ROLL_ANIM_TICKS = 9
        private const val ROLL_ANIM_DELAY = 55L
        private const val STEP_DELAY = 90L
        private const val BOT_MOVE_DELAY = 350L
        const val TURN_BANNER_MS = 2000L
    }
}
