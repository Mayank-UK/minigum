package com.minigum.domain.useCases

import com.minigum.core.result.Result
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.models.UserRank
import com.minigum.domain.repositoryinterfaces.LeaderboardRepository
import javax.inject.Inject

class GetUserRankUseCase @Inject constructor(private val repository: LeaderboardRepository) {
    suspend operator fun invoke(game: LeaderboardGame, uid: String): Result<UserRank?> =
        repository.getUserRank(game, uid)
}
