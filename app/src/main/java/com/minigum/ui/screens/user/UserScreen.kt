package com.minigum.ui.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.minigum.ui.components.PageScaffold
import com.minigum.ui.theme.MinigumTheme
import com.minigum.ui.theme.Sizes

@Composable
fun UserScreen(
    navController: NavController,
    viewModel: UserViewModel = hiltViewModel(),
) {
    PageScaffold(
        navController = navController,
        title = "User",
        mobileUI = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Sizes.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Sizes.md),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.xxl),
                )
                Text(
                    text = viewModel.userName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Welcome back! This is your player profile. Your name is randomly assigned at the start of a session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun UserScreenPreview() {
    MinigumTheme { UserScreen(navController = rememberNavController()) }
}
