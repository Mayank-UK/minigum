package com.minigum.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * A subtle ambient background: a scatter of tiny flowers that sway with a gusting wind,
 * plus a few wind-blown specks drifting across. Purely decorative — matches the tiny
 * flower aesthetic of the welcome screen.
 *
 * Flowers are positioned deterministically (fixed seed) so they don't jump between
 * recompositions; only their sway angle is animated.
 *
 * @param flowerCount  how many flowers to scatter.
 * @param particleCount how many wind-blown specks to drift across.
 * @param stemColor    colour of flower stems.
 * @param particleColor colour of the drifting wind specks (keep it low-alpha/soft).
 */
@Composable
fun WindFlowerField(
    modifier: Modifier = Modifier,
    flowerCount: Int = 12,
    leafCount: Int = 22,
    flowerColors: List<Color> = DefaultFlowerColors,
    stemColor: Color = Color(0xFF4CAF50),
    leafColors: List<Color> = DefaultLeafColors,
    streakColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.18f),
) {
    val transition = rememberInfiniteTransition(label = "wind")
    // `t` advances 0→1 over WIND_LOOP_MS, used as a continuous phase for all motion below.
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = WIND_LOOP_MS, easing = LinearEasing)),
        label = "t",
    )

    val flowers = remember(flowerCount, flowerColors.size) { buildFlowers(flowerCount) }
    val leaves = remember(leafCount, leafColors.size) { buildLeaves(leafCount) }
    val streaks = remember(STREAK_COUNT) { buildStreaks(STREAK_COUNT) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Wind envelope: gusts that rise and fall so the flowers breathe rather than sit still.
        // Gust period ≈ 2s, slower swell ≈ 5s, combined for an organic feel.
        val windStrength = windStrengthAt(t)

        for (f in flowers) {
            drawFlower(f, t, windStrength, w, h, flowerColors, stemColor)
        }
        drawWindStreaks(streaks, t, windStrength, w, h, streakColor)
        drawLeaves(leaves, t, windStrength, w, h, leafColors)
    }
}

// ---- Model -----------------------------------------------------------------

private data class Flower(
    val x: Float,          // 0..1 horizontal position
    val yBand: Float,      // 0..1 within the drawable band
    val heightFrac: Float, // stem height as a fraction of min(w,h)
    val colorIndex: Int,
    val phase: Float,      // per-flower sway phase
    val freq: Float,       // per-flower sway speed multiplier
)

internal data class Leaf(
    val y: Float,          // base vertical position 0..1
    val speed: Float,      // base drift speed (fraction of width per loop)
    val sizeFrac: Float,   // leaf length as a fraction of min(w,h)
    val colorIndex: Int,
    val spin: Float,       // tumble cycles per loop
    val spinPhase: Float,
    val bobAmp: Float,     // vertical bob amplitude (fraction of h)
    val bobFreq: Float,    // vertical bob cycles per loop
    val offset: Float,     // starting x offset 0..1
)

internal data class WindStreak(
    val y: Float,          // 0..1 vertical position
    val speed: Float,      // drift speed across the screen
    val baseLength: Float, // length as a fraction of width
    val offset: Float,
)

private val DefaultFlowerColors = listOf(
    Color(0xFFEC407A), Color(0xFFFFB74D), Color(0xFFAB47BC),
    Color(0xFFFF7043), Color(0xFFE53935), Color(0xFF42A5F5),
)

/** Greens plus a few autumn tones so the drifting leaves read as leaves. */
internal val DefaultLeafColors = listOf(
    Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
    Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFEF5350),
)

private const val STREAK_COUNT = 6

private fun buildFlowers(count: Int): List<Flower> {
    val rng = java.util.Random(20260710L)
    return List(count) {
        Flower(
            x = 0.03f + rng.nextFloat() * 0.94f,
            // Keep flowers in the lower ~62% so they read as ground cover.
            yBand = rng.nextFloat(),
            heightFrac = 0.018f + rng.nextFloat() * 0.016f,
            colorIndex = rng.nextInt(DefaultFlowerColors.size),
            phase = rng.nextFloat() * TAU,
            // 2–5 sway cycles per 6s loop → a lively flutter, not a slow pendulum.
            freq = 2f + rng.nextFloat() * 3f,
        )
    }
}

internal fun buildLeaves(count: Int): List<Leaf> {
    val rng = java.util.Random(909090L)
    return List(count) {
        Leaf(
            y = rng.nextFloat(),
            // Crosses ~¼–¾ of the screen per loop; the wind multiplier adds the whoosh.
            speed = 0.25f + rng.nextFloat() * 0.5f,
            sizeFrac = 0.010f + rng.nextFloat() * 0.012f,
            colorIndex = rng.nextInt(DefaultLeafColors.size),
            spin = 1f + rng.nextFloat() * 3f,
            spinPhase = rng.nextFloat() * TAU,
            bobAmp = 0.01f + rng.nextFloat() * 0.03f,
            bobFreq = 0.8f + rng.nextFloat() * 2f,
            offset = rng.nextFloat(),
        )
    }
}

