package com.minigum.domain.models

/**
 * The current user's standing in a game's leaderboard.
 *
 * [rank] is 1-based and computed as "number of players with a better score + 1", so tied scores
 * share a rank. `null` means the user has not yet recorded a score in this game.
 */
data class UserRank(
    val rank: Int,
    val bestScore: Int,
)
