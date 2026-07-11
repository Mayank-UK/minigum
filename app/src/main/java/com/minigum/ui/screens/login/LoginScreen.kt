package com.minigum.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.minigum.core.validators.emailValidator
import com.minigum.core.validators.nameValidator
import com.minigum.core.validators.passwordValidator
import com.minigum.ui.components.Button
import com.minigum.ui.components.ButtonType
import com.minigum.ui.components.TextField
import com.minigum.ui.components.PageScaffold
import com.minigum.ui.theme.Sizes

/**
 * Email/password sign-in & sign-up. Reached from any leaderboard entry point when the user is not
 * yet logged in. On success the screen pops back to wherever the user came from. Built from the
 * shared [TextField] / [MinigumButton] components and the `core/validators` rules.
 */
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    // Navigate back exactly once on successful authentication.
    LaunchedEffect(state.success) {
        if (state.success) navController.popBackStack()
    }

    PageScaffold(
        navController = navController,
        title = if (state.isSignUp) "Create account" else "Log in",
        mobileUI = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Sizes.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Sizes.md),
            ) {
                Text(
                    if (state.isSignUp) "Join the arcade" else "Welcome back",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (state.isSignUp) {
                    TextField(
                        value = displayName,
                        onValueChange = { displayName = it; viewModel.clearError() },
                        label = "Display name",
                        validator = ::nameValidator,
                    )
                }

                TextField(
                    value = email,
                    onValueChange = { email = it; viewModel.clearError() },
                    label = "Email",
                    validator = ::emailValidator,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )

                TextField(
                    value = password,
                    onValueChange = { password = it; viewModel.clearError() },
                    label = "Password",
                    validator = { passwordValidator(it) },
                    isPassword = true,
                    imeAction = if (state.isSignUp) ImeAction.Next else ImeAction.Done,
                )

                state.error?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    text = if (state.isSignUp) "Create account" else "Log in",
                    onClick = {
                        if (state.isSignUp) viewModel.signUp(email, password, displayName)
                        else viewModel.signIn(email, password)
                    },
                    loading = state.isLoading,
                )

                Button(
                    text = if (state.isSignUp) "Already have an account? Log in"
                    else "New here? Create an account",
                    onClick = { viewModel.isSignUp = !state.isSignUp },
                    type = ButtonType.Text,
                    fullWidth = false,
                )
            }
        },
    )
}
