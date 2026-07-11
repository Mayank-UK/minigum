package com.minigum.ui.screens.leaderboard

import androidx.lifecycle.SavedStateHandle
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
import com.minigum.ui.navigation.LeaderboardArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Loads a single game's leaderboard for the signed-in user.
 *
 * Centralizes the auth gate (the "not logged in → login" rule) and the optional score submission:
 * if the screen was opened with a non-negative [LeaderboardArgs.SCORE], that score is submitted as
 * the player's best (best-only, race-safe) before the board is read. A negative score means the
 * user is just viewing the board.
 */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSession: UserSession,
    private val getLeaderboardUseCase: GetLeaderboardUseCase,
    private val getUserRankUseCase: GetUserRankUseCase,
    private val submitScoreUseCase: SubmitScoreUseCase,
) : ViewModel() {

    private val game: LeaderboardGame? = LeaderboardGame.fromId(
        savedStateHandle.get<String>(LeaderboardArgs.GAME)
    )
    private val pendingScore: Int = savedStateHandle.get<Int>(LeaderboardArgs.SCORE) ?: -1

    private val _state = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val resolvedGame = game
            if (resolvedGame == null) {
                _state.value = LeaderboardUiState.Error("Unknown game.")
                return@launch
            }
            // Auth gate: take the current user snapshot. If signed out, the screen sends the user
            // to login instead of showing (empty) data.
            val profile = userSession.user.first()
            if (profile == null) {
                _state.value = LeaderboardUiState.RequiresLogin
                return@launch
            }
            load(resolvedGame, profile)
        }
    }

    private suspend fun load(game: LeaderboardGame, profile: UserProfile) {
        _state.value = LeaderboardUiState.Loading

        // Submit the pending score first (best-only; failures are non-fatal — still show the board).
        var justSetRecord = false
        if (pendingScore >= 0) {
            justSetRecord = submitScoreUseCase(game, profile.uid, profile.displayName, pendingScore)
                .isSuccess
        }

        val topResult = getLeaderboardUseCase(game, profile.uid)
        val rankResult = getUserRankUseCase(game, profile.uid)

        _state.value = when {
            topResult is Result.Failed ->
                LeaderboardUiState.Error(topResult.failure.message)
            topResult is Result.Success ->
                LeaderboardUiState.Loaded(
                    game = game,
                    entries = topResult.value,
                    userRank = (rankResult as? Result.Success)?.value,
                    justSetRecord = justSetRecord,
                )
            else -> LeaderboardUiState.Loading
        }
    }
}

/** Screen-level states for the leaderboard. */
sealed interface LeaderboardUiState {
    /** Loading top entries / submitting score. */
    data object Loading : LeaderboardUiState
    /** User is not logged in — the screen redirects to [com.minigum.ui.navigation.NavRouter.LoginScreen]. */
    data object RequiresLogin : LeaderboardUiState
    data class Error(val message: String) : LeaderboardUiState
    data class Loaded(
        val game: LeaderboardGame,
        val entries: List<LeaderboardEntry>,
        val userRank: UserRank?,
        val justSetRecord: Boolean,
    ) : LeaderboardUiState
}
