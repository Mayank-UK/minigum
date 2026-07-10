package com.minigum.ui.screens.ludo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM correctness checks for the Ludo engine. */
class LudoEngineTest {

    @Test fun `no moves from base without a six`() {
        val state = LudoEngine.initialState() // all tokens in base, Red to move
        for (die in 1..5) {
            assertEquals("die=$die should yield no moves", 0, LudoEngine.legalMoves(state, die).size)
        }
    }

    @Test fun `a six yields one exit move per token`() {
        val state = LudoEngine.initialState()
        val moves = LudoEngine.legalMoves(state, 6)
        assertEquals(4, moves.size) // Red's four tokens can each exit
        assertTrue(moves.all { it.exitsBase })
    }

    @Test fun `capture sends the opponent back to base`() {
        // Red lands on absolute cell 12 (not a safe square) where a Green token sits.
        val base = LudoEngine.initialState()
        val red = LudoEngine.Token(LudoEngine.Player.RED, 0)
        val green = LudoEngine.Token(LudoEngine.Player.GREEN, 0)
        val spots = base.spots.toMutableMap()
        // Red at absolute 10 (progress 10); rolls 2 → lands on absolute 12.
        spots[red] = LudoEngine.Spot.Track(10)
        // Green on absolute 12: (13 + p) % 52 = 12 → p = 51.
        spots[green] = LudoEngine.Spot.Track(51)
        val state = base.copy(spots = spots, turn = LudoEngine.Player.RED)

        val move = LudoEngine.legalMoves(state, 2).first { it.token == red }
        assertEquals(listOf(green), move.captures)

        val after = LudoEngine.applyMove(state, move)
        assertEquals(LudoEngine.Spot.Base, after.spots[green]) // sent home
        assertEquals(LudoEngine.Spot.Track(12), after.spots[red]) // landed on 12
    }

    @Test fun `cannot overshoot the final home square`() {
        val base = LudoEngine.initialState()
        val red = LudoEngine.Token(LudoEngine.Player.RED, 0)
        val spots = base.spots.toMutableMap()
        // advance 55 → one short of finish (56); only a roll of 1 is legal.
        spots[red] = LudoEngine.Spot.Home(4) // advance = 51 + 4 = 55
        val state = base.copy(spots = spots, turn = LudoEngine.Player.RED)

        assertEquals(0, LudoEngine.legalMoves(state, 2).size) // would overshoot
        val moves = LudoEngine.legalMoves(state, 1)
        assertEquals(1, moves.size)
        assertTrue(moves.first().scores)
    }
}
