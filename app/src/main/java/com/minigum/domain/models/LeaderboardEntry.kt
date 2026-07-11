package com.minigum.domain.models

/** A single row in a game's leaderboard, already ranked. */
data class LeaderboardEntry(
    val rank: Int,
    val displayName: String,
    val score: Int,
    val isCurrentUser: Boolean,
)
