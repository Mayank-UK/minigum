package com.minigum.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minigum.core.mapping.toFailure
import com.minigum.core.result.Result
import com.minigum.core.validators.emailValidator
import com.minigum.core.validators.nameValidator
import com.minigum.core.validators.passwordValidator
import com.minigum.domain.useCases.SignInUseCase
import com.minigum.domain.useCases.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val signUpUseCase: SignUpUseCase,
) : ViewModel() {

    /** Whether the form is currently collecting sign-up (vs sign-in) input. */
    var isSignUp: Boolean
        get() = _uiState.value.isSignUp
        set(value) {
            _uiState.value = _uiState.value.copy(isSignUp = value, error = null)
        }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Clears the top-level error banner the moment the user edits any field. */
    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    fun signIn(email: String, password: String) {
        if (!validateAll(email, password, displayName = null)) return
        submit { signInUseCase(email.trim(), password) }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (!validateAll(email, password, displayName)) return
        submit { signUpUseCase(email.trim(), password, displayName.trim()) }
    }

    private fun submit(block: suspend () -> Result<*>) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val r = block()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(isLoading = false, success = true)
                is Result.Failed -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = r.failure.message,
                )
            }
        }
    }

    /**
     * Runs the shared validators (from `core/validators`) for all fields. The fields also validate
     * inline as the user types; this is the final gate on submit so an untouched field is still
     * caught. Returns true when everything is valid.
     */
    private fun validateAll(email: String, password: String, displayName: String?): Boolean {
        val message = emailValidator(email)
            ?: passwordValidator(password)
            ?: if (isSignUp) nameValidator(displayName.orEmpty()) else null
        return if (message == null) {
            _uiState.value = _uiState.value.copy(error = null)
            true
        } else {
            _uiState.value = _uiState.value.copy(error = message)
            false
        }
    }
}

data class AuthUiState(
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Set true once after a successful sign-in/up; the screen consumes it to navigate away. */
    val success: Boolean = false,
)
