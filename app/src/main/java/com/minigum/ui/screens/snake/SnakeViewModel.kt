package com.minigum.ui.screens.snake

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

@HiltViewModel
class SnakeViewModel @Inject constructor(
) : ViewModel() {

    enum class Direction(val dx: Int, val dy: Int) {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        fun isOpposite(other: Direction): Boolean =
            dx == -other.dx && dy == -other.dy
    }

    data class Point(val x: Int, val y: Int)

    data class GameState(
        val cols: Int = DEFAULT_COLS,
        val rows: Int = DEFAULT_ROWS,
        val snake: List<Point> = emptyList(),
        val food: Point = Point(0, 0),
        val direction: Direction = Direction.RIGHT,
        /** Direction queued by the player; applied on the next tick to avoid reversing into self. */
        val pendingDirection: Direction? = null,
        val isRunning: Boolean = false,
        val isGameOver: Boolean = false,
        val score: Int = 0,
        val bestScore: Int = 0,
    )

    private val _state = MutableStateFlow(initialState(DEFAULT_COLS, DEFAULT_ROWS, bestScore = 0))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var tickJob: Job? = null

    /**
     * Set the playfield dimensions (called once the screen measures its size).
     * Resets the game to fit the new grid while preserving the best score.
     * Cells stay square in the UI; the grid is rectangular to fill the screen.
     */
    fun configure(cols: Int, rows: Int) {
        val current = _state.value
        if (cols == current.cols && rows == current.rows) return
        tickJob?.cancel()
        _state.value = initialState(cols, rows, bestScore = current.bestScore)
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
        _state.value = initialState(current.cols, current.rows, bestScore = current.bestScore)
        start()
    }

    fun changeDirection(direction: Direction) {
        _state.update { current ->
            val effective = current.pendingDirection ?: current.direction
            // Ignore 180° reversals and no-ops.
            if (direction == effective || direction.isOpposite(effective)) {
                current
            } else {
                current.copy(pendingDirection = direction)
            }
        }
    }

    private fun tick() {
        _state.update { current ->
            if (!current.isRunning || current.isGameOver) return@update current

            val direction = current.pendingDirection ?: current.direction
            val head = current.snake.first()
            val newHead = Point(head.x + direction.dx, head.y + direction.dy)

            // Wall collision -> game over (classic).
            if (newHead.x < 0 || newHead.x >= current.cols ||
                newHead.y < 0 || newHead.y >= current.rows
            ) {
                return@update endGame(current)
            }

            val eating = newHead == current.food
            // The tail moves out of the way unless the snake just grew.
            val body = if (eating) current.snake else current.snake.dropLast(1)

            // Self collision -> game over.
            if (newHead in body) {
                return@update endGame(current)
            }

            val newSnake = listOf(newHead) + body
            val newFood = if (eating) randomFood(newSnake, current.cols, current.rows) else current.food
            val newScore = if (eating) current.score + 1 else current.score

            current.copy(
                snake = newSnake,
                food = newFood,
                direction = direction,
                pendingDirection = null,
                score = newScore,
            )
        }
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
            while (isActive) {
                val state = _state.value
                if (!state.isRunning || state.isGameOver) break
                delay(tickDelayMs(state.score))
                tick()
            }
        }
    }

    /** Tick speed increases (delay shrinks) as the score grows, down to a floor. */
    private fun tickDelayMs(score: Int): Long = (BASE_DELAY_MS - score * SPEED_STEP_MS)
        .coerceAtLeast(MIN_DELAY_MS)

    private fun initialState(cols: Int, rows: Int, bestScore: Int): GameState {
        val startY = rows / 2
        val startX = cols / 2
        // A short horizontal snake pointing right, centered vertically.
        val snake = listOf(
            Point(startX, startY),
            Point(startX - 1, startY),
            Point(startX - 2, startY),
        )
        return GameState(
            cols = cols,
            rows = rows,
            snake = snake,
            food = randomFood(snake, cols, rows),
            direction = Direction.RIGHT,
            bestScore = bestScore,
        )
    }

    private fun randomFood(snake: List<Point>, cols: Int, rows: Int): Point {
        val free = (0 until cols * rows)
            .map { Point(it % cols, it / cols) }
            .filter { it !in snake }
        return free.random()
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }

    companion object {
        const val DEFAULT_COLS = 16
        const val DEFAULT_ROWS = 16
        private const val BASE_DELAY_MS = 200L
        private const val MIN_DELAY_MS = 70L
        private const val SPEED_STEP_MS = 6L
    }
}
