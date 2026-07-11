package com.minigum.domain.useCases

import com.minigum.core.result.Result
import com.minigum.domain.models.LeaderboardEntry
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.repositoryinterfaces.LeaderboardRepository
import javax.inject.Inject

class GetLeaderboardUseCase @Inject constructor(private val repository: LeaderboardRepository) {
    suspend operator fun invoke(
        game: LeaderboardGame,
        currentUid: String,
        limit: Int = 10,
    ): Result<List<LeaderboardEntry>> = repository.getTopEntries(game, currentUid, limit)
}
