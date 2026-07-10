package com.minigum.ui.screens.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM correctness checks for the chess engine. */
class ChessEngineTest {

    private fun sq(rank: Int, file: Int) = ChessEngine.square(rank, file)

    /** Finds the legal move from->to (any promotion) and applies it. */
    private fun play(pos: ChessEngine.Position, from: Int, to: Int): ChessEngine.Position {
        val move = ChessEngine.legalMoves(pos).first { it.from == from && it.to == to }
        return ChessEngine.makeMove(pos, move)
    }

    @Test fun `start position has 20 legal moves`() {
        val pos = ChessEngine.startingPosition()
        assertEquals(20, ChessEngine.legalMoves(pos).size)
    }

    @Test fun `no legal move leaves own king in check`() {
        // Sanity: every legal move from the start keeps the mover's king safe.
        val pos = ChessEngine.startingPosition()
        for (move in ChessEngine.legalMoves(pos)) {
            val next = ChessEngine.makeMove(pos, move)
            assertFalse(ChessEngine.inCheck(next, ChessEngine.Side.WHITE))
        }
    }

    @Test fun `scholar's mate is checkmate`() {
        var pos = ChessEngine.startingPosition()
        pos = play(pos, sq(1, 4), sq(3, 4)) // 1. e4
        pos = play(pos, sq(6, 4), sq(4, 4)) //    e5
        pos = play(pos, sq(0, 5), sq(3, 2)) // 2. Bc4
        pos = play(pos, sq(7, 1), sq(5, 2)) //    Nc6
        pos = play(pos, sq(0, 3), sq(4, 7)) // 3. Qh5
        pos = play(pos, sq(7, 6), sq(5, 5)) //    Nf6
        pos = play(pos, sq(4, 7), sq(6, 5)) // 4. Qxf7#
        assertEquals(ChessEngine.Status.CHECKMATE, ChessEngine.status(pos))
    }

    @Test fun `castling is generated when path is clear`() {
        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. d3 d6 — clear the way, no castle-through-check.
        var pos = ChessEngine.startingPosition()
        pos = play(pos, sq(1, 4), sq(3, 4)); pos = play(pos, sq(6, 4), sq(4, 4))
        pos = play(pos, sq(0, 6), sq(2, 5)); pos = play(pos, sq(7, 6), sq(5, 5))
        pos = play(pos, sq(0, 5), sq(3, 2)); pos = play(pos, sq(7, 5), sq(4, 2))
        pos = play(pos, sq(1, 3), sq(2, 3)); pos = play(pos, sq(6, 3), sq(5, 3))
        // White should be able to castle kingside (e1 -> g1).
        assertTrue(ChessEngine.legalMovesFrom(pos, sq(0, 4)).any { it.isCastle && it.to == sq(0, 6) })
    }
}
