package com.minigum.ui.screens.user

import androidx.lifecycle.ViewModel
import com.minigum.domain.session.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    userSession: UserSession,
) : ViewModel() {

    /** Same random name shown in the home drawer — sourced from the shared [UserSession]. */
    val userName = userSession.userName
}
