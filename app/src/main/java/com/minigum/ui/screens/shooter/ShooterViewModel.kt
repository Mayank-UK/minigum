package com.minigum.ui.screens.shooter

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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@HiltViewModel
class ShooterViewModel @Inject constructor(
) : ViewModel() {

    enum class Difficulty(val marchMult: Float, val bombRate: Float, val label: String) {
        EASY(0.7f, 0.2f, "Easy"),
        NORMAL(1.0f, 0.45f, "Normal"),
        HARD(1.4f, 0.85f, "Hard"),
    }

    enum class PowerKind { RAPID, SPREAD, LIFE }

    /** A player bullet; straight shots have vx = 0, spread shots carry an angle. */
    data class Bullet(val x: Float, val y: Float, val vx: Float, val vy: Float)

    /** An alien bomb falling toward the player. */
    data class Bomb(val x: Float, val y: Float)

    /** A beneficial capsule dropped by a killed alien; falls like a bomb. */
    data class PowerUp(val kind: PowerKind, val x: Float, val y: Float)

    /** One cell of the alien grid; its world position is derived from the group origin. */
    data class Alien(val col: Int, val row: Int, val hp: Int, val maxHp: Int, val alive: Boolean)

    /** A short-lived visual burst played when an alien dies. */
    data class Explosion(val x: Float, val y: Float, val remaining: Float)

    data class GameState(
        val aspect: Float = DEFAULT_ASPECT,
        val difficulty: Difficulty = Difficulty.NORMAL,
        val aliens: List<Alien> = emptyList(),
        val groupX: Float = 0f,
        val groupY: Float = START_GY,
        /** March direction: +1 right, -1 left. */
        val groupDir: Int = 1,
        val bullets: List<Bullet> = emptyList(),
        val bombs: List<Bomb> = emptyList(),
        val powerUps: List<PowerUp> = emptyList(),
        val explosions: List<Explosion> = emptyList(),
        val playerX: Float = DEFAULT_ASPECT / 2f,
        val playerTargetX: Float = DEFAULT_ASPECT / 2f,
        val fireTimer: Float = 0f,
        val score: Int = 0,
        val bestScore: Int = 0,
        val lives: Int = START_LIVES,
        val wave: Int = 1,
        /** Per-wave march speed multiplier; grows each cleared wave. */
        val waveSpeedMul: Float = 1f,
        // Active timed buffs (seconds remaining).
        val rapidRemaining: Float = 0f,
        val spreadRemaining: Float = 0f,
        // Combo: consecutive kills within COMBO_WINDOW seconds.
        val combo: Int = 0,
        val comboTimer: Float = 0f,
        // Layout derived from aspect (field-width units for x, heights for y).
        val cellW: Float = 0f,
        val alienHalfW: Float = 0f,
        val alienHalfH: Float = 0f,
        // Ship half-width tracks the field width (the narrow axis in portrait) so
        // the ship stays a small, consistent fraction of the screen at any aspect.
        val playerHalfW: Float = DEFAULT_ASPECT * PLAYER_HALF_W_FRAC,
        val isRunning: Boolean = false,
        val isGameOver: Boolean = false,
        /** Seconds of invulnerability remaining after a bomb hit. */
        val invulnRemaining: Float = 0f,
        /** Monotonic bump each time the player is hit, so the UI fires a one-shot flash/haptic. */
        val hitEventId: Int = 0,
    )

    private val _state = MutableStateFlow(initialState(DEFAULT_ASPECT, Difficulty.NORMAL, bestScore = 0))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * Set the playfield aspect ratio (width / height) once the screen measures.
     * Resets the game to fit while preserving the best score and difficulty.
     * Physics run in a normalized field where height = 1 and width = [aspect],
     * mirroring Pong so the playfield always fills the screen.
     */
    fun configure(aspect: Float) {
        val current = _state.value
        if (aspect == current.aspect) return
        tickJob?.cancel()
        _state.value = initialState(aspect, current.difficulty, bestScore = current.bestScore)
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
        _state.value = initialState(current.aspect, current.difficulty, bestScore = current.bestScore)
        start()
    }

    /** Player ship chases the finger; x is a fraction of field width in [0, 1]. */
    fun setPlayerTarget(xFraction: Float) {
        _state.update {
            it.copy(playerTargetX = (xFraction * it.aspect).coerceIn(it.playerHalfW, it.aspect - it.playerHalfW))
        }
    }

    fun setDifficulty(difficulty: Difficulty) {
        _state.update { it.copy(difficulty = difficulty) }
    }

    private fun tick(dt: Float) {
        _state.update { current ->
            if (!current.isRunning || current.isGameOver) return@update current
            if (current.aliens.none { it.alive }) return@update spawnNextWave(current)

            // Tick timed buffs and the combo window.
            var s = current.copy(
                invulnRemaining = (current.invulnRemaining - dt).coerceAtLeast(0f),
                rapidRemaining = (current.rapidRemaining - dt).coerceAtLeast(0f),
                spreadRemaining = (current.spreadRemaining - dt).coerceAtLeast(0f),
            )
            if (s.combo > 0) {
                val ct = s.comboTimer - dt
                s = if (ct <= 0f) s.copy(combo = 0, comboTimer = 0f) else s.copy(comboTimer = ct)
            }

            // Player ship tracks the finger target instantly, clamped to the field.
            s = s.copy(playerX = s.playerTargetX.coerceIn(s.playerHalfW, s.aspect - s.playerHalfW))

            // Auto-fire: rate depends on the rapid buff; spread buff fires three bullets.
            s = stepFire(s, dt)

            // March the alien block; reverse + drop at the walls.
            s = marchAliens(s, dt)

            // Aliens landing on the player line ends the game.
            if (lowestAlienBottom(s) >= PLAYER_Y - PLAYER_HALF_H) {
                return@update endGame(s)
            }

            // Resolve player bullets vs aliens (handles hp, scoring, combo, drops, explosions).
            s = resolveBullets(s)

            // Aliens drop bombs and bombs fall / hit the player.
            s = stepBombs(spawnBombs(s, dt), dt)

            // Power-ups fall and are collected by the player.
            s = stepPowerUps(s, dt)

            // Advance and cull explosion effects.
            s = s.copy(
                explosions = s.explosions
                    .map { it.copy(remaining = it.remaining - dt) }
                    .filter { it.remaining > 0f },
            )

            // Cleared wave check (after shots may have killed the last alien).
            if (s.aliens.none { it.alive }) {
                return@update spawnNextWave(s)
            }

            s
        }
    }

    /** Accumulate fire time and spawn bullets on each interval (single or triple). */
    private fun stepFire(s: GameState, dt: Float): GameState {
        val interval = if (s.rapidRemaining > 0f) RAPID_INTERVAL_S else FIRE_INTERVAL_S
        val fireTimer = s.fireTimer + dt
        if (fireTimer < interval) {
            return s.copy(fireTimer = fireTimer)
        }
        val muzzleY = PLAYER_Y - PLAYER_HALF_H
        val newBullets = if (s.spreadRemaining > 0f) {
            val vx = sin(SPREAD_ANGLE) * BULLET_SPEED
            val vy = -cos(SPREAD_ANGLE) * BULLET_SPEED
            listOf(
                Bullet(s.playerX, muzzleY, 0f, -BULLET_SPEED),
                Bullet(s.playerX, muzzleY, -vx, vy),
                Bullet(s.playerX, muzzleY, vx, vy),
            )
        } else {
            listOf(Bullet(s.playerX, muzzleY, 0f, -BULLET_SPEED))
        }
        val bullets = (s.bullets + newBullets)
            .map { it.copy(x = it.x + it.vx * dt, y = it.y + it.vy * dt) }
            .filter { it.y + BULLET_HALF_H > 0f && it.x > -0.1f && it.x < s.aspect + 0.1f }
        return s.copy(fireTimer = fireTimer - interval, bullets = bullets)
    }

    /** Move the alien block horizontally; bounce off the side walls and step down. */
    private fun marchAliens(s: GameState, dt: Float): GameState {
        val aliveCols = s.aliens.filter { it.alive }.map { it.col }
        if (aliveCols.isEmpty()) return s
        val minCol = aliveCols.min()
        val maxCol = aliveCols.max()

        val aliveFraction = aliveCols.size.toFloat() / s.aliens.size.toFloat()
        val ramp = 1f + (1f - aliveFraction) * RAMP
        val speed = MARCH_BASE_W * s.aspect * s.difficulty.marchMult * s.waveSpeedMul * ramp
        val nextGx = s.groupX + s.groupDir * speed * dt
        val nextLeft = nextGx + minCol * s.cellW - s.alienHalfW
        val nextRight = nextGx + maxCol * s.cellW + s.alienHalfW

        // If the next step would breach a wall, drop down a row and reverse instead.
        return if (
            (s.groupDir > 0 && nextRight >= s.aspect - WALL_MARGIN) ||
            (s.groupDir < 0 && nextLeft <= WALL_MARGIN)
        ) {
            s.copy(groupY = s.groupY + DROP_STEP, groupDir = -s.groupDir)
        } else {
            s.copy(groupX = nextGx)
        }
    }

    /** Returns the lowest (largest y) bottom edge among alive aliens. */
    private fun lowestAlienBottom(s: GameState): Float {
        var lowest = 0f
        for (a in s.aliens) {
            if (!a.alive) continue
            val bottom = s.groupY + a.row * ALIEN_CELL_H + s.alienHalfH
            if (bottom > lowest) lowest = bottom
        }
        return lowest
    }

    /** Bullet–alien collisions: damage aliens, kill at 0 hp, score with combo, drop loot, boom. */
    private fun resolveBullets(s: GameState): GameState {
        val aliens = s.aliens.toMutableList()
        val remainingBullets = mutableListOf<Bullet>()
        var scoreGain = 0
        var combo = s.combo
        var comboTimer = s.comboTimer
        val explosions = s.explosions.toMutableList()
        val powerUps = s.powerUps.toMutableList()

        for (bullet in s.bullets) {
            var hitIndex = -1
            for (i in aliens.indices) {
                val a = aliens[i]
                if (!a.alive) continue
                val ax = s.groupX + a.col * s.cellW
                val ay = s.groupY + a.row * ALIEN_CELL_H
                if (abs(bullet.x - ax) <= s.alienHalfW + BULLET_HALF_W &&
                    abs(bullet.y - ay) <= s.alienHalfH + BULLET_HALF_H
                ) {
                    hitIndex = i
                    break
                }
            }
            if (hitIndex < 0) {
                remainingBullets.add(bullet)
                continue
            }

            val a = aliens[hitIndex]
            val hp = a.hp - 1
            if (hp > 0) {
                // Tough alien absorbs the hit.
                aliens[hitIndex] = a.copy(hp = hp)
            } else {
                aliens[hitIndex] = a.copy(hp = 0, alive = false)
                combo += 1
                comboTimer = COMBO_WINDOW_S
                val mult = comboMultiplier(combo)
                val base = rowPoints(a.row) + if (a.maxHp >= 2) TOUGH_BONUS else 0
                scoreGain += (base * mult).toInt()

                val ax = s.groupX + a.col * s.cellW
                val ay = s.groupY + a.row * ALIEN_CELL_H
                explosions.add(Explosion(ax, ay, EXPLOSION_DURATION_S))
                if (Random.nextFloat() < POWERUP_DROP_CHANCE) {
                    powerUps.add(PowerUp(randomPowerKind(), ax, ay))
                }
            }
        }

        return s.copy(
            aliens = aliens.toList(),
            bullets = remainingBullets,
            score = s.score + scoreGain,
            combo = combo,
            comboTimer = comboTimer,
            explosions = explosions.toList(),
            powerUps = powerUps.toList(),
        )
    }

    /** Stochastically drop bombs from the bottom-most alien of random columns. */
    private fun spawnBombs(s: GameState, dt: Float): GameState {
        if (s.bombs.size >= MAX_BOMBS) return s
        val aliveFraction = s.aliens.count { it.alive }.toFloat() / s.aliens.size.toFloat()
        val chance = s.difficulty.bombRate * dt * (0.5f + 0.5f * aliveFraction)
        if (Random.nextFloat() >= chance) return s

        // Bottom-most alive alien per column — those are the only ones that can shoot.
        val shooters = s.aliens.filter { it.alive }
            .groupBy { it.col }
            .mapValues { (_, list) -> list.maxBy { it.row } }
            .values
        if (shooters.isEmpty()) return s
        val shooter = shooters.random()
        val x = s.groupX + shooter.col * s.cellW
        val y = s.groupY + shooter.row * ALIEN_CELL_H + s.alienHalfH
        return s.copy(bombs = s.bombs + Bomb(x, y))
    }

    /** Advance bombs downward, cull off-screen ones, and resolve hits on the player. */
    private fun stepBombs(s: GameState, dt: Float): GameState {
        val moved = s.bombs.map { it.copy(y = it.y + BOMB_SPEED * dt) }
            .filter { it.y - BOMB_HALF_H < 1f }
        if (s.invulnRemaining > 0f) return s.copy(bombs = moved)

        var hit = false
        val remaining = mutableListOf<Bomb>()
        for (bomb in moved) {
            if (!hit &&
                abs(bomb.x - s.playerX) <= s.playerHalfW + BOMB_HALF_W &&
                abs(bomb.y - PLAYER_Y) <= PLAYER_HALF_H + BOMB_HALF_H
            ) {
                hit = true // consume this bomb; remaining bombs keep falling.
            } else {
                remaining.add(bomb)
            }
        }
        return if (hit) {
            val lives = s.lives - 1
            val updated = s.copy(
                bombs = remaining,
                lives = lives,
                invulnRemaining = INVULN_S,
                combo = 0,
                hitEventId = s.hitEventId + 1,
            )
            if (lives <= 0) endGame(updated.copy(lives = 0)) else updated
        } else {
            s.copy(bombs = remaining)
        }
    }

    /** Advance power-ups downward and apply any the player collects. */
    private fun stepPowerUps(s: GameState, dt: Float): GameState {
        val moved = s.powerUps.map { it.copy(y = it.y + POWERUP_SPEED * dt) }
            .filter { it.y - POWERUP_HALF < 1f }

        var rapid = s.rapidRemaining
        var spread = s.spreadRemaining
        var lives = s.lives
        val remaining = mutableListOf<PowerUp>()
        for (pu in moved) {
            if (abs(pu.x - s.playerX) <= s.playerHalfW + POWERUP_HALF &&
                abs(pu.y - PLAYER_Y) <= PLAYER_HALF_H + POWERUP_HALF
            ) {
                when (pu.kind) {
                    PowerKind.RAPID -> rapid = RAPID_DURATION_S
                    PowerKind.SPREAD -> spread = SPREAD_DURATION_S
                    PowerKind.LIFE -> lives = minOf(lives + 1, MAX_LIVES)
                }
            } else {
                remaining.add(pu)
            }
        }
        return s.copy(powerUps = remaining, rapidRemaining = rapid, spreadRemaining = spread, lives = lives)
    }

    /** Reset the block upward and speed up for the next wave. */
    private fun spawnNextWave(s: GameState): GameState {
        val wave = s.wave + 1
        val cellW = s.cellW
        return s.copy(
            aliens = buildAliens(wave),
            groupX = groupStartX(s.aspect, cellW),
            groupY = START_GY,
            groupDir = 1,
            wave = wave,
            waveSpeedMul = s.waveSpeedMul * WAVE_SPEED_STEP,
            bombs = emptyList(),
            bullets = emptyList(),
        )
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

    private fun initialState(aspect: Float, difficulty: Difficulty, bestScore: Int): GameState {
        val cellW = aspect * BLOCK_WIDTH_FRAC / GRID_COLS
        return GameState(
            aspect = aspect,
            difficulty = difficulty,
            aliens = buildAliens(wave = 1),
            groupX = groupStartX(aspect, cellW),
            groupY = START_GY,
            groupDir = 1,
            playerX = aspect / 2f,
            playerTargetX = aspect / 2f,
            cellW = cellW,
            alienHalfW = cellW * ALIEN_HALF_W_FRAC,
            alienHalfH = ALIEN_CELL_H * ALIEN_HALF_H_FRAC,
            playerHalfW = aspect * PLAYER_HALF_W_FRAC,
            bestScore = bestScore,
        )
    }

    companion object {
        const val START_LIVES = 3
        const val MAX_LIVES = 5

        // Exposed so the Canvas can render sprites at the right size.
        const val PLAYER_Y = 0.9f
        const val PLAYER_HALF_W_FRAC = 0.04f  // of field width -> ship is ~8% of screen wide
        const val PLAYER_HALF_H = 0.02f
        const val BULLET_HALF_W = 0.004f
        const val BULLET_HALF_H = 0.02f
        const val BOMB_HALF_W = 0.006f
        const val BOMB_HALF_H = 0.014f
        const val POWERUP_HALF = 0.016f
        const val ALIEN_CELL_H = 0.05f
        const val EXPLOSION_DURATION_S = 0.32f

        private const val DEFAULT_ASPECT = 9f / 16f
        private const val TICK_MS = 16L
        private const val MAX_DT = 0.033f

        private const val BULLET_SPEED = 2.1f        // heights/sec upward
        private const val FIRE_INTERVAL_S = 0.3f
        private const val RAPID_INTERVAL_S = 0.14f
        private const val SPREAD_ANGLE = 0.28f       // radians off vertical
        private const val BOMB_SPEED = 0.5f          // heights/sec downward
        private const val POWERUP_SPEED = 0.34f      // heights/sec downward
        private const val POWERUP_DROP_CHANCE = 0.13f
        private const val RAPID_DURATION_S = 5f
        private const val SPREAD_DURATION_S = 6f
        private const val INVULN_S = 1.3f
        private const val MAX_BOMBS = 5

        private const val GRID_COLS = 6
        private const val GRID_ROWS = 5
        private const val ALIEN_HALF_W_FRAC = 0.32f  // of cellW
        private const val ALIEN_HALF_H_FRAC = 0.4f   // of cellH
        private const val BLOCK_WIDTH_FRAC = 0.7f    // alien block spans 70% of field width
        private const val START_GY = 0.07f
        private const val WALL_MARGIN = 0.02f
        private const val DROP_STEP = 0.025f

        // March speed: fraction of field width per second, scaled by aspect/difficulty/wave/ramp.
        private const val MARCH_BASE_W = 0.09f
        private const val RAMP = 1.6f                 // speed-up factor as aliens are killed
        private const val WAVE_SPEED_STEP = 1.12f     // each cleared wave marches faster

        // Combo: every 4 consecutive kills adds +0.5x to the multiplier, capped at 4x.
        private const val COMBO_WINDOW_S = 1.3f
        private const val TOUGH_BONUS = 10

        /** Score multiplier for the current combo count (1 at combo 1, capped at 4x). */
        fun comboMultiplier(combo: Int): Float = (1f + ((combo - 1) / 4) * 0.5f).coerceAtMost(4f)

        /** Top rows are worth more: row 0 (top) scores highest. */
        private fun rowPoints(row: Int): Int = (GRID_ROWS - row) * 10

        /** Tough aliens (2 hp) occupy the top row, and from wave 3 the second row too. */
        private fun maxHpFor(row: Int, wave: Int): Int = when {
            row == 0 -> 2
            row == 1 && wave >= 3 -> 2
            else -> 1
        }

        private fun buildAliens(wave: Int): List<Alien> = buildList {
            for (row in 0 until GRID_ROWS) {
                for (col in 0 until GRID_COLS) {
                    val maxHp = maxHpFor(row, wave)
                    add(Alien(col, row, hp = maxHp, maxHp = maxHp, alive = true))
                }
            }
        }

        /** Center the alien block horizontally within the field. */
        private fun groupStartX(aspect: Float, cellW: Float): Float =
            aspect / 2f - (GRID_COLS - 1) / 2f * cellW

        private fun randomPowerKind(): PowerKind {
            val r = Random.nextFloat()
            return when {
                r < 0.15f -> PowerKind.LIFE
                r < 0.55f -> PowerKind.SPREAD
                else -> PowerKind.RAPID
            }
        }
    }
}
