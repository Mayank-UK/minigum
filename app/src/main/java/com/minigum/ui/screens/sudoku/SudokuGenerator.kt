package com.minigum.ui.screens.sudoku

/**
 * Sudoku difficulty levels. [clues] is the target number of given cells: fewer
 * clues → harder puzzle. Generation stops once that few cells remain (or every
 * cell has been tried), always preserving a unique solution.
 */
enum class SudokuDifficulty(val clues: Int, val label: String) {
    EASY(45, "Easy"),
    MEDIUM(38, "Medium"),
    HARD(31, "Hard"),
}

/**
 * Generates Sudoku puzzles: a full valid solved grid, then cells are removed one
 * at a time (in random order) as long as the puzzle keeps exactly one solution.
 * Guarantees a unique-solution puzzle for the requested difficulty.
 *
 * Pure logic — no Android/Compose dependencies — so it can run on a background
 * dispatcher and is easy to reason about.
 */
object SudokuGenerator {

    const val N = 9
    const val CELLS = N * N

    data class Puzzle(val solution: IntArray, val givens: IntArray, val difficulty: SudokuDifficulty)

    /** Produce a uniquely-solvable puzzle for [difficulty]. */
    fun generate(difficulty: SudokuDifficulty): Puzzle {
        val solution = IntArray(CELLS)
        fillFull(solution, 0)
        val givens = solution.copyOf()
        val order = (0 until CELLS).toMutableList().apply { shuffle() }

        var remaining = CELLS
        for (cell in order) {
            if (remaining <= difficulty.clues) break
            val saved = givens[cell]
            givens[cell] = 0
            // Verify uniqueness on a scratch copy so the working grid stays intact.
            if (countSolutions(givens.copyOf(), limit = 2) == 1) {
                remaining--
            } else {
                givens[cell] = saved // removing it created multiple solutions; keep it
            }
        }
        return Puzzle(solution, givens, difficulty)
    }

    /**
     * Count solutions up to [limit] (use 2 to test uniqueness: 1 == unique).
     * Uses the minimum-remaining-values heuristic to pick the next empty cell,
     * which prunes the search dramatically.
     */
    fun countSolutions(grid: IntArray, limit: Int): Int {
        var count = 0

        fun solve() {
            if (count >= limit) return
            val cell = pickEmptyCell(grid) ?: run { count++; return }
            for (v in 1..N) {
                if (canPlace(grid, cell, v)) {
                    grid[cell] = v
                    solve()
                    grid[cell] = 0
                    if (count >= limit) return
                }
            }
        }

        solve()
        return count
    }

    /** Fill the whole grid with a valid randomized solution via backtracking. */
    private fun fillFull(grid: IntArray, idx: Int): Boolean {
        if (idx >= CELLS) return true
        val values = (1..N).toMutableList().apply { shuffle() }
        for (v in values) {
            if (canPlace(grid, idx, v)) {
                grid[idx] = v
                if (fillFull(grid, idx + 1)) return true
                grid[idx] = 0
            }
        }
        return false
    }

    /** Pick the empty cell with the fewest legal candidates (MRV), or null if full. */
    private fun pickEmptyCell(grid: IntArray): Int? {
        var best = -1
        var bestCount = N + 1
        for (i in grid.indices) {
            if (grid[i] != 0) continue
            var candidates = 0
            for (v in 1..N) if (canPlace(grid, i, v)) candidates++
            if (candidates == 0) return i // dead end — fail fast
            if (candidates < bestCount) {
                bestCount = candidates
                best = i
            }
        }
        return if (best < 0) null else best
    }

    private fun canPlace(grid: IntArray, idx: Int, v: Int): Boolean {
        val row = idx / N
        val col = idx % N
        for (i in 0 until N) {
            if (grid[row * N + i] == v) return false
            if (grid[i * N + col] == v) return false
        }
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                if (grid[(boxRow + i) * N + (boxCol + j)] == v) return false
            }
        }
        return true
    }
}
