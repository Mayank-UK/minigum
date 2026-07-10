package com.minigum.ui.screens.ludo

/**
 * Pure-Kotlin Ludo rules engine. No Android/Compose dependencies, so it is
 * unit-testable and reusable by [LudoBot]. Mirrors the role [ChessEngine]
 * plays for the chess game.
 *
 * Board model:
 *  - 4 players (Red, Green, Yellow, Blue). Each player's start cell on the
 *    52-cell main track is `ordinal * 13` (Red=0, Green=13, Yellow=26, Blue=39).
 *  - A token's location is a [Spot]: [Spot.Base] (in its yard), [Spot.Track]
 *    (on the main track, progress 0..50 from its start cell), or [Spot.Home]
 *    (its private home column, step 0..5; step 5 == finished).
 *  - A token's "advance" (0..56) merges track + home into one number so move
 *    legality is a simple bounds check; 56 == finished.
 */
object LudoEngine {

    enum class Player(val startCell: Int) {
        RED(0), GREEN(13), YELLOW(26), BLUE(39);
        val next: Player get() = values()[(ordinal + 1) % values().size]
    }

    sealed class Spot {
        object Base : Spot()
        /** progress 0..50 on the main track. */
        data class Track(val progress: Int) : Spot()
        /** step 0..5 in the home column; step 5 == finished. */
        data class Home(val step: Int) : Spot()
    }

    data class Token(val owner: Player, val id: Int) // id 0..3

    data class Move(
        val token: Token,
        val from: Spot,
        val to: Spot,
        val captures: List<Token>,
        val exitsBase: Boolean,
    ) {
        val scores: Boolean get() = to is Spot.Home && to.step == 5
    }

    enum class Phase { ROLL, MOVE, GAMEOVER }

    data class GameState(
        val spots: Map<Token, Spot>,
        val turn: Player,
        val die: Int? = null,
        val consecutiveSixes: Int = 0,
        val winners: List<Player> = emptyList(),
        val phase: Phase = Phase.ROLL,
    )

    /** Safe cells (4 start cells + 4 star cells). No capture happens here. */
    val SAFE_CELLS: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    private const val TOKENS_PER_PLAYER = 4
    private const val TRACK_LEN = 51 // progress 0..50 inclusive
    private const val HOME_FINISH = 56 // advance value for a finished token

    fun initialState(): GameState {
        val spots = LinkedHashMap<Token, Spot>()
        for (player in Player.values()) {
            for (id in 0 until TOKENS_PER_PLAYER) {
                spots[Token(player, id)] = Spot.Base
            }
        }
        return GameState(spots = spots, turn = Player.RED)
    }

    // --- coordinate-free queries (drive both logic and rendering) ---

    fun advance(spot: Spot): Int = when (spot) {
        Spot.Base -> -1
        is Spot.Track -> spot.progress
        is Spot.Home -> TRACK_LEN + spot.step // 51 + step
    }

    fun isFinished(spot: Spot): Boolean = spot is Spot.Home && spot.step == 5

    /** Absolute main-track cell index (0..51) for a track spot, or null elsewhere. */
    fun absoluteCell(owner: Player, spot: Spot): Int? =
        if (spot is Spot.Track) (owner.startCell + spot.progress) % 52 else null

    fun finishedCount(state: GameState, player: Player): Int =
        state.spots.count { it.key.owner == player && isFinished(it.value) }

    fun hasWon(state: GameState, player: Player): Boolean =
        state.spots.count { it.key.owner == player } == TOKENS_PER_PLAYER &&
            state.spots.filter { it.key.owner == player }.values.all { isFinished(it) }

    // --- move generation ---

    /** All legal moves for the side to move, given a die value. */
    fun legalMoves(state: GameState, die: Int): List<Move> {
        val mover = state.turn
        val moves = mutableListOf<Move>()
        for ((token, spot) in state.spots) {
            if (token.owner != mover) continue
            if (isFinished(spot)) continue

            if (spot is Spot.Base) {
                if (die == 6) {
                    val to = Spot.Track(0)
                    moves.add(Move(token, spot, to, capturesOn(state, mover, to), exitsBase = true))
                }
                continue
            }

            val fromAdvance = advance(spot)
            val newAdvance = fromAdvance + die
            if (newAdvance > HOME_FINISH) continue // overshoot — illegal for this token
            val to = spotForAdvance(newAdvance)
            moves.add(Move(token, spot, to, capturesOn(state, mover, to), exitsBase = false))
        }
        return moves
    }

    /** The spot a token lands on after reaching [advance] (0..56). Public so the UI can animate step-by-step. */
    fun spotForAdvance(advance: Int): Spot =
        if (advance < TRACK_LEN) Spot.Track(advance) else Spot.Home(advance - TRACK_LEN)

    /** Opponent tokens that would be sent home if [mover] lands on [to]. */
    private fun capturesOn(state: GameState, mover: Player, to: Spot): List<Token> {
        if (to !is Spot.Track) return emptyList() // home column is private; base/finish can't capture
        val abs = (mover.startCell + to.progress) % 52
        if (abs in SAFE_CELLS) return emptyList()
        return state.spots
            .filter { (t, sp) -> t.owner != mover && absoluteCell(t.owner, sp) == abs }
            .keys.toList()
    }

    /** Apply [move]: move the token, send any captures to base. Does not change turn/die. */
    fun applyMove(state: GameState, move: Move): GameState {
        val spots = LinkedHashMap(state.spots)
        for (captured in move.captures) spots[captured] = Spot.Base
        spots[move.token] = move.to
        return state.copy(spots = spots)
    }
}
