package com.minigum.ui.screens.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minigum.ui.navigation.NavRouter
import com.minigum.ui.theme.Sizes
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/**
 * The app's welcome screen. A tiny pixel-art baby lives its own life, forever chasing a butterfly
 * it can never quite catch — it runs, leaps, and pauses to watch. The butterfly roams the whole
 * scene (darting sideways, swooping low, climbing high) and always flees just out of reach.
 * Purely decorative; Continue enters the game list. Drawn in Compose Canvas to match the app style.
 */
@Composable
fun WelcomeScreen(navController: NavController) {
    val mascot = remember { Mascot() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val music = remember { AmbientMusic(context.applicationContext) }
    var muted by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).toFloat().coerceIn(0f, 0.05f)
                last = now
                mascot.update(dt)
                frame += dt
            }
        }
    }

    // Play while the screen is foregrounded; stop when it leaves or the app is backgrounded.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> music.start()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> music.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        music.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            music.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Scene(mascot = mascot, frame = frame, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = { muted = !muted; music.setMuted(muted) },
            modifier = Modifier.align(Alignment.TopEnd).padding(Sizes.sm),
        ) {
            Icon(
                if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = "Mute music",
                tint = Color.White,
            )
        }

        Text(
            text = "Minigum",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = Sizes.xxl + Sizes.lg),
        )

        Button(
            onClick = {
                navController.navigate(NavRouter.HomeScreen.route) {
                    popUpTo(NavRouter.WelcomeScreen.route) { inclusive = true }
                }
            },
            shape = RoundedCornerShape(Sizes.lg),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Sizes.xl),
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

// ---- Scene -------------------------------------------------------------

@Composable
private fun Scene(mascot: Mascot, frame: Float, modifier: Modifier = Modifier) {
    val flowers = remember { buildFlowers() }
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") frame
        val w = size.width
        val h = size.height
        val groundY = h * 0.82f

        // Cap the butterfly's height at ~2× the character's height above the ground.
        val charHeightFrac = (SPRITE_ROWS * 0.013f * minOf(w, h)) / h
        mascot.highCap = (groundY / h) - 2f * charHeightFrac

        drawSky(w, h, groundY)
        drawSun(w, h)
        drawClouds(mascot.phase, w, h)
        drawGround(w, h, groundY)
        drawFlowers(flowers, w, h, groundY)
        mascot.butterfly?.let { drawButterfly(it, mascot.phase, w, h) }
        drawMascot(mascot, w, h, groundY)
    }
}

private fun DrawScope.drawSky(w: Float, h: Float, groundY: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF7EC8F2), Color(0xFFC9ECFF)), startY = 0f, endY = groundY),
        topLeft = Offset(0f, 0f),
        size = Size(w, groundY),
    )
}

private fun DrawScope.drawSun(w: Float, h: Float) {
    val c = Offset(w * 0.82f, h * 0.16f)
    val r = minOf(w, h) * 0.07f
    drawCircle(Color(0xFFFFE082), radius = r * 1.25f, center = c)
    drawCircle(Color(0xFFFFD54F), radius = r, center = c)
}

private fun DrawScope.drawClouds(t: Float, w: Float, h: Float) {
    val drift = (t * 0.02f) % 1f
    val clouds = listOf(0.15f to 0.14f, 0.55f to 0.22f, 0.35f to 0.30f)
    for ((baseX, baseY) in clouds) {
        val x = ((baseX + drift) % 1.2f - 0.1f) * w
        val y = baseY * h
        val r = w * 0.035f
        val c = Color.White.copy(alpha = 0.9f)
        drawCircle(c, radius = r, center = Offset(x, y))
        drawCircle(c, radius = r * 0.8f, center = Offset(x - r, y + r * 0.2f))
        drawCircle(c, radius = r * 0.8f, center = Offset(x + r, y + r * 0.2f))
    }
}

private fun DrawScope.drawGround(w: Float, h: Float, groundY: Float) {
    drawRect(Color(0xFF7CB342), Offset(0f, groundY), Size(w, h - groundY))
    drawRect(Color(0xFF689F38), Offset(0f, groundY), Size(w, h * 0.02f))
}

private data class Flower(val x: Float, val y: Float, val color: Color, val size: Float)

private val flowerColors = listOf(
    Color(0xFFEC407A), Color(0xFFFFFFFF), Color(0xFFFFB74D),
    Color(0xFFAB47BC), Color(0xFFFF7043), Color(0xFFE53935),
)

/** Scattered randomly across the grass — not in a line. */
private fun buildFlowers(): List<Flower> {
    val rng = java.util.Random()
    return List(16) {
        Flower(
            x = 0.03f + rng.nextFloat() * 0.94f,
            y = 0.05f + rng.nextFloat() * 0.80f, // fraction within the grass band
            color = flowerColors[rng.nextInt(flowerColors.size)],
            size = 0.005f + rng.nextFloat() * 0.006f,
        )
    }
}

