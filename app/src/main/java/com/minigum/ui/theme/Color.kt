package com.minigum.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Minigum's arcade-neon palette.
 *
 * The brand's visual identity (see the home drawer's aurora header) is dark indigo space shot
 * through with electric violet, mint-cyan, hot magenta and gold. These tokens make that identity
 * the single source of truth for color across the app — used by [MinigumTheme] and by the
 * [ArcadeColors] accent object for gradients/glows.
 */

// ---------------------------------------------------------------------------
//  Dark scheme — the showcase look (deep purple-black space + neon).
// ---------------------------------------------------------------------------
val PrimaryDark = Color(0xFFB388FF)
val OnPrimaryDark = Color(0xFF2A0A6B)
val PrimaryContainerDark = Color(0xFF4A148C)
val OnPrimaryContainerDark = Color(0xFFE9D5FF)

val SecondaryDark = Color(0xFF00E5C7)
val OnSecondaryDark = Color(0xFF003731)
val SecondaryContainerDark = Color(0xFF0B4A42)
val OnSecondaryContainerDark = Color(0xFFB6FFF2)

val TertiaryDark = Color(0xFFFF4DCE)
val OnTertiaryDark = Color(0xFF4A002E)
val TertiaryContainerDark = Color(0xFF7A1F6E)
val OnTertiaryContainerDark = Color(0xFFFFD6F0)

val BackgroundDark = Color(0xFF110A24)
val OnBackgroundDark = Color(0xFFEDE3FF)
val SurfaceDark = Color(0xFF1A0F33)
val OnSurfaceDark = Color(0xFFEDE3FF)
val SurfaceVariantDark = Color(0xFF2A1D4E)
val OnSurfaceVariantDark = Color(0xFFC9BEEA)
val SurfaceContainerLowDark = Color(0xFF1F1438)
val SurfaceContainerHighDark = Color(0xFF281B4A)

val ErrorDark = Color(0xFFFF5A6E)
val OnErrorDark = Color(0xFF2A0006)
val OutlineDark = Color(0xFF7E6BA8)
val OutlineVariantDark = Color(0xFF3A2D6B)

// ---------------------------------------------------------------------------
//  Light scheme — vibrant on lavender-white (kept energetic, not corporate).
// ---------------------------------------------------------------------------
val PrimaryLight = Color(0xFF6A1BDB)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE9D5FF)
val OnPrimaryContainerLight = Color(0xFF3B0764)

val SecondaryLight = Color(0xFF00A895)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFB6FFF2)
val OnSecondaryContainerLight = Color(0xFF002B25)

val TertiaryLight = Color(0xFFD81B9C)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD6F0)
val OnTertiaryContainerLight = Color(0xFF4A002E)

val BackgroundLight = Color(0xFFFBF7FF)
val OnBackgroundLight = Color(0xFF1A0F33)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1A0F33)
val SurfaceVariantLight = Color(0xFFEDE6FF)
val OnSurfaceVariantLight = Color(0xFF5A4C8A)
val SurfaceContainerLowLight = Color(0xFFF4EEFF)
val SurfaceContainerHighLight = Color(0xFFE9E0FF)

val ErrorLight = Color(0xFFC4002A)
val OnErrorLight = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFF7E6BA8)
val OutlineVariantLight = Color(0xFFD0C6EE)

/**
 * Named neon/gradient accents that don't map cleanly onto Material3 color roles — used for the
 * drawer aurora, avatar ring, leaderboard medals, and arcade buttons. Centralised here so screens
 * stop hard-coding `Color(0xFFFFD400)`.
 */
object ArcadeColors {
    val Gold = Color(0xFFFFD400)
    val Magenta = Color(0xFFFF4DCE)
    val Cyan = Color(0xFF00E5C7)
    val Violet = Color(0xFF7C4DFF)
    val Blue = Color(0xFF3D5AFE)

    /** Signature purple→blue gradient used by the avatar disc and primary buttons. */
    val PurpleGradient = listOf(Color(0xFF6A11CB), Color(0xFF2575FC))
    /** Rainbow sweep used by the avatar ring. */
    val RingSweep = listOf(Gold, Magenta, Blue, Cyan, Gold)
    /** Trophy/leaderboard accent. */
    val TrophyGradient = listOf(Gold, Color(0xFFFF6D00))
}
