package com.minigum.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minigum.core.result.Result
import com.minigum.domain.models.LeaderboardEntry
import com.minigum.domain.models.LeaderboardGame
import com.minigum.domain.models.UserProfile
import com.minigum.domain.models.UserRank
import com.minigum.domain.session.UserSession
import com.minigum.domain.useCases.GetLeaderboardUseCase
import com.minigum.domain.useCases.GetUserRankUseCase
import com.minigum.domain.useCases.SubmitScoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Powers the inline leaderboard shown inside [com.minigum.ui.components.GameOverOverlay].
 *
 * Unlike [LeaderboardViewModel] (which reads its game/score from nav args via SavedStateHandle),
 * this one is parameter-driven — the host calls [load] with the concrete game + score — so it can
 * be embedded directly in any game's game-over screen. It submits the score (best-only) and then
 * loads the top entries + the user's rank, same as the full screen but in compact form.
 */
@HiltViewModel
class LeaderboardCardViewModel @Inject constructor(
    private val userSession: UserSession,
    private val submitScoreUseCase: SubmitScoreUseCase,
    private val getLeaderboardUseCase: GetLeaderboardUseCase,
    private val getUserRankUseCase: GetUserRankUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<LeaderboardCardState>(LeaderboardCardState.Loading)
    val state: StateFlow<LeaderboardCardState> = _state.asStateFlow()

    fun load(game: LeaderboardGame, score: Int) {
        viewModelScope.launch {
            _state.value = LeaderboardCardState.Loading
            val profile = userSession.user.first()
            if (profile == null) {
                _state.value = LeaderboardCardState.NotLoggedIn
                return@launch
            }
            loadForUser(game, score, profile)
        }
    }

    private suspend fun loadForUser(game: LeaderboardGame, score: Int, profile: UserProfile) {
        if (score >= 0) {
            submitScoreUseCase(game, profile.uid, profile.displayName, score)
        }
        val topResult = getLeaderboardUseCase(game, profile.uid)
        val rankResult = getUserRankUseCase(game, profile.uid)

        _state.value = when (topResult) {
            is Result.Failed -> LeaderboardCardState.Error(topResult.failure.message)
            is Result.Success -> LeaderboardCardState.Loaded(
                game = game,
                entries = topResult.value,
                userRank = (rankResult as? Result.Success)?.value,
            )
        }
    }
}

sealed interface LeaderboardCardState {
    data object Loading : LeaderboardCardState
    /** Signed out — the card shows a prompt to log in instead of scores. */
    data object NotLoggedIn : LeaderboardCardState
    data class Error(val message: String) : LeaderboardCardState
    data class Loaded(
        val game: LeaderboardGame,
        val entries: List<LeaderboardEntry>,
        val userRank: UserRank?,
    ) : LeaderboardCardState
}