private fun DrawScope.drawFlowers(flowers: List<Flower>, w: Float, h: Float, groundY: Float) {
    val grassH = h - groundY
    for (f in flowers) {
        val cx = f.x * w
        val cy = groundY + f.y * grassH
        val r = f.size * w
        for (i in 0 until 5) {
            val a = (i / 5f) * 6.283f
            drawCircle(f.color, radius = r, center = Offset(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r))
        }
        drawCircle(Color(0xFFFFCA28), radius = r * 0.7f, center = Offset(cx, cy))
    }
}

// ---- Pixel sprite ------------------------------------------------------

// 12-wide x 14-tall pixel baby. '.' = transparent. Arms raised, reaching for the butterfly.
private val BABY_FRAME = listOf(
    ".WWWWWWWW.",
    "WWWWPWPWWW",
    "WPSSSSSSPW",
    "WSSSSEESSW",
    "WSSSSSSSSW",
    ".WKSSSSKW.",
    "..GBBBGG..",
    ".GBBBBBBG.",
    ".GBBBBBBG.",
    "..BBBBBB..",
    "..BB..BB..",
    ".NN....NN.",
    ".NN....NN.",
    ".........."
)

private val PALETTE = mapOf(
    'P' to Color(0xFF5E3A78), // purple outline
    'W' to Color(0xFFF8F8FF), // white hood
    'S' to Color(0xFFFFDFC5), // skin
    'E' to Color(0xFF1E0F0A), // eyes
    'K' to Color(0xFF2E7D32), // backpack
    'G' to Color(0xFF4F6BD8), // blue shirt
    'B' to Color(0xFF6F84E8), // light blue clothes
    'N' to Color(0xFF6D4C41), // shoes
)

private const val SPRITE_COLS = 12
private const val SPRITE_ROWS = 14

private fun DrawScope.drawSprite(
    frame: List<String>,
    palette: Map<Char, Color>,
    ox: Float,
    oy: Float,
    px: Float,
    mirror: Boolean,
    scaleY: Float,
) {
    val rowH = px * scaleY
    for (row in frame.indices) {
        val line = frame[row]
        for (col in line.indices) {
            val color = palette[line[col]] ?: continue
            val c = if (mirror) SPRITE_COLS - 1 - col else col
            // +0.6f closes sub-pixel seams between blocks.
            drawRect(color, topLeft = Offset(ox + c * px, oy + row * rowH), size = Size(px + 0.6f, rowH + 0.6f))
        }
    }
}

private fun DrawScope.drawMascot(m: Mascot, w: Float, h: Float, groundY: Float) {
    val px = minOf(w, h) * 0.013f
    val scaleY = m.squash * (1f - m.sit * 0.35f)
    val feetY = groundY - m.yOffset * h
    val ox = m.x * w - SPRITE_COLS * px / 2f
    val oy = feetY - SPRITE_ROWS * px * scaleY

    // Shadow on the ground (fades while airborne).
    val air = m.yOffset / 0.15f
    drawOval(
        Color.Black.copy(alpha = 0.16f * (1f - air)),
        Offset(m.x * w - SPRITE_COLS * px * 0.3f, groundY - px * 0.4f),
        Size(SPRITE_COLS * px * 0.6f, px * 0.7f),
    )

    val bob = if (m.action == Mascot.Action.CHASE) sin(m.phase * 9f).toFloat() * px * 0.5f else 0f
    drawSprite(BABY_FRAME, PALETTE, ox, oy - bob, px, mirror = m.facing < 0, scaleY = scaleY)
}

// ---- Mascot model ------------------------------------------------------

class Mascot {
    enum class Action { CHASE, WATCH, JUMP, SIT }

    var x = 0.3f
    var facing = 1
    var action: Action = Action.CHASE
    var phase = 0f
    var yOffset = 0f
    var squash = 1f
    var sit = 0f
    var butterfly: Butterfly? = Butterfly(0.6f)
    /** Highest the butterfly may fly (y fraction). Set each frame from the screen size. */
    var highCap = 0.62f

    private var actionTime = 0f
    private var actionDuration = 3f
    private val rng = java.util.Random()

