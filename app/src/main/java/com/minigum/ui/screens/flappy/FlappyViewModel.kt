package com.minigum.ui.screens.flappy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class FlappyViewModel @Inject constructor(
) : ViewModel() {

    /** A pipe pair: one gap the bird must fly through, centred at [gapCenter]. */
    data class Pipe(val x: Float, val gapCenter: Float, val scored: Boolean)

    data class GameState(
        val aspect: Float = DEFAULT_ASPECT,
        val birdX: Float = DEFAULT_ASPECT * BIRD_X_FRAC,
        val birdY: Float = 0.5f,
        val birdVy: Float = 0f,
        val pipes: List<Pipe> = emptyList(),
        val pipeW: Float = DEFAULT_ASPECT * PIPE_W_FRAC,
        val speed: Float = DEFAULT_ASPECT * SPEED_FRAC,
        val score: Int = 0,
        val bestScore: Int = 0,
        val isRunning: Boolean = false,
        val isGameOver: Boolean = false,
        /** Horizontal scroll offset for the ground/clouds, advanced each frame. */
        val scroll: Float = 0f,
    )

    private val _state = MutableStateFlow(GameState(bestScore = 0))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * Set the playfield aspect ratio (width / height) once the screen measures.
     * Resets the game to fit while preserving the best score. Physics run in a
     * normalized field where height = 1 and width = [aspect] (portrait).
     */
    fun configure(aspect: Float) {
        val current = _state.value
        if (aspect == current.aspect) return
        tickJob?.cancel()
        _state.value = initialState(aspect, bestScore = current.bestScore)
    }

    fun start() {
        val current = _state.value
        if (current.isGameOver || current.isRunning) return
        _state.update { it.copy(isRunning = true) }
        startLoop()
    }

    fun pause() {
        tickJob?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun restart() {
        tickJob?.cancel()
        val current = _state.value
        _state.value = initialState(current.aspect, bestScore = current.bestScore)
        start()
    }

    /** Tap anywhere: an upward impulse (negative velocity, since +y is down). */
    fun flap() {
        _state.update { it.copy(birdVy = -FLAP_V) }
    }

    private fun tick(dt: Float) {
        _state.update { current ->
            if (!current.isRunning || current.isGameOver) return@update current

            // Gravity + integrate.
            var vy = current.birdVy + GRAVITY * dt
            var y = current.birdY + vy * dt

            // Ceiling: clamp without killing (classic Flappy lets you bonk the top).
            if (y - BIRD_R < 0f) {
                y = BIRD_R
                vy = 0f
            }
            // Ground: crash.
            if (y + BIRD_R >= GROUND_Y) {
                return@update endGame(current.copy(birdY = GROUND_Y - BIRD_R, birdVy = 0f, scroll = current.scroll + current.speed * dt))
            }

            val scroll = current.scroll + current.speed * dt

            // Scroll pipes and spawn new ones at a fixed horizontal spacing.
            val moved = current.pipes.map { it.copy(x = it.x - current.speed * dt) }
            val spawned = if (moved.isEmpty() || moved.last().x < current.aspect - SPAWN_GAP_FRAC * current.aspect) {
                moved + Pipe(current.aspect + current.pipeW, randomGapCenter(current.score), scored = false)
            } else {
                moved
            }
            // Guard against runaway spawns if dt spikes: cap pipe count.
            val pipes = spawned.takeLast(MAX_PIPES)

            // Score pipes the bird has passed, and cull the ones off the left edge.
            var score = current.score
            val kept = mutableListOf<Pipe>()
            for (p in pipes) {
                val passed = !p.scored && p.x + current.pipeW < current.birdX
                val pip = if (passed) { score += 1; p.copy(scored = true) } else p
                if (pip.x + current.pipeW > 0f) kept.add(pip)
            }

            var s = current.copy(
                birdY = y,
                birdVy = vy,
                pipes = kept,
                score = score,
                scroll = scroll,
            )

            // Collision with any pipe's top or bottom segment.
            if (hitsPipe(s)) return@update endGame(s)

            s
        }
    }

    /** Circle (bird) vs the top and bottom rects of every pipe. */
    private fun hitsPipe(s: GameState): Boolean {
        val bx = s.birdX
        val by = s.birdY
        for (p in s.pipes) {
            if (bx + BIRD_R < p.x || bx - BIRD_R > p.x + s.pipeW) continue
            val gapTop = p.gapCenter - gapHeight(s.score) / 2f
            val gapBottom = p.gapCenter + gapHeight(s.score) / 2f
            // Top segment spans [0, gapTop]; bottom spans [gapBottom, GROUND_Y].
            val nearestTopY = gapTop
            val nearestBottomY = gapBottom
            // Closest point on the top rect to the bird centre, then on the bottom rect.
            if (circleRectOverlap(bx, by, BIRD_R, p.x, 0f, s.pipeW, nearestTopY) ||
                circleRectOverlap(bx, by, BIRD_R, p.x, nearestBottomY, s.pipeW, GROUND_Y - nearestBottomY)
            ) {
                return true
            }
        }
        return false
    }

    private fun circleRectOverlap(cx: Float, cy: Float, r: Float, rx: Float, ry: Float, rw: Float, rh: Float): Boolean {
        val nearestX = cx.coerceIn(rx, rx + rw)
        val nearestY = cy.coerceIn(ry, ry + rh)
        val dx = cx - nearestX
        val dy = cy - nearestY
        return dx * dx + dy * dy < r * r
    }

    private fun endGame(current: GameState): GameState {
        tickJob?.cancel()
        return current.copy(
            isRunning = false,
            isGameOver = true,
            bestScore = maxOf(current.bestScore, current.score),
        )
    }

    private fun startLoop() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive) {
                val state = _state.value
                if (!state.isRunning || state.isGameOver) break
                delay(TICK_MS)
                val now = System.nanoTime()
                var dt = ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                dt = dt.coerceIn(0f, MAX_DT)
                tick(dt)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }

    private fun initialState(aspect: Float, bestScore: Int): GameState = GameState(
        aspect = aspect,
        birdX = aspect * BIRD_X_FRAC,
        birdY = 0.5f,
        pipeW = aspect * PIPE_W_FRAC,
        speed = aspect * SPEED_FRAC,
        bestScore = bestScore,
    )

    /** Gap shrinks slightly with score for a gentle difficulty ramp, floored. */
    private fun gapHeight(score: Int): Float = (BASE_GAP - score * GAP_SHRINK).coerceAtLeast(MIN_GAP)

    private fun randomGapCenter(score: Int): Float {
        val half = gapHeight(score) / 2f
        return Random.nextFloat() * (GROUND_Y - half - (CEILING + half)) + (CEILING + half)
    }

    companion object {
        // Exposed for rendering.
        const val BIRD_R = 0.035f
        const val GROUND_Y = 0.9f

        private const val DEFAULT_ASPECT = 16f / 9f
        private const val TICK_MS = 16L
        private const val MAX_DT = 0.033f

        private const val BIRD_X_FRAC = 0.3f   // bird sits in the left third
        private const val PIPE_W_FRAC = 0.16f  // pipe width as a fraction of field width
        private const val SPEED_FRAC = 0.42f   // scroll speed as a fraction of field width per sec
        private const val SPAWN_GAP_FRAC = 0.6f // horizontal spacing between pipes
        private const val MAX_PIPES = 6

        private const val GRAVITY = 2.6f       // heights/sec^2
        private const val FLAP_V = 0.8f        // upward impulse (heights/sec)
        private const val CEILING = 0.04f
        private const val BASE_GAP = 0.3f
        private const val MIN_GAP = 0.2f
        private const val GAP_SHRINK = 0.004f
    }
}
