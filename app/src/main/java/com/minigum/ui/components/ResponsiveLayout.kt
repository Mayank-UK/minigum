package com.minigum.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun ResponsiveLayout(
    mobileUI: @Composable () -> Unit,
    tabletUI: @Composable () -> Unit,
    desktopUI: @Composable () -> Unit
) {
    val windowSize = rememberWindowSize()

    when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> mobileUI()
        WindowWidthSizeClass.Medium -> tabletUI()
        WindowWidthSizeClass.Expanded -> desktopUI()
    }
}

@Composable
fun rememberWindowSize(): WindowSize {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    return WindowSize(
        widthSizeClass = when {
            screenWidth < 600.dp -> WindowWidthSizeClass.Compact
            screenWidth < 840.dp -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        },
        heightSizeClass = when {
            screenHeight < 480.dp -> WindowHeightSizeClass.Compact
            screenHeight < 900.dp -> WindowHeightSizeClass.Medium
            else -> WindowHeightSizeClass.Expanded
        }
    )
}

enum class WindowWidthSizeClass { Compact, Medium, Expanded }
enum class WindowHeightSizeClass { Compact, Medium, Expanded }

data class WindowSize(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass
)