    fun update(dt: Float) {
        phase += dt
        actionTime += dt
        if (butterfly == null) butterfly = Butterfly(x)
        val b = butterfly!!
        facing = if (b.x >= x) 1 else -1

        when (action) {
            Action.CHASE -> {
                x = (x + facing * 0.20f * dt).coerceIn(0.08f, 0.92f)
                yOffset = abs(sin(phase * 9f)) * 0.045f // little running hops
                if (actionTime >= actionDuration) next(b)
            }
            Action.WATCH -> {
                yOffset += (0f - yOffset) * minOf(1f, dt * 8f)
                if (actionTime >= actionDuration) next(b)
            }
            Action.JUMP -> {
                val p = (actionTime / actionDuration).coerceIn(0f, 1f)
                yOffset = sin(p * Math.PI.toFloat()) * 0.15f
                squash = if (p < 0.18f || p > 0.82f) 0.86f else 1.12f
                if (actionTime >= actionDuration) { yOffset = 0f; squash = 1f; next(b) }
            }
            Action.SIT -> {
                sit = smoothstep(actionTime, actionDuration)
                yOffset += (0f - yOffset) * minOf(1f, dt * 8f)
                if (actionTime >= actionDuration) { sit = 0f; next(b) }
            }
        }

        b.update(dt, x, highCap)
    }

    private fun smoothstep(t: Float, d: Float): Float {
        val n = (t / d).coerceIn(0f, 1f)
        return n * n * (3 - 2 * n)
    }

    private fun next(b: Butterfly) {
        actionTime = 0f
        val r = rng.nextFloat()
        // Leap more often when the butterfly swoops low (almost within reach).
        val low = b.y > 0.48f
        action = when (action) {
            Action.CHASE -> when {
                low && r < 0.5f -> Action.JUMP
                r < 0.30f -> Action.WATCH
                r < 0.45f -> Action.JUMP
                r < 0.50f -> Action.SIT
                else -> Action.CHASE
            }
            Action.WATCH -> if (r < 0.5f) Action.JUMP else Action.CHASE
            Action.JUMP -> Action.CHASE
            Action.SIT -> Action.CHASE
        }
        actionDuration = when (action) {
            Action.CHASE -> 2.5f + rng.nextFloat() * 2.5f
            Action.WATCH -> 1.0f + rng.nextFloat() * 1.2f
            Action.JUMP -> 0.85f
            Action.SIT -> 1.6f + rng.nextFloat() * 1.2f
        }
    }
}

/** A butterfly that roams the whole scene — darting sideways, swooping low, climbing high — and
 *  flees upward whenever the baby closes in, so it's never caught. */
class Butterfly(startX: Float) {
    var x: Float = (startX + 0.25f).coerceIn(0.15f, 0.85f)
    var y: Float = 0.4f
    private var tx = x
    private var ty = y
    private var t = 0f
    private var retargetIn = 1.5f
    private val rng = java.util.Random()

    fun update(dt: Float, charX: Float, minY: Float) {
        t += dt
        retargetIn -= dt
        if (hypot(tx - x, ty - y) < 0.05f || retargetIn <= 0f) retarget(minY)
        // Baby got close — flee to the opposite side and climb out of reach.
        if (abs(x - charX) < 0.13f) flee(charX, minY)

        val speed = 0.32f
        x += sign(tx - x) * speed * dt
        y += sign(ty - y) * speed * 0.8f * dt
        // Organic flutter.
        x = (x + sin(t * 5f) * 0.01f).coerceIn(0.05f, 0.95f)
        y = (y + sin(t * 7f) * 0.008f).coerceIn(minY, 0.80f)
    }

    private fun retarget(minY: Float) {
        tx = 0.1f + rng.nextFloat() * 0.8f
        ty = minY + 0.02f + rng.nextFloat() * (0.78f - minY) // anywhere across its allowed band
        retargetIn = 0.7f + rng.nextFloat() * 1.6f
    }

    private fun flee(charX: Float, minY: Float) {
        var tries = 0
        do {
            tx = if (charX > 0.5f) rng.nextFloat() * 0.4f else 0.55f + rng.nextFloat() * 0.4f
            ty = minY + rng.nextFloat() * 0.06f // climb up to its cap, out of reach
            tries++
        } while (abs(tx - charX) < 0.22f && tries < 6)
        retargetIn = 1.2f
    }
}

private fun DrawScope.drawButterfly(b: Butterfly, t: Float, w: Float, h: Float) {
    val cx = b.x * w
    val cy = b.y * h
    val flap = abs(sin(t * 16f))
    val wingW = w * 0.022f * (0.35f + flap * 0.65f)
    val wingH = w * 0.028f
    drawOval(Color(0xFFBA68C8), Offset(cx - wingW - w * 0.004f, cy - wingH * 0.5f), Size(wingW, wingH))
    drawOval(Color(0xFFFF8A65), Offset(cx + w * 0.004f, cy - wingH * 0.5f), Size(wingW, wingH))
    drawOval(Color(0xFF5D4037), Offset(cx - w * 0.0015f, cy - wingH * 0.6f), Size(w * 0.003f, wingH * 1.1f))
}
