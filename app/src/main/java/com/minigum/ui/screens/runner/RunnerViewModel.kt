package com.minigum.ui.screens.runner

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
class RunnerViewModel @Inject constructor(
) : ViewModel() {

    /** How the player must clear an obstacle in its lane. */
    enum class ObstacleKind { LOW, HIGH, FULL }

    /** One approaching hazard: [z] is depth (0 = at the player, larger = farther). */
    data class Obstacle(val z: Float, val lane: Int, val kind: ObstacleKind)

    data class GameState(
        val aspect: Float = DEFAULT_ASPECT,
        val obstacles: List<Obstacle> = emptyList(),
        /** Committed lane index in {-1, 0, 1}; collision uses this. */
        val targetLane: Int = 0,
        /** Rendered lane position, lerped toward [targetLane] for smooth movement. */
        val playerLane: Float = 0f,
        /** Player height above the ground (0 = grounded); positive while airborne. */
        val playerY: Float = 0f,
        val playerVy: Float = 0f,
        val isSliding: Boolean = false,
        val slideTimer: Float = 0f,
        /** Current forward speed in depth units/sec; ramps up with distance. */
        val speed: Float = START_SPEED,
        /** Distance travelled, in metres (also the score). */
        val metres: Float = 0f,
        val bestMetres: Int = 0,
        val lives: Int = START_LIVES,
        val spawnTimer: Float = FIRST_SPAWN_DELAY,
        val isRunning: Boolean = false,
        val isGameOver: Boolean = false,
        /** Seconds of invulnerability remaining after a hit. */
        val invulnRemaining: Float = 0f,
        /** Monotonic bump each time the player is hit, so the UI fires a one-shot flash/haptic. */
        val hitEventId: Int = 0,
    )

    private val _state = MutableStateFlow(GameState(bestMetres = 0))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * Set the playfield aspect ratio (width / height) once the screen measures.
     * The runner is portrait, so [aspect] is < 1; only stored to preserve best score
     * and reset cleanly across configuration changes.
     */
    fun configure(aspect: Float) {
        val current = _state.value
        if (aspect == current.aspect) return
        _state.value = current.copy(aspect = aspect)
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
        _state.value = GameState(aspect = current.aspect, bestMetres = current.bestMetres)
        start()
    }

    /** Swipe left/right shifts the committed lane by [delta] (-1 or +1). */
    fun moveLane(delta: Int) {
        _state.update { it.copy(targetLane = (it.targetLane + delta).coerceIn(-1, 1)) }
    }

    /** Swipe up or tap: jump, but only from the ground (no double jump). */
    fun jump() {
        _state.update {
            if (it.playerY <= 0f && !it.isSliding) it.copy(playerVy = JUMP_V) else it
        }
    }

    /** Swipe down: slide under HIGH obstacles, but only from the ground. */
    fun slide() {
        _state.update {
            if (it.playerY <= 0f && !it.isSliding) it.copy(isSliding = true, slideTimer = SLIDE_DURATION) else it
        }
    }

    private fun tick(dt: Float) {
        _state.update { current ->
            if (!current.isRunning || current.isGameOver) return@update current

            // Speed and distance ramp up over time — the core runner difficulty curve.
            val speed = (current.speed + RAMP * dt).coerceAtMost(MAX_SPEED)
            val metres = current.metres + speed * SCORE_FACTOR * dt
            val invuln = (current.invulnRemaining - dt).coerceAtLeast(0f)

            // Slide timer.
            val slideTimer = (current.slideTimer - dt).coerceAtLeast(0f)
            val isSliding = current.isSliding && slideTimer > 0f

            // Jump physics (only while airborne).
            var py = current.playerY
            var vy = current.playerVy
            if (py > 0f || vy > 0f) {
                vy -= GRAVITY * dt
                py += vy * dt
                if (py <= 0f) { py = 0f; vy = 0f }
            }

            // Lane position lerps toward the committed lane.
            val playerLane = current.playerLane +
                (current.targetLane - current.playerLane) * (LANE_LERP * dt).coerceAtMost(1f)

            // Spawn obstacles at intervals scaled by speed (keeps z-spacing roughly constant).
            val spawnTimer = current.spawnTimer - dt
            val withSpawn = if (spawnTimer <= 0f) {
                current.obstacles + spawnWave(metres)
            } else {
                current.obstacles
            }
            val nextSpawnDelay = spawnInterval(speed)

            var s = current.copy(
                speed = speed,
                metres = metres,
                invulnRemaining = invuln,
                slideTimer = slideTimer,
                isSliding = isSliding,
                playerY = py,
                playerVy = vy,
                playerLane = playerLane,
                obstacles = withSpawn,
                spawnTimer = if (spawnTimer <= 0f) nextSpawnDelay else spawnTimer,
            )

            // Advance obstacles, resolve collisions, cull the ones behind the player.
            s = stepObstacles(s, dt)
            if (s.lives <= 0) return@update endGame(s)

            s
        }
    }

    /** Move obstacles forward, detect hits as they cross the player plane, cull passed ones. */
    private fun stepObstacles(s: GameState, dt: Float): GameState {
        val newObstacles = mutableListOf<Obstacle>()
        var lives = s.lives
        var invuln = s.invulnRemaining
        var hitId = s.hitEventId

        for (o in s.obstacles) {
            val nz = o.z - s.speed * dt
            val justReachedPlayer = o.z > COLLIDE_Z && nz <= COLLIDE_Z
            if (justReachedPlayer && o.lane == s.targetLane && invuln <= 0f) {
                val hit = when (o.kind) {
                    ObstacleKind.LOW -> s.playerY < LOW_CLEAR_Y   // must be jumping
                    ObstacleKind.HIGH -> !s.isSliding            // must be sliding
                    ObstacleKind.FULL -> true                     // must have changed lane
                }
                if (hit) {
                    lives -= 1
                    invuln = INVULN_S
                    hitId += 1
                    continue // consumed by the hit; don't keep it
                }
            }
            if (nz >= CULL_Z) {
                newObstacles.add(o.copy(z = nz))
            }
        }

        return if (lives == s.lives && invuln == s.invulnRemaining) {
            s.copy(obstacles = newObstacles)
        } else {
            s.copy(obstacles = newObstacles, lives = lives, invulnRemaining = invuln, hitEventId = hitId)
        }
    }

    /** Build one spawn: a primary obstacle, occasionally a second in a different lane. */
    private fun spawnWave(metres: Float): List<Obstacle> {
        val first = Obstacle(SPAWN_Z, randomLane(), randomKind(metres))
        // 25% chance of a second obstacle, in a different lane, slightly deeper.
        if (Random.nextFloat() < 0.25f) {
            val otherLane = ((first.lane - 1).coerceIn(-1, 1)..(first.lane + 1).coerceIn(-1, 1))
                .filter { it != first.lane }
                .let { it.random() }
            // Never two FULL obstacles at the same depth in different lanes that could trap —
            // with at most two obstacles there is always a free lane, so this stays solvable.
            val secondKind = if (first.kind == ObstacleKind.FULL) ObstacleKind.LOW else randomKind(metres)
            return listOf(first, Obstacle(SPAWN_Z + 0.12f, otherLane, secondKind))
        }
        return listOf(first)
    }

    /** Time until the next spawn, chosen so obstacles keep roughly fixed z-spacing. */
    private fun spawnInterval(speed: Float): Float =
        (SPAWN_SPACING / speed) * (0.85f + Random.nextFloat() * 0.3f)

    /** Obstacle mix shifts from jump-only toward slides and lane changes as you progress. */
    private fun randomKind(metres: Float): ObstacleKind {
        val r = Random.nextFloat()
        return when {
            metres < 120f -> ObstacleKind.LOW
            metres < 300f -> when {
                r < 0.6f -> ObstacleKind.LOW
                r < 0.85f -> ObstacleKind.HIGH
                else -> ObstacleKind.FULL
            }
            else -> when {
                r < 0.45f -> ObstacleKind.LOW
                r < 0.75f -> ObstacleKind.HIGH
                else -> ObstacleKind.FULL
            }
        }
    }

    private fun randomLane(): Int = Random.nextInt(-1, 2) // -1, 0, 1

    private fun endGame(current: GameState): GameState {
        tickJob?.cancel()
        return current.copy(
            isRunning = false,
            isGameOver = true,
            bestMetres = maxOf(current.bestMetres, current.metres.toInt()),
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

    companion object {
        const val START_LIVES = 3

        // Player physics.
        const val JUMP_V = 0.5f          // upward launch velocity (heights/sec)
        const val GRAVITY = 1.3f         // heights/sec^2
        const val LOW_CLEAR_Y = 0.04f    // jump height needed to clear a LOW obstacle
        const val SLIDE_DURATION = 0.55f // seconds a slide lasts
        const val LANE_LERP = 11f        // how fast rendered lane catches up to the committed lane

        // Speed / scoring.
        const val START_SPEED = 0.45f    // depth units/sec
        const val MAX_SPEED = 0.95f
        const val RAMP = 0.012f          // speed gain per second
        const val SCORE_FACTOR = 100f    // metres per depth unit

        // Spawning.
        const val SPAWN_Z = 1.05f        // depth at which obstacles appear
        const val SPAWN_SPACING = 0.55f  // desired z-gap between waves
        const val FIRST_SPAWN_DELAY = 1.2f
        const val COLLIDE_Z = 0.06f      // obstacle is "at the player" once z <= this
        const val CULL_Z = -0.15f        // remove once safely behind the player

        const val INVULN_S = 1.3f

        private const val DEFAULT_ASPECT = 9f / 16f
        private const val TICK_MS = 16L
        private const val MAX_DT = 0.033f
    }
}
