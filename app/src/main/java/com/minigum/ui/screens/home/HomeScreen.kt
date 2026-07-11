package com.minigum.ui.screens.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.minigum.ui.components.WindFlowerField
import com.minigum.ui.navigation.NavRouter
import com.minigum.ui.theme.ArcadeColors
import com.minigum.ui.theme.Sizes
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val displayName by viewModel.displayName.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    fun openDrawer() = scope.launch { drawerState.open() }
    fun navigate(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route)
    }

    // can't use PageScaffold here as this screen contains LazyVerticalGrid at root
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MinigumDrawer(
                userName = displayName,
                isLoggedIn = isLoggedIn,
                onNavigate = ::navigate,
            )
        },
    ) {
        Scaffold(
            topBar = {
                // Gradient bar (purple→blue) with a bold tracked-out wordmark and a gold neon
                // hairline along the bottom — replaces the flat primary-colored bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(ArcadeColors.PurpleGradient)),
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "MINIGUM",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { openDrawer() }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open menu",
                                    tint = Color.White,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(ArcadeColors.Gold),
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Ambient wind-blown flowers, fixed behind the (transparent) scrolling grid.
                WindFlowerField(modifier = Modifier.fillMaxSize())

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Sizes.md),
                ) {
                    items(viewModel.links) { link ->
                        Column(
                            modifier = Modifier
                                .padding(Sizes.sm)
                                .clickable {
                                    navController.navigate(link.routePath)
                                }
                        ) {
                            GameLink(
                                image = link.image,
                                onClick = {
                                    navController.navigate(link.routePath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
//  Drawer — a playful arcade-style panel: animated aurora header with a
//  glowing initials avatar (showing the assigned random name), then vibrant
//  gradient navigation cards and a drifting marquee footer.
// =========================================================================

@Composable
private fun MinigumDrawer(
    userName: String,
    isLoggedIn: Boolean,
    onNavigate: (String) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            // Ambient wind-blown flowers, drifting behind the whole drawer.
            WindFlowerField(modifier = Modifier.fillMaxSize())

            Column(modifier = Modifier.fillMaxHeight()) {
                AuroraHeader(
                    userName = userName,
                    isLoggedIn = isLoggedIn,
                    onLogin = { onNavigate(NavRouter.LoginScreen.route) },
                )

                Spacer(Modifier.height(Sizes.md))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Sizes.md),
                    verticalArrangement = Arrangement.spacedBy(Sizes.sm),
                ) {
                    DrawerNavCard(
                        title = "Leaderboard",
                        subtitle = "Top players & your rank",
                        accent = listOf(Color(0xFFFFD400), Color(0xFFFF6D00)),
                        icon = Icons.Filled.EmojiEvents,
                        onClick = { onNavigate(NavRouter.LeaderboardHubScreen.route) },
                    )
                    DrawerNavCard(
                        title = "User",
                        subtitle = "Your player profile",
                        accent = listOf(Color(0xFF7C4DFF), Color(0xFF3D5AFE)),
                        icon = Icons.Filled.Person,
                        onClick = { onNavigate(NavRouter.UserScreen.route) },
                    )
                    DrawerNavCard(
                        title = "Game logic",
                        subtitle = "How each game works inside",
                        accent = listOf(Color(0xFF00BFA5), Color(0xFF00838F)),
                        icon = Icons.Filled.Psychology,
                        onClick = { onNavigate(NavRouter.GameLogicScreen.route) },
                    )
                    DrawerNavCard(
                        title = "About",
                        subtitle = "About Minigum",
                        accent = listOf(Color(0xFFFFB300), Color(0xFFFF6D00)),
                        icon = Icons.Filled.Info,
                        onClick = { onNavigate(NavRouter.AboutScreen.route) },
                    )
                }

                Spacer(Modifier.weight(1f))

                MarqueeFooter()
            }
        }
    }
}

/** Animated hero header: a living aurora with floating pixel stars, the avatar and name on top. */
@Composable
private fun AuroraHeader(userName: String, isLoggedIn: Boolean, onLogin: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 12_000, easing = LinearEasing)),
        label = "phase",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6_000, easing = LinearEasing)),
        label = "spin",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAurora(phase, size.width, size.height)
            drawStars(phase, size.width, size.height)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(Sizes.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarWithRing(initials = initialsOf(userName), rotation = spin)
            Spacer(Modifier.height(Sizes.sm))
            Text(
                text = userName,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
            )
            Text(
                text = "arcade member",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            // Signed-out players see a Log in button here; signed-in players see nothing extra
            // (their profile lives behind the User card).
            if (!isLoggedIn) {
                Spacer(Modifier.height(Sizes.sm))
                Button(onClick = onLogin) { Text("Log in") }
            }
        }
    }
}

