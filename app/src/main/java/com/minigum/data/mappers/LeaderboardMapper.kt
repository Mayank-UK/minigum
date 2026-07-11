package com.minigum.data.mappers

import com.minigum.data.remote.dtos.ScoreDto
import com.minigum.domain.models.LeaderboardEntry

/**
 * Turns an already-score-ordered list of [ScoreDto] into ranked [LeaderboardEntry] rows, flagging
 * the current user's own row.
 *
 * Uses competition ranking (1224): tied scores share a rank, and the next distinct score jumps by
 * the number of tied entries ahead of it. The order produced by the Firestore query is preserved.
 */
fun List<ScoreDto>.toLeaderboardEntries(currentUid: String): List<LeaderboardEntry> {
    val entries = mutableListOf<LeaderboardEntry>()
    var prevScore: Int? = null
    for ((index, dto) in withIndex()) {
        val rank = if (dto.score != prevScore) index + 1 else entries.last().rank
        entries += LeaderboardEntry(
            rank = rank,
            displayName = dto.displayName,
            score = dto.score,
            isCurrentUser = dto.uid == currentUid,
        )
        prevScore = dto.score
    }
    return entries
}
