package com.minigum.ui.screens.pong

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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@HiltViewModel
class PongViewModel @Inject constructor(
) : ViewModel() {

    enum class Winner { PLAYER, AI }

    data class Ball(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        /** Current speed magnitude (heights/sec); grows each rally. */
        val speed: Float,
    )

    /** A pause between points: the ball is hidden at center, then auto-served. */
    data class PendingServe(
        val scorer: Winner,
        val remaining: Float,
    )

    data class GameState(
        val aspect: Float = DEFAULT_ASPECT,
        val ball: Ball = serveBall(DEFAULT_ASPECT, towardsPlayer = false),
        val playerY: Float = 0.5f,
        val playerTargetY: Float = 0.5f,
        val aiY: Float = 0.5f,
        /** Per-rally jitter (heights) added to the AI's angled-return offset. */
        val aiJitter: Float = 0f,
        val playerScore: Int = 0,
        val aiScore: Int = 0,
        val isRunning: Boolean = false,
        val isGameOver: Boolean = false,
        val winner: Winner? = null,
        /** Active during the post-point freeze; null while the ball is live. */
        val pendingServe: PendingServe? = null,
        /** Who scored the most recent point — drives the banner / flash color. */
        val lastScorer: Winner? = null,
        /** Monotonically increments each point so the UI can fire one-shot animations. */
        val scoreEventId: Int = 0,
    )

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * Set the playfield aspect ratio (width / height) once the screen measures.
     * Resets the game to fit while preserving the difficulty.
     * Physics run in a normalized field where height = 1 and width = [aspect],
     * so the playfield always fills the screen at any resolution.
     */
    fun configure(aspect: Float) {
        val current = _state.value
        if (aspect == current.aspect) return
        tickJob?.cancel()
        _state.value = current.copy(
            aspect = aspect,
            ball = serveBall(aspect, towardsPlayer = false),
            aiJitter = randomJitter(),
            isRunning = false,
        )
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
        _state.value = GameState(
            aspect = current.aspect,
            ball = serveBall(current.aspect, towardsPlayer = false),
            aiJitter = randomJitter(),
        )
        start()
    }

    /** Player paddle chases the finger; y is a fraction of field height in [0, 1]. */
    fun setPlayerTarget(y: Float) {
        _state.update { it.copy(playerTargetY = y.coerceIn(0f, 1f)) }
    }

    private fun tick(dt: Float) {
        _state.update { current ->
            if (!current.isRunning || current.isGameOver) return@update current

            // Between points: count down the freeze, then release the serve.
            current.pendingServe?.let { pending ->
                val remaining = pending.remaining - dt
                return@update if (remaining <= 0f) {
                    // Ball is already positioned at center; just go live.
                    current.copy(pendingServe = null, lastScorer = null)
                } else {
                    current.copy(pendingServe = pending.copy(remaining = remaining))
                }
            }

            var ball = stepBall(current.ball, current.aspect, dt)

            // Move the player paddle toward the finger target instantly (1:1 feel),
            // clamped so it can't leave the field.
            val playerY = current.playerTargetY.coerceIn(PADDLE_HALF_H, 1f - PADDLE_HALF_H)

            // AI tracks the ball only while it is approaching (vx > 0); otherwise it
            // eases back to center. Capped speed + a per-rally aim error set difficulty.
            val aiY = stepAi(current, ball, dt)

            ball = collidePlayer(ball, playerY)
            ball = collideAi(ball, aiY, current.aspect)

            // Did someone score?
            val scored = applyScore(ball, current, playerY, aiY)
            if (scored != null) return@update scored

            current.copy(ball = ball, playerY = playerY, aiY = aiY)
        }
    }

    private fun stepBall(ball: Ball, aspect: Float, dt: Float): Ball {
        var x = ball.x + ball.vx * dt
        var y = ball.y + ball.vy * dt
        var vy = ball.vy

        // Top / bottom walls reflect.
        if (y - BALL_R < 0f) {
            y = BALL_R
            vy = -vy
        } else if (y + BALL_R > 1f) {
            y = 1f - BALL_R
            vy = -vy
        }
        return ball.copy(x = x, y = y, vy = vy)
    }

    private fun stepAi(current: GameState, ball: Ball, dt: Float): Float {
        val approaching = ball.vx > 0f
        val target = if (approaching) {
            // Predict where the ball will reach the AI's edge (folding in wall bounces),
            // then aim the paddle so the return goes to the half the player is NOT in.
            val arrival = predictArrivalY(ball, current.aspect)
            val desiredVySign = if (current.playerY < 0.5f) 1f else -1f
            val offset = AI_HIT_OFFSET + current.aiJitter
            (arrival - desiredVySign * offset).coerceIn(PADDLE_HALF_H, 1f - PADDLE_HALF_H)
        } else {
            // Ball headed away: ease back to center slowly so a quick reversal
            // can catch the paddle out of position (this is how the player scores).
            0.5f
        }
        val maxStep = (if (approaching) AI_SPEED else AI_RECENTER_SPEED) * dt
        val delta = (target - current.aiY).coerceIn(-maxStep, maxStep)
        return (current.aiY + delta).coerceIn(PADDLE_HALF_H, 1f - PADDLE_HALF_H)
    }

    /**
     * Where the ball will be (y) when it reaches the AI's edge, accounting for
     * top/bottom wall reflections. Returns the current y if the ball isn't approaching.
     */
    private fun predictArrivalY(ball: Ball, aspect: Float): Float {
        if (ball.vx <= 0f) return ball.y
        val targetX = aiEdgeX(aspect)
        val t = (targetX - ball.x) / ball.vx
        val y = ball.y + ball.vy * t
        val lo = BALL_R
        val hi = 1f - BALL_R
        val span = hi - lo
        if (span <= 0f) return 0.5f
        // Fold y into [lo, hi] by reflecting off the walls (triangle wave).
        var py = (y - lo) % (2f * span)
        if (py < 0f) py += 2f * span
        if (py > span) py = 2f * span - py
        return lo + py
    }

    private fun collidePlayer(ball: Ball, playerY: Float): Ball {
        if (ball.vx >= 0f) return ball
        val edge = playerEdgeX()
        if (ball.x - BALL_R <= edge && ball.x > 0f &&
            abs(ball.y - playerY) <= PADDLE_HALF_H + BALL_R
        ) {
            val relative = ((ball.y - playerY) / PADDLE_HALF_H).coerceIn(-1f, 1f)
            val angle = relative * MAX_BOUNCE_ANGLE
            val speed = (ball.speed * SPEED_UP).coerceAtMost(MAX_SPEED)
            return ball.copy(
                x = edge + BALL_R,
                vx = speed * cos(angle),
                vy = speed * sin(angle),
                speed = speed,
            )
        }
        return ball
    }

    private fun collideAi(ball: Ball, aiY: Float, aspect: Float): Ball {
        if (ball.vx <= 0f) return ball
        val edge = aiEdgeX(aspect)
        if (ball.x + BALL_R >= edge && ball.x < aspect &&
            abs(ball.y - aiY) <= PADDLE_HALF_H + BALL_R
        ) {
            val relative = ((ball.y - aiY) / PADDLE_HALF_H).coerceIn(-1f, 1f)
            val angle = relative * MAX_BOUNCE_ANGLE
            val speed = (ball.speed * SPEED_UP).coerceAtMost(MAX_SPEED)
            return ball.copy(
                x = edge - BALL_R,
                vx = -speed * cos(angle),
                vy = speed * sin(angle),
                speed = speed,
            )
        }
        return ball
    }

    /**
     * Handles scoring, win check, and serve reset.
     * Returns the new state if someone scored, or null if play continues.
     */
    private fun applyScore(
        ball: Ball,
        current: GameState,
        playerY: Float,
        aiY: Float,
    ): GameState? {
        val common = current.copy(playerY = playerY, aiY = aiY)
        val bump = current.scoreEventId + 1

        if (ball.x + BALL_R >= current.aspect) {
            // Ball got past the AI -> player scores.
            val playerScore = current.playerScore + 1
            if (playerScore >= WIN_SCORE) {
                return endGame(common.copy(
                    playerScore = playerScore,
                    winner = Winner.PLAYER,
                    lastScorer = Winner.PLAYER,
                    scoreEventId = bump,
                ))
            }
            // Serve toward the conceding side (the AI).
            return common.copy(
                playerScore = playerScore,
                ball = serveBall(current.aspect, towardsPlayer = false),
                aiJitter = randomJitter(),
                pendingServe = PendingServe(Winner.PLAYER, SERVE_DELAY_S),
                lastScorer = Winner.PLAYER,
                scoreEventId = bump,
            )
        }
        if (ball.x - BALL_R <= 0f) {
            // Ball got past the player -> AI scores.
            val aiScore = current.aiScore + 1
            if (aiScore >= WIN_SCORE) {
                return endGame(common.copy(
                    aiScore = aiScore,
                    winner = Winner.AI,
                    lastScorer = Winner.AI,
                    scoreEventId = bump,
                ))
            }
            // Serve toward the conceding side (the player).
            return common.copy(
                aiScore = aiScore,
                ball = serveBall(current.aspect, towardsPlayer = true),
                aiJitter = randomJitter(),
                pendingServe = PendingServe(Winner.AI, SERVE_DELAY_S),
                lastScorer = Winner.AI,
                scoreEventId = bump,
            )
        }
        return null
    }

    private fun endGame(current: GameState): GameState {
        tickJob?.cancel()
        return current.copy(isRunning = false, isGameOver = true)
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
                // Clamp so a stalled frame can't tunnel the ball through a paddle.
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
        const val WIN_SCORE = 7
        /** Seconds the board freezes between a point and the next serve. */
        const val SERVE_DELAY_S = 1.0f

        private const val DEFAULT_ASPECT = 16f / 9f
        private const val TICK_MS = 16L
        private const val MAX_DT = 0.033f

        private const val PADDLE_HALF_H = 0.09f
        private const val PADDLE_HALF_W = 0.012f
        private const val EDGE_MARGIN = 0.035f
        private const val BALL_R = 0.014f

        private const val BASE_SPEED = 0.55f
        private const val SPEED_UP = 1.06f
        private const val MAX_SPEED = 1.35f
        private val MAX_BOUNCE_ANGLE = (60.0 * PI / 180.0).toFloat()

        // AI: predicts the ball's arrival and angles returns toward the player's
        // open side. Fast enough to reach most shots, but it eases back to center
        // slowly when the ball heads away, so sharp, quick reversals beat it.
        private const val AI_SPEED = 0.8f
        private const val AI_RECENTER_SPEED = 0.35f
        private const val AI_HIT_OFFSET = 0.06f

        fun playerEdgeX(): Float = EDGE_MARGIN + PADDLE_HALF_W
        fun aiEdgeX(aspect: Float): Float = aspect - EDGE_MARGIN - PADDLE_HALF_W
        const val PADDLE_WIDTH = PADDLE_HALF_W * 2f
        const val PADDLE_HEIGHT = PADDLE_HALF_H * 2f
        const val BALL_RADIUS = BALL_R

        /** A small signed jitter in [-0.02, 0.02] heights added to the AI's return offset. */
        private fun randomJitter(): Float = (Random.nextFloat() - 0.5f) * 0.04f

        /** Serve from center; [towardsPlayer] picks the initial horizontal direction. */
        private fun serveBall(aspect: Float, towardsPlayer: Boolean): Ball {
            val dir = if (towardsPlayer) -1 else 1
            val verticalAngle = Random.nextDouble(0.2, 0.45)
            val vy = BASE_SPEED * sin(verticalAngle) * (if (Random.nextBoolean()) 1 else -1)
            val vx = dir * BASE_SPEED * cos(verticalAngle)
            return Ball(
                x = aspect / 2f,
                y = 0.5f,
                vx = vx.toFloat(),
                vy = vy.toFloat(),
                speed = BASE_SPEED,
            )
        }
    }
}
