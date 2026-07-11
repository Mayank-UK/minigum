package com.minigum.domain.useCases

import com.minigum.core.result.Result
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.repositoryinterfaces.LeaderboardRepository
import javax.inject.Inject

class SubmitScoreUseCase @Inject constructor(private val repository: LeaderboardRepository) {
    suspend operator fun invoke(
        game: LeaderboardGame,
        uid: String,
        displayName: String,
        score: Int,
    ): Result<Unit> = repository.submitScore(game, uid, displayName, score)
}
