package com.minigum.domain.models

/**
 * The games that contribute to a leaderboard.
 *
 * Scores are NOT comparable across games (Snake points ≠ Runner metres ≠ Sudoku seconds), so each
 * game owns its own leaderboard. [higherIsBetter] encodes the sort direction: most games reward a
 * higher score, but Sudoku is time-based and a **lower** number of seconds is better.
 */
enum class LeaderboardGame(
    val id: String,
    val displayName: String,
    val higherIsBetter: Boolean,
    /** Short label appended to a score in the UI, e.g. "320 pts" / "412 m" / "84 s". */
    val unit: String,
) {
    SNAKE("snake", "Snake", higherIsBetter = true, unit = "pts"),
    PONG("pong", "Pong", higherIsBetter = true, unit = "pts"),
    SHOOTER("shooter", "Shooter", higherIsBetter = true, unit = "pts"),
    RUNNER("runner", "Runner", higherIsBetter = true, unit = "m"),
    FLAPPY("flappy", "Flappy", higherIsBetter = true, unit = "pts"),
    BALLOON("balloon", "Balloon", higherIsBetter = true, unit = "m"),
    SUDOKU("sudoku", "Sudoku", higherIsBetter = false, unit = "s");

    companion object {
        fun fromId(id: String?): LeaderboardGame? = entries.firstOrNull { it.id == id }
    }
}
