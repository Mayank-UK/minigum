package com.minigum.data.repositoryimplementations

import com.minigum.core.guards.guardedFirebaseCall
import com.minigum.core.guards.safeRepositoryCall
import com.minigum.core.result.Result
import com.minigum.data.mappers.toLeaderboardEntries
import com.minigum.data.remote.LeaderboardService
import com.minigum.domain.models.LeaderboardEntry
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.models.UserRank
import com.minigum.domain.repositoryinterfaces.LeaderboardRepository
import javax.inject.Inject

/**
 * Implements [LeaderboardRepository] on top of [LeaderboardService]. Mirrors [PostRepositoryImpl]:
 * every Firestore call is wrapped in [guardedFirebaseCall] + [safeRepositoryCall] so the UI sees
 * only [Result] / [com.minigum.core.failures.Failure].
 *
 * The rank/entry logic (game-agnostic) lives here; the Firestore-specific querying (direction,
 * transaction) lives in the service. `safeRepositoryCall` has several optional callback params, so
 * the block is passed as `block =` (a trailing lambda would otherwise bind to the last callback).
 */
class LeaderboardRepositoryImpl @Inject constructor(
    private val service: LeaderboardService,
) : LeaderboardRepository {

    override suspend fun getTopEntries(
        game: LeaderboardGame,
        currentUid: String,
        limit: Int,
    ): Result<List<LeaderboardEntry>> = safeRepositoryCall(block = {
        val top = guardedFirebaseCall { service.getTopScores(game, limit) }
        top.toLeaderboardEntries(currentUid)
    })

    override suspend fun getUserRank(game: LeaderboardGame, uid: String): Result<UserRank?> =
        safeRepositoryCall(block = {
            val userScore = guardedFirebaseCall { service.getUserScore(game, uid) }
                ?: return@safeRepositoryCall null
            // rank = (# of strictly better scores) + 1 — ties share a rank.
            val better = guardedFirebaseCall { service.countBetterScores(game, userScore.score) }
            UserRank(rank = (better + 1).toInt(), bestScore = userScore.score)
        })

    override suspend fun submitScore(
        game: LeaderboardGame,
        uid: String,
        displayName: String,
        score: Int,
    ): Result<Unit> = safeRepositoryCall(block = {
        // Returns true on a new record, false otherwise; either way it's a success for the caller.
        guardedFirebaseCall { service.submitBestScore(game, uid, displayName, score) }
    })
}
