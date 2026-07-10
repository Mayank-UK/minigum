package com.minigum.ui.screens.chess

/**
 * A simple, self-contained chess opponent: negamax + alpha-beta with a
 * material + piece-square-table evaluation. Strength maps to search depth.
 * EASY additionally widens its pick to near-best moves for a forgiving feel.
 *
 * Pure Kotlin — called by [ChessViewModel] on a background dispatcher.
 */
object ChessAi {

    enum class AiStrength(val depth: Int) { EASY(2), MEDIUM(3), HARD(4) }

    private const val MATE = 1_000_000

    /** Picks the best move for the side to move in [pos]. */
    fun bestMove(pos: ChessEngine.Position, strength: AiStrength): ChessEngine.Move {
        val moves = orderedMoves(pos)
        if (moves.isEmpty()) error("no legal moves for AI")

        var bestScore = Int.MIN_VALUE
        val scored = ArrayList<Pair<ChessEngine.Move, Int>>(moves.size)
        var alpha = Int.MIN_VALUE
        val beta = Int.MAX_VALUE
        for (move in moves) {
            val next = ChessEngine.makeMove(pos, move)
            val score = -negamax(next, strength.depth - 1, -beta, -alpha, 1)
            if (score > bestScore) bestScore = score
            if (bestScore > alpha) alpha = bestScore
            scored.add(move to score)
        }

        val pool = if (strength == AiStrength.EASY) {
            // Keep EASY beatable: accept anything within a pawn of the best.
            scored.filter { it.second >= bestScore - 100 }
        } else {
            scored.filter { it.second == bestScore }
        }
        return pool.random().first
    }

    private fun negamax(pos: ChessEngine.Position, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        if (depth == 0) return evaluate(pos)

        val moves = ChessEngine.legalMoves(pos)
        if (moves.isEmpty()) {
            return if (ChessEngine.inCheck(pos, pos.sideToMove)) -MATE + ply else 0 // mate (sooner is better) or stalemate
        }
        var best = Int.MIN_VALUE
        var a = alpha
        for (move in orderedFrom(pos, moves)) {
            val next = ChessEngine.makeMove(pos, move)
            val score = -negamax(next, depth - 1, -beta, -a, ply + 1)
            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break // beta cutoff
        }
        return best
    }

    /** Static evaluation in centipawns, from the perspective of the side to move. */
    private fun evaluate(pos: ChessEngine.Position): Int {
        var score = 0 // white's perspective
        for (i in 0 until 64) {
            val p = pos.board[i] ?: continue
            val v = p.type.value + pst(p.type, i, p.side)
            score += if (p.side == ChessEngine.Side.WHITE) v else -v
        }
        return if (pos.sideToMove == ChessEngine.Side.WHITE) score else -score
    }

    /** Legal moves ordered captures-first (MVV-LVA) then promotions, for better pruning. */
    private fun orderedMoves(pos: ChessEngine.Position): List<ChessEngine.Move> =
        orderedFrom(pos, ChessEngine.legalMoves(pos))

    private fun orderedFrom(pos: ChessEngine.Position, moves: List<ChessEngine.Move>): List<ChessEngine.Move> {
        return moves.sortedByDescending { mv ->
            var s = 0
            mv.captured?.let { s += 10_000 + it.value - (pos.board[mv.from]?.type?.value ?: 0) }
            mv.promotion?.let { s += it.value }
            s
        }
    }

    // --- piece-square tables (white's perspective, a1 = index 0) ---

    private fun pst(type: ChessEngine.PieceType, sq: Int, side: ChessEngine.Side): Int {
        val idx = if (side == ChessEngine.Side.WHITE) sq else sq xor 56 // vertical mirror for black
        return when (type) {
            ChessEngine.PieceType.PAWN -> PAWN_PST[idx]
            ChessEngine.PieceType.KNIGHT -> KNIGHT_PST[idx]
            ChessEngine.PieceType.BISHOP -> BISHOP_PST[idx]
            ChessEngine.PieceType.ROOK -> ROOK_PST[idx]
            ChessEngine.PieceType.QUEEN -> QUEEN_PST[idx]
            ChessEngine.PieceType.KING -> KING_PST[idx]
        }
    }

    // Tables laid out from a8 (index 56) down to h1 (index 7) so they read naturally;
    // index arithmetic is rank-major (0=a1..63=h8), so we list them top rank first.
    private val PAWN_PST = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val KNIGHT_PST = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val BISHOP_PST = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val ROOK_PST = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0,
    )
    private val QUEEN_PST = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val KING_PST = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20,
    )
}
