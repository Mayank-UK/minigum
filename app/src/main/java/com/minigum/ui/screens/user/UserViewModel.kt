package com.minigum.ui.screens.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minigum.domain.models.UserProfile
import com.minigum.domain.session.UserSession
import com.minigum.domain.useCases.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UserViewModel @Inject constructor(
    userSession: UserSession,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    /** The signed-in user, or `null` when signed out. */
    val user: StateFlow<UserProfile?> = userSession.user
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stable guest name shown when signed out. */
    val guestDisplayName: String = userSession.guestDisplayName

    fun signOut() {
        viewModelScope.launch { signOutUseCase() }
    }
}
