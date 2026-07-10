package com.minigum.ui.screens.balloon

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

/**
 * Balloon Escape — a vertical climber where the microphone is the throttle.
 *
 * Blow (any sound) to rise, stay silent to fall. Swipe left/right to drift horizontally (with
 * screen-edge wrap-around). The world scrolls up forever: land on platforms to rest, ride springs
 * for a boost, avoid birds and spikes. The camera follows the balloon both up AND down (within a
 * screen band), and platforms below are kept, so falling drops you onto a lower platform to rest —
 * only a fall past the very bottom resets you to the start.
 *
 * Real-time dt loop + StateFlow follow the Flappy/Runner convention; the mic is sampled by
 * [MicEngine] and normalized through a per-device silence/blow calibration.
 */
@HiltViewModel
class BalloonViewModel @Inject constructor() : ViewModel() {

    enum class Calibration { NONE, SILENCE, BLOW, DONE, FAILED }

    enum class PlatformKind { NORMAL, SPRING }

    data class Platform(
        val worldY: Float,
        val cx: Float,
        val halfWidth: Float,
        val kind: PlatformKind = PlatformKind.NORMAL,
        val spikeStart: Float = 0f,
        val spikeSpan: Float = 0f,
        val bottomSpikes: Boolean = false,
    )

    data class Bird(var x: Float, val worldY: Float, val vx: Float, val r: Float)

    data class Coin(var worldY: Float, val x: Float, var taken: Boolean = false)

    data class GameState(
        val balloonWorldY: Float = 0f,
        val balloonVy: Float = 0f,
        val balloonX: Float = 0.5f,
        val balloonVx: Float = 0f,
        val cameraWorldY: Float = 0f,
        val intensity: Float = 0f,
        val maxHeight: Float = 0f,
        val platforms: List<Platform> = emptyList(),
        val birds: List<Bird> = emptyList(),
        val coins: List<Coin> = emptyList(),
        val coinsCollected: Int = 0,
        val isRunning: Boolean = false,
        val isGameOver: Boolean = false,
        val calibration: Calibration = Calibration.NONE,
        val bestHeight: Int = 0,
    )

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val mic = MicEngine()
    private var tickJob: Job? = null
    private var calibJob: Job? = null

    private var floor = 0f
    private var ceiling = 0f
    private var genTopY = 0f
    private var platformCount = 0

    // Balloon is a true circle: radius is a fraction of HEIGHT, and the x half-extent is derived
    // from the aspect ratio so both equal the same pixel size. Set via [configure].
    private var aspect = 9f / 16f
    private var halfX = BALLOON_R
    private var halfY = BALLOON_R

    /** Pass the screen aspect (width/height) so the balloon collision can stay perfectly round. */
    fun configure(aspect: Float) {
        if (aspect > 0f) {
            this.aspect = aspect
            halfX = BALLOON_R / aspect
            halfY = BALLOON_R
        }
    }

    // ---- Lifecycle / calibration -----------------------------------------

    fun startWithMic() {
        mic.start()
        calibJob?.cancel()
        calibJob = viewModelScope.launch { runCalibration() }
    }

    private suspend fun runCalibration() {
        _state.update { it.copy(calibration = Calibration.SILENCE) }
        floor = sampleMaxOver(1.6f) * 1.2f
        _state.update { it.copy(calibration = Calibration.BLOW) }
        val blow = sampleMaxOver(1.8f)
        ceiling = if (blow > floor * 3f) blow else floor * 4f
        if (floor <= 0f && ceiling <= 0f) {
            _state.update { it.copy(calibration = Calibration.FAILED) }
            return
        }
        beginRun()
        start()
    }

    private suspend fun sampleMaxOver(seconds: Float): Float {
        var max = 0f
        val end = System.nanoTime() + (seconds * 1e9f).toLong()
        while (System.nanoTime() < end) {
            mic.ensureActive()
            val a = mic.amplitude
            if (a > max) max = a
            delay(40)
        }
        return max
    }

    private fun beginRun() {
        mic.ensureActive()
        val best = _state.value.bestHeight
        platformCount = 0
        val platforms = ArrayList<Platform>()
        val start = Platform(worldY = -0.05f, cx = 0.5f, halfWidth = 0.4f)
        platforms.add(start)
        genTopY = start.worldY
        platformCount = 1
        while (genTopY < 1.2f) {
            genTopY += randSpacing(0f)
            platforms.add(makePlatform(genTopY, safe = platformCount < 4, diff = 0f))
            platformCount++
        }
        _state.value = GameState(
            balloonWorldY = 0f,
            cameraWorldY = 0f,
            platforms = platforms,
            calibration = Calibration.DONE,
            bestHeight = best,
        )
    }

    fun start() {
        val current = _state.value
        if (current.isGameOver || current.isRunning || current.calibration != Calibration.DONE) return
        _state.update { it.copy(isRunning = true) }
        startLoop()
    }

