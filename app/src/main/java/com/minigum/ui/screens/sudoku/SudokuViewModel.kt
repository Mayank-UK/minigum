package com.minigum.ui.screens.sudoku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
class SudokuViewModel @Inject constructor(
) : ViewModel() {

    enum class Phase { IDLE, GENERATING, PLAYING, COMPLETE }

    /** One undo record: enough to restore a single cell's entry and notes. */
    data class UndoStep(val cell: Int, val entry: Int, val notes: BooleanArray)

    data class State(
        val phase: Phase = Phase.IDLE,
        /** Original givens (0 = empty). These cells are not editable. */
        val givens: IntArray = IntArray(SudokuGenerator.CELLS),
        /** The unique solution; used for hints. */
        val solution: IntArray = IntArray(SudokuGenerator.CELLS),
        /** Player entries (0 = empty). */
        val entries: IntArray = IntArray(SudokuGenerator.CELLS),
        /** notes[cell * 9 + (digit - 1)]. */
        val notes: BooleanArray = BooleanArray(SudokuGenerator.CELLS * 9),
        /** Cells revealed by a hint, drawn in a distinct colour. */
        val hints: Set<Int> = emptySet(),
        val selected: Int = 40,
        val notesMode: Boolean = false,
        val mistakes: Int = 0,
        val hintsUsed: Int = 0,
        val elapsedSeconds: Long = 0L,
        val bestSeconds: Long? = null,
        /** Cells whose current value clashes in their row/col/box (across the merged board). */
        val conflicts: Set<Int> = emptySet(),
        val paused: Boolean = false,
        val undoStack: List<UndoStep> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val bestTimes = mutableMapOf<SudokuDifficulty, Long>()
    private var timerJob: Job? = null

    init {
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _state.value
                if (s.phase == Phase.PLAYING && !s.paused) {
                    _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    /** Generate a fresh Medium puzzle (difficulty selector removed by design). */
    fun startNewGame() {
        _state.update {
            it.copy(phase = Phase.GENERATING, selected = 40, paused = false, elapsedSeconds = 0L)
        }
        viewModelScope.launch(Dispatchers.Default) {
            val puzzle = SudokuGenerator.generate(sudokuDifficulty)
            _state.update {
                it.copy(
                    phase = Phase.PLAYING,
                    givens = puzzle.givens.copyOf(),
                    solution = puzzle.solution.copyOf(),
                    entries = IntArray(SudokuGenerator.CELLS),
                    notes = BooleanArray(SudokuGenerator.CELLS * 9),
                    hints = emptySet(),
                    selected = 40,
                    notesMode = false,
                    mistakes = 0,
                    hintsUsed = 0,
                    elapsedSeconds = 0L,
                    bestSeconds = bestTimes[sudokuDifficulty],
                    conflicts = emptySet(),
                    undoStack = emptyList(),
                    paused = false,
                )
            }
        }
    }

    fun pauseGame() {
        _state.update { if (it.phase == Phase.PLAYING) it.copy(paused = true) else it }
    }

    fun resumeGame() {
        _state.update { it.copy(paused = false) }
    }

    fun selectCell(cell: Int) {
        _state.update { it.copy(selected = cell) }
    }

    fun toggleNotesMode() {
        _state.update { it.copy(notesMode = !it.notesMode) }
    }

    fun enterDigit(digit: Int) {
        _state.update { current ->
            if (current.phase != Phase.PLAYING || current.paused) return@update current
            val cell = current.selected
            if (current.givens[cell] != 0) return@update current // givens are locked

            val entries = current.entries.copyOf()
            val notes = current.notes.copyOf()
            var undoStack = current.undoStack
            var mistakes = current.mistakes

            if (current.notesMode) {
                val prevEntry = entries[cell]
                val prevNotes = BooleanArray(9) { current.notes[cell * 9 + it] }
                if (prevEntry != 0) entries[cell] = 0
                val bit = cell * 9 + (digit - 1)
                notes[bit] = !notes[bit]
                undoStack = undoStack + UndoStep(cell, prevEntry, prevNotes)
            } else {
                val prevEntry = entries[cell]
                val prevNotes = BooleanArray(9) { current.notes[cell * 9 + it] }
                if (prevEntry == digit) {
                    entries[cell] = 0 // tapping the placed digit erases it
                } else {
                    entries[cell] = digit
                    clearPeerNotes(notes, cell, digit)
                    // Mistake if the placement clashes on the full board (incl. givens).
                    val board = merge(entries, current.givens)
                    if (hasClash(board, cell, digit)) mistakes++
                }
                for (i in 0 until 9) notes[cell * 9 + i] = false
                undoStack = undoStack + UndoStep(cell, prevEntry, prevNotes)
            }

            val board = merge(entries, current.givens)
            val conflicts = computeConflicts(board)
            val phase = if (isComplete(board, conflicts)) Phase.COMPLETE else Phase.PLAYING
            var best = current.bestSeconds
            if (phase == Phase.COMPLETE) {
                bestTimes.mergeBest(sudokuDifficulty, current.elapsedSeconds)
                best = bestTimes[sudokuDifficulty]
            }

            current.copy(
                entries = entries,
                notes = notes,
                mistakes = mistakes,
                conflicts = conflicts,
                undoStack = undoStack,
                phase = phase,
                bestSeconds = best,
            )
        }
    }

    fun erase() {
        _state.update { current ->
            if (current.phase != Phase.PLAYING || current.paused) return@update current
            val cell = current.selected
            if (current.givens[cell] != 0) return@update current
            val prevEntry = current.entries[cell]
            val prevNotes = BooleanArray(9) { current.notes[cell * 9 + it] }
            if (prevEntry == 0 && prevNotes.all { !it }) return@update current
            val entries = current.entries.copyOf().also { it[cell] = 0 }
            val notes = current.notes.copyOf()
            for (i in 0 until 9) notes[cell * 9 + i] = false
            current.copy(
                entries = entries,
                notes = notes,
                conflicts = computeConflicts(merge(entries, current.givens)),
                undoStack = current.undoStack + UndoStep(cell, prevEntry, prevNotes),
            )
        }
    }

    fun undo() {
        _state.update { current ->
            val step = current.undoStack.lastOrNull() ?: return@update current
            val entries = current.entries.copyOf().also { it[step.cell] = step.entry }
            val notes = current.notes.copyOf()
            for (i in 0 until 9) notes[step.cell * 9 + i] = step.notes[i]
            current.copy(
                entries = entries,
                notes = notes,
                conflicts = computeConflicts(merge(entries, current.givens)),
                undoStack = current.undoStack.dropLast(1),
            )
        }
    }

    /** Reveal the correct digit in the selected cell (or a random empty one). */
    fun hint() {
        _state.update { current ->
            if (current.phase != Phase.PLAYING || current.paused) return@update current
            val cell = when {
                current.entries[current.selected] == 0 && current.givens[current.selected] == 0 -> current.selected
                else -> (0 until SudokuGenerator.CELLS).firstOrNull {
                    current.entries[it] == 0 && current.givens[it] == 0
                } ?: return@update current
            }
            val entries = current.entries.copyOf().also { it[cell] = current.solution[cell] }
            val notes = current.notes.copyOf()
            for (i in 0 until 9) notes[cell * 9 + i] = false
            clearPeerNotes(notes, cell, current.solution[cell])
            val board = merge(entries, current.givens)
            val conflicts = computeConflicts(board)
            val phase = if (isComplete(board, conflicts)) Phase.COMPLETE else Phase.PLAYING
            var best = current.bestSeconds
            if (phase == Phase.COMPLETE) {
                bestTimes.mergeBest(sudokuDifficulty, current.elapsedSeconds)
                best = bestTimes[sudokuDifficulty]
            }
            current.copy(
                entries = entries,
                notes = notes,
                hints = current.hints + cell,
                hintsUsed = current.hintsUsed + 1,
                conflicts = conflicts,
                phase = phase,
                bestSeconds = best,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    // --- validation helpers (always operate on the merged board: givens + entries) ---

    /** Combine givens and player entries into a full board view. */
    private fun merge(entries: IntArray, givens: IntArray): IntArray =
        IntArray(SudokuGenerator.CELLS) { i -> val e = entries[i]; if (e != 0) e else givens[i] }

    /** Does the value at [cell] clash with an equal digit in its row/col/box? */
    private fun hasClash(board: IntArray, cell: Int, digit: Int): Boolean {
        val row = cell / 9
        val col = cell % 9
        for (i in 0 until 9) {
            if (i != col && board[row * 9 + i] == digit) return true
            if (i != row && board[i * 9 + col] == digit) return true
        }
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (i in 0 until 3) for (j in 0 until 3) {
            val c = (boxRow + i) * 9 + (boxCol + j)
            if (c != cell && board[c] == digit) return true
        }
        return false
    }

    /** All cells whose value clashes with a peer. */
    private fun computeConflicts(board: IntArray): Set<Int> {
        val conflicts = mutableSetOf<Int>()
        for (cell in board.indices) {
            val v = board[cell]
            if (v != 0 && hasClash(board, cell, v)) conflicts.add(cell)
        }
        return conflicts
    }

    private fun isComplete(board: IntArray, conflicts: Set<Int>): Boolean =
        conflicts.isEmpty() && board.all { it != 0 }

    /** Remove [digit] from the candidate notes of every peer cell of [cell]. */
    private fun clearPeerNotes(notes: BooleanArray, cell: Int, digit: Int) {
        val row = cell / 9
        val col = cell % 9
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        val bit = digit - 1
        for (i in 0 until 9) {
            notes[(row * 9 + i) * 9 + bit] = false // row peers
            notes[(i * 9 + col) * 9 + bit] = false // column peers
        }
        for (i in 0 until 3) for (j in 0 until 3) {
            notes[((boxRow + i) * 9 + (boxCol + j)) * 9 + bit] = false // box peers
        }
    }

    companion object {
        /** Fixed difficulty — the selector was intentionally removed. */
        val sudokuDifficulty: SudokuDifficulty = SudokuDifficulty.MEDIUM
    }
}

/** Keep the best (lowest) time. */
private fun MutableMap<SudokuDifficulty, Long>.mergeBest(d: SudokuDifficulty, seconds: Long) {
    val existing = this[d]
    if (existing == null || seconds < existing) this[d] = seconds
}