/** Glowing circular avatar with initials, wrapped in a rotating conic-gradient ring. */
@Composable
private fun AvatarWithRing(initials: String, rotation: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
        // Rotating gradient ring.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFFD400), Color(0xFFFF4DCE),
                        Color(0xFF4D8CFF), Color(0xFF00E5C7), Color(0xFFFFD400),
                    ),
                ),
                style = Stroke(width = 5.dp.toPx()),
            )
        }
        // Solid disc with the initials.
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF6A11CB), Color(0xFF2575FC)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
            )
        }
    }
}

/** A single navigation card: gradient accent stripe, icon chip, title/subtitle, chevron. */
@Composable
private fun DrawerNavCard(
    title: String,
    subtitle: String,
    accent: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.md),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sizes.md))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(Sizes.sm),
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.lg + Sizes.md)
                .clip(RoundedCornerShape(Sizes.sm))
                .background(Brush.linearGradient(accent)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

/** Slowly drifting sparkle marquee at the bottom of the drawer. */
@Composable
private fun MarqueeFooter() {
    val transition = rememberInfiniteTransition(label = "marquee")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 9_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "x",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Sizes.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFB300),
            modifier = Modifier.offset { IntOffset((x * 40f).toInt(), 0) },
        )
        Spacer(Modifier.size(Sizes.xs))
        Text(
            "minigum • play forever",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

// ---- Drawing helpers --------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(phase: Float, w: Float, h: Float) {
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color(0xFF1A1033), Color(0xFF2D1B69), Color(0xFF123A6B)),
        ),
    )
    // Three soft drifting color blobs that morph into an aurora.
    val blobs = listOf(
        Blob(0xFF7C4DFF, 0.55f, 0.45f, 1f, 0.7f),
        Blob(0xFF00E5C7, 0.45f, 0.6f, 1.3f, 0.6f),
        Blob(0xFFFF4DCE, 0.6f, 0.5f, 0.8f, 0.9f),
    )
    for (b in blobs) {
        val angle = phase * 2f * Math.PI.toFloat() * b.speed
        val cx = (b.cx + cos(angle) * 0.12f) * w
        val cy = (b.cy + sin(angle) * 0.12f) * h
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(b.color).copy(alpha = b.alpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.6f,
            ),
            center = Offset(cx, cy),
            radius = w * 0.6f,
        )
    }
}

private data class Blob(val color: Long, val cx: Float, val cy: Float, val speed: Float, val alpha: Float)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStars(phase: Float, w: Float, h: Float) {
    val positions = listOf(
        0.12f to 0.18f, 0.8f to 0.12f, 0.25f to 0.35f,
        0.68f to 0.3f, 0.9f to 0.42f, 0.4f to 0.16f, 0.55f to 0.34f,
    )
    for ((i, p) in positions.withIndex()) {
        val twinkle = (sin(phase * 2f * Math.PI.toFloat() + i) + 1f) / 2f
        val size = (1.5f + (i % 3)) * 1.5f
        drawRect(
            color = Color.White.copy(alpha = 0.35f + twinkle * 0.5f),
            topLeft = Offset(p.first * w - size / 2, p.second * h - size / 2),
            size = androidx.compose.ui.geometry.Size(size, size),
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(' ').filter { it.isNotEmpty() }
    return parts.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifEmpty { "P" }
}

@Composable
fun GameLink(
    @DrawableRes image: Int,
    onClick: () -> Unit,
) {
    Image(
        contentDescription = "Snake image",
        painter = painterResource(image),
        modifier = Modifier
            .clip(RoundedCornerShape(Sizes.md))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit,
    )
}