    fun pause() {
        tickJob?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun restart() {
        tickJob?.cancel()
        beginRun()
        start()
    }

    fun swipe(dxFrac: Float) {
        _state.update {
            it.copy(balloonVx = (it.balloonVx + dxFrac * HORIZ_IMPULSE).coerceIn(-MAX_VX, MAX_VX))
        }
    }

    // ---- Game loop --------------------------------------------------------

    private fun startLoop() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive) {
                val s = _state.value
                if (!s.isRunning || s.isGameOver) break
                delay(16)
                val now = System.nanoTime()
                var dt = ((now - last) / 1_000_000_000.0).toFloat()
                last = now
                dt = dt.coerceIn(0f, MAX_DT)
                tick(dt)
            }
        }
    }

    private fun tick(dt: Float) {
        mic.ensureActive() // recover the mic if it ever goes stale/dead
        val s = _state.value

        val raw = mic.amplitude
        val norm = if (ceiling > floor) ((raw - floor) / (ceiling - floor)).coerceIn(0f, 1f) else 0f
        val intensity = s.intensity + (norm - s.intensity) * 0.35f

        // Horizontal: friction + integrate; blocked at the screen edges (no wrap-through).
        var vx = s.balloonVx - s.balloonVx * minOf(1f, HORIZ_FRICTION * dt)
        var balloonX = s.balloonX + vx * dt
        val minX = halfX
        val maxX = 1f - halfX
        if (balloonX < minX) { balloonX = minX; if (vx < 0f) vx = 0f }
        if (balloonX > maxX) { balloonX = maxX; if (vx > 0f) vx = 0f }

        var vy = s.balloonVy - GRAVITY * dt + LIFT * intensity * dt
        val prevY = s.balloonWorldY
        var by = prevY + vy * dt

        val platforms = s.platforms.toMutableList()
        val birds = s.birds.toMutableList()
        val coins = s.coins.toMutableList()
        var coinsCollected = s.coinsCollected
        var dead = false

        // --- Solid AABB collision ---
        for (p in platforms) {
            val left = p.cx - p.halfWidth
            val right = p.cx + p.halfWidth
            val top = p.worldY
            val bottom = p.worldY - THICK
            val bLeft = balloonX - halfX
            val bRight = balloonX + halfX
            val bTop = by + halfY
            val bBottom = by - halfY

            val ox = minOf(bRight, right) - maxOf(bLeft, left)
            val oy = minOf(bTop, top) - maxOf(bBottom, bottom)
            if (ox <= 0f || oy <= 0f) continue

            if (ox < oy) {
                if (balloonX < p.cx) balloonX -= ox else balloonX += ox
                vx = 0f
            } else {
                val midY = p.worldY - THICK / 2f
                if (by > midY) {
                    if (inTopSpikeZone(p, balloonX)) { dead = true; break }
                    by = top + halfY
                    vy = if (p.kind == PlatformKind.SPRING && vy < 0f) SPRING_VY else 0f
                } else {
                    if (p.bottomSpikes) { dead = true; break }
                    by = bottom - halfY
                    if (vy > 0f) vy = 0f
                }
            }
        }
        if (dead) return endGame()

        // --- Camera: soft follow in BOTH directions (balloon may drift within a screen band). ---
        var camera = s.cameraWorldY
        val frac = 0.45f - (by - camera)
        if (frac > 0.62f) camera = by + 0.17f        // balloon fell low → move camera down
        else if (frac < 0.33f) camera = by - 0.12f   // balloon rose high → move camera up
        val maxHeight = maxOf(s.maxHeight, by)

        // --- Generate above as we climb. ---
        val diff = difficulty(by)
        while (genTopY < camera + 1.2f) {
            genTopY += randSpacing(diff)
            platforms.add(makePlatform(genTopY, safe = false, diff = diff))
            platformCount++
            if (rng().nextFloat() < 0.5f) coins.add(Coin(worldY = genTopY + 0.06f, x = rng().nextFloat() * 0.8f + 0.1f))
            if (diff > 0.2f && rng().nextFloat() < 0.3f + diff * 0.2f) {
                val fromLeft = rng().nextBoolean()
                birds.add(Bird(x = if (fromLeft) -0.1f else 1.1f, worldY = genTopY - randSpacing(diff) * 0.5f, vx = (if (fromLeft) 1f else -1f) * (0.12f + diff * 0.1f), r = 0.03f))
            }
        }

        for (b in birds) b.x += b.vx * dt

        // Keep platforms (so a fall lands below); only cap memory by dropping the very lowest.
        if (platforms.size > MAX_PLATFORMS) {
            val lowest = platforms.filter { it.worldY > 0f }.minByOrNull { it.worldY }
            if (lowest != null) platforms.remove(lowest)
        }
        val cullBelow = camera - 1.2f
        birds.removeAll { it.worldY < cullBelow || it.x < -0.3f || it.x > 1.3f }
        coins.removeAll { it.worldY < cullBelow }

        for (c in coins) {
            if (!c.taken && kotlin.math.abs(c.worldY - by) < halfY + 0.03f && kotlin.math.abs(c.x - balloonX) < halfX + 0.03f) {
                c.taken = true
                coinsCollected++
            }
        }

        for (b in birds) {
            val dx = b.x - balloonX
            val dy = b.worldY - by
            val rr = b.r + halfX
            if (dx * dx + dy * dy < rr * rr) return endGame()
        }

        // Fell past the very bottom (shouldn't happen normally — platforms below catch you).
        if (by < -0.55f) return fallReset()

        _state.value = s.copy(
            balloonWorldY = by,
            balloonVy = vy,
            balloonX = balloonX,
            balloonVx = vx,
            cameraWorldY = camera,
            intensity = intensity,
            maxHeight = maxHeight,
            platforms = platforms,
            birds = birds,
            coins = coins,
            coinsCollected = coinsCollected,
        )
    }

    private fun inTopSpikeZone(p: Platform, x: Float): Boolean {
        if (p.spikeSpan <= 0f) return false
        val left = p.cx - p.halfWidth
        val width = p.halfWidth * 2f
        val zoneStart = left + p.spikeStart * width
        val zoneEnd = left + (p.spikeStart + p.spikeSpan) * width
        return x in zoneStart..zoneEnd
    }

    private fun endGame() {
        tickJob?.cancel()
        val metres = (_state.value.maxHeight * METRES_PER_SCREEN).toInt()
        _state.update {
            it.copy(isRunning = false, isGameOver = true, bestHeight = maxOf(it.bestHeight, metres))
        }
    }

    /** Fell past everything — respawn at the start, keeping best height and coins. */
    private fun fallReset() {
        tickJob?.cancel()
        val keep = _state.value
        platformCount = 0
        val platforms = ArrayList<Platform>()
        val start = Platform(worldY = -0.05f, cx = 0.5f, halfWidth = 0.4f)
        platforms.add(start)
        genTopY = start.worldY
        platformCount = 1
        while (genTopY < 1.2f) {
            genTopY += randSpacing(0f)
            platforms.add(makePlatform(genTopY, safe = platformCount < 4, diff = 0f))
            platformCount++
        }
        _state.value = keep.copy(
            balloonWorldY = 0f, balloonVy = 0f, balloonX = 0.5f, balloonVx = 0f,
            cameraWorldY = 0f, intensity = 0f, platforms = platforms,
            birds = emptyList(), coins = emptyList(), isRunning = false,
        )
        start()
    }

    // --- Procedural generation --------------------------------------------

    private fun makePlatform(y: Float, safe: Boolean, diff: Float): Platform {
        val halfWidth = 0.07f + rng().nextFloat() * 0.11f
        // Centre can sit anywhere across the full width, including right at the edges.
        val cx = halfWidth + rng().nextFloat() * (1f - 2 * halfWidth)
        val kind = if (!safe && rng().nextFloat() < 0.13f) PlatformKind.SPRING else PlatformKind.NORMAL
        var spikeSpan = 0f
        var spikeStart = 0f
        var bottomSpikes = false
        if (!safe) {
            if (rng().nextFloat() < 0.33f + diff * 0.12f) {
                spikeSpan = (0.3f + rng().nextFloat() * 0.4f)
                spikeStart = rng().nextFloat() * (1f - spikeSpan)
            }
            if (kind == PlatformKind.NORMAL && rng().nextFloat() < 0.22f + diff * 0.1f) bottomSpikes = true
        }
        return Platform(y, cx, halfWidth, kind, spikeStart, spikeSpan, bottomSpikes)
    }

    private fun randSpacing(diff: Float): Float = (0.30f + rng().nextFloat() * 0.30f + diff * 0.04f).coerceIn(0.30f, 0.66f)

    private fun difficulty(height: Float): Float = (height * 0.12f).coerceIn(0f, 1f)

    private val random = java.util.Random()

    private fun rng(): java.util.Random = random

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
        calibJob?.cancel()
        mic.stop()
    }

    companion object {
        private const val MAX_DT = 0.05f
        private const val GRAVITY = 1.2f
        private const val LIFT = 2.9f
        private const val SPRING_VY = 1.8f
        private const val THICK = 0.03f
        private const val METRES_PER_SCREEN = 10f
        private const val HORIZ_FRICTION = 3.5f
        private const val HORIZ_IMPULSE = 14f
        private const val MAX_VX = 1.4f
        private const val MAX_PLATFORMS = 400
        const val BALLOON_R = 0.035f
    }
}
