package com.minigum.domain.repositoryinterfaces

import com.minigum.core.result.Result
import com.minigum.domain.models.LeaderboardEntry
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.models.UserRank

/**
 * Per-game leaderboard reads/writes against Cloud Firestore.
 *
 * Each game keeps the single best score per user (one document per user-per-game), so a user
 * appears at most once per leaderboard and their stored score never decreases.
 */
interface LeaderboardRepository {

    /** The top [limit] entries for [game], ranked, with the current user's row flagged. */
    suspend fun getTopEntries(
        game: LeaderboardGame,
        currentUid: String,
        limit: Int = 10,
    ): Result<List<LeaderboardEntry>>

    /**
     * The current user's standing for [game]: their best score and 1-based global rank, computed as
     * "players with a better score + 1". `null` if the user has not recorded a score in this game.
     */
    suspend fun getUserRank(game: LeaderboardGame, uid: String): Result<UserRank?>

    /**
     * Records [score] for [uid] in [game] iff it improves on the stored best (race-safe via a
     * Firestore transaction). No-ops when the score isn't a personal record.
     */
    suspend fun submitScore(
        game: LeaderboardGame,
        uid: String,
        displayName: String,
        score: Int,
    ): Result<Unit>
}
