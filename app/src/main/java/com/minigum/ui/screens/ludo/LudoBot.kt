package com.minigum.ui.screens.ludo

import kotlin.random.Random

/**
 * A noticeably stronger Ludo bot.
 *
 * Instead of a fixed priority list, it scores the position that results from
 * each legal move with an evaluation function, then picks the best. The
 * evaluation rewards progress, finishing, captures and safe squares, and —
 * crucially — penalises leaving a token on a square where an opponent could
 * capture it on their next roll (weighted by how advanced that token is, so
 * it protects valuable tokens). It also values keeping opponents stuck in
 * their yards. Still pure Kotlin and instant to run.
 */
object LudoBot {

    fun chooseMove(state: LudoEngine.GameState, moves: List<LudoEngine.Move>): LudoEngine.Move {
        require(moves.isNotEmpty()) { "bot asked to move with no legal moves" }
        val bot = state.turn
        val scored = moves.map { it to evaluate(LudoEngine.applyMove(state, it), bot) }
        val best = scored.maxOf { it.second }
        // Random tie-break only among genuinely equal-best moves, so games vary
        // without ever picking a strictly worse move.
        val candidates = scored.filter { it.second == best }
        return candidates[Random.nextInt(candidates.size)].first
    }

    // --- evaluation (centipawn-ish, from [bot]'s perspective) ---

    private fun evaluate(state: LudoEngine.GameState, bot: LudoEngine.Player): Int {
        var score = 0
        for ((token, spot) in state.spots) {
            if (token.owner == bot) {
                score += ownTokenValue(state, token, spot)
            } else {
                score += opponentTokenValue(spot)
            }
        }
        return score
    }

    private fun ownTokenValue(state: LudoEngine.GameState, token: LudoEngine.Token, spot: LudoEngine.Spot): Int {
        val adv = LudoEngine.advance(spot)
        return when {
            LudoEngine.isFinished(spot) -> FINISH // a token home is worth the most
            spot is LudoEngine.Spot.Base -> BASE_PENALTY // reward spreading tokens out
            spot is LudoEngine.Spot.Home -> adv * PROGRESS + HOME_NEAR // safe + close to finishing
            else -> {
                var v = adv * PROGRESS
                val abs = LudoEngine.absoluteCell(token.owner, spot)
                if (abs != null && abs in LudoEngine.SAFE_CELLS) {
                    v += SAFE_BONUS // a defended square — no capture possible
                } else {
                    // Expected loss if an opponent captures this token next roll.
                    val threats = threatCount(state, token, spot)
                    if (threats > 0) {
                        val progressValue = adv * PROGRESS + CAPTURE_HURT
                        v -= ((threats / 6.0) * progressValue).toInt()
                    }
                }
                v
            }
        }
    }

    private fun opponentTokenValue(spot: LudoEngine.Spot): Int {
        // We want opponents stuck in base and far from home.
        return when {
            LudoEngine.isFinished(spot) -> -FINISH
            spot is LudoEngine.Spot.Base -> OPP_IN_BASE // good for us
            else -> -LudoEngine.advance(spot) * OPP_PROGRESS
        }
    }

    /**
     * How many distinct die values (1..6) would let *some* opponent capture
     * [token] on its current square next turn. Higher = more likely to be hit.
     */
    private fun threatCount(state: LudoEngine.GameState, token: LudoEngine.Token, spot: LudoEngine.Spot): Int {
        val target = LudoEngine.absoluteCell(token.owner, spot) ?: return 0
        if (target in LudoEngine.SAFE_CELLS) return 0
        val distances = HashSet<Int>()
        for ((other, otherSpot) in state.spots) {
            if (other.owner == token.owner) continue
            val otherAbs = LudoEngine.absoluteCell(other.owner, otherSpot) ?: continue
            val dist = ((target - otherAbs) % 52 + 52) % 52
            if (dist in 1..6) distances.add(dist)
        }
        return distances.size
    }

    private const val FINISH = 1000
    private const val PROGRESS = 5
    private const val HOME_NEAR = 25
    private const val SAFE_BONUS = 20
    private const val BASE_PENALTY = -8
    private const val OPP_IN_BASE = 120
    private const val OPP_PROGRESS = 3
    /** How much it hurts to lose a token (on top of its lost progress). */
    private const val CAPTURE_HURT = 150
}