internal fun buildStreaks(count: Int): List<WindStreak> {
    val rng = java.util.Random(131313L)
    return List(count) {
        WindStreak(
            y = 0.1f + rng.nextFloat() * 0.8f,
            speed = 0.5f + rng.nextFloat() * 0.5f,
            baseLength = 0.06f + rng.nextFloat() * 0.08f,
            offset = rng.nextFloat(),
        )
    }
}

// ---- Drawing ---------------------------------------------------------------

private const val PI = Math.PI.toFloat()
internal const val TAU = 2f * PI

/** Loop length for the wind phase. All cycle counts below are "per loop". */
internal const val WIND_LOOP_MS = 6_000
/** Gust and swell cycles per loop (loop = 6s): gust ≈ every 2s, swell ≈ every 6s. */
internal const val GUST_CYCLES = 3f
internal const val SWELL_CYCLES = 1f

/**
 * The shared wind envelope, given a phase `t` in 0..1 (one full loop = [WIND_LOOP_MS]).
 * Exposed so other scenes (e.g. the welcome screen) can drive identical wind behaviour.
 */
internal fun windStrengthAt(t: Float): Float {
    val gust = sin(t * TAU * GUST_CYCLES)
    val swell = sin(t * TAU * SWELL_CYCLES)
    return 0.30f + 0.70f * (0.5f + 0.5f * gust) * (0.5f + 0.5f * swell)
}

/** Maximum stem lean (radians) at full gust — about 32°. */
private const val MAX_LEAN_RAD = 0.55f

private fun DrawScope.drawFlower(
    f: Flower,
    t: Float,
    windStrength: Float,
    w: Float,
    h: Float,
    colors: List<Color>,
    stemColor: Color,
) {
    val bandTop = h * 0.38f
    val bandBottom = h * 0.98f
    val baseX = f.x * w
    val baseY = bandTop + f.yBand * (bandBottom - bandTop)
    val stemLen = f.heightFrac * minOf(w, h)
    val petalR = stemLen * 0.42f

    // Sway: a push in the wind direction plus a per-flower oscillation, scaled by the gust envelope.
    val sway = sin(t * 2f * PI * f.freq + f.phase)
    val angleRad = MAX_LEAN_RAD * windStrength * (0.65f + 0.35f * sway)
    val tipX = baseX + sin(angleRad) * stemLen
    val tipY = baseY - cos(angleRad) * stemLen

    // Stem: a short line that leans with the sway.
    drawLine(
        color = stemColor,
        start = Offset(baseX, baseY),
        end = Offset(tipX, tipY),
        strokeWidth = stemLen * 0.10f,
        cap = StrokeCap.Round,
    )

    // Head: five petals + a centre.
    val petalColor = colors[(f.colorIndex % colors.size).coerceAtLeast(0)]
    for (i in 0 until 5) {
        val a = (i / 5f) * 2f * PI + angleRad
        drawCircle(
            color = petalColor,
            radius = petalR,
            center = Offset(tipX + cos(a) * petalR, tipY + sin(a) * petalR),
        )
    }
    drawCircle(color = Color(0xFFFFCA28), radius = petalR * 0.7f, center = Offset(tipX, tipY))
}

internal fun DrawScope.drawLeaves(
    leaves: List<Leaf>,
    t: Float,
    windStrength: Float,
    w: Float,
    h: Float,
    colors: List<Color>,
) {
    val minDim = minOf(w, h)
    for (leaf in leaves) {
        // Wind multiplier makes leaves surge forward during gusts and dawdle during lulls.
        val windMul = 0.5f + windStrength * 1.6f
        val x = (((t * leaf.speed * windMul) + leaf.offset) % 1.25f - 0.12f) * w
        val y = leaf.y * h + sin(t * TAU * leaf.bobFreq + leaf.spinPhase) * leaf.bobAmp * h
        val len = leaf.sizeFrac * minDim
        val halfW = len * 0.5f
        val halfH = len * 0.28f
        val rotDeg = Math.toDegrees((t * TAU * leaf.spin + leaf.spinPhase).toDouble()).toFloat()
        val color = colors[(leaf.colorIndex % colors.size).coerceAtLeast(0)]

        rotate(degrees = rotDeg, pivot = Offset(x, y)) {
            // Leaf body as a slim oval, with a faint vein down the middle.
            drawOval(
                color = color,
                topLeft = Offset(x - halfW, y - halfH),
                size = Size(halfW * 2f, halfH * 2f),
            )
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(x - halfW, y),
                end = Offset(x + halfW, y),
                strokeWidth = halfH * 0.3f,
            )
        }
    }
}

internal fun DrawScope.drawWindStreaks(
    streaks: List<WindStreak>,
    t: Float,
    windStrength: Float,
    w: Float,
    h: Float,
    color: Color,
) {
    // Streaks lengthen and speed up with the gusts, then fade when the wind drops.
    val alpha = (windStrength * 0.8f).coerceIn(0f, 0.6f)
    if (alpha <= 0.01f) return
    for (s in streaks) {
        val x = (((t * s.speed * (0.5f + windStrength)) + s.offset) % 1.3f - 0.15f) * w
        val y = s.y * h
        val len = s.baseLength * (0.6f + windStrength) * w
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(x + len, y),
            strokeWidth = h * 0.004f,
            cap = StrokeCap.Round,
        )
    }
}
