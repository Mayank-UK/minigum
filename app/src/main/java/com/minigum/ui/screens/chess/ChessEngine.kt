package com.minigum.ui.screens.chess

/**
 * Pure-Kotlin chess rules engine. No Android/Compose dependencies, so it is
 * unit-testable and can be reused by the AI ([ChessAi]) or moved to another
 * layer later. Mirrors the role [SudokuGenerator] plays for the Sudoku game.
 *
 * Board model:
 *  - 64 squares indexed `0..63`, `index = rank * 8 + file`.
 *  - rank 0 = rank "1" (white's home), file 0 = file "a". So a1 = 0, h1 = 7,
 *    a8 = 56, h8 = 63.
 */
object ChessEngine {

    /** Side to move / piece colour. */
    enum class Side { WHITE, BLACK;
        val opponent: Side get() = if (this == WHITE) BLACK else WHITE
    }

    /** Piece kinds with a mid-game material value and a Unicode glyph. */
    enum class PieceType(val value: Int, val glyph: Char) {
        PAWN(100, '♟'),
        KNIGHT(320, '♞'),
        BISHOP(330, '♝'),
        ROOK(500, '♜'),
        QUEEN(900, '♛'),
        KING(20_000, '♚');
    }

    /** A concrete piece: type + colour. */
    data class Piece(val type: PieceType, val side: Side)

    enum class CastleRight { WHITE_KINGSIDE, WHITE_QUEENSIDE, BLACK_KINGSIDE, BLACK_QUEENSIDE }

    /** A single move. [captured] is the type of any captured piece (incl. en passant pawn). */
    data class Move(
        val from: Int,
        val to: Int,
        val promotion: PieceType? = null,
        val isCastle: Boolean = false,
        val isEnPassant: Boolean = false,
        val captured: PieceType? = null,
    )

    /** Terminal/non-terminal game status from the perspective of the position. */
    enum class Status { ONGOING, CHECK, CHECKMATE, STALEMATE, DRAW_INSUFFICIENT, DRAW_FIFTY_MOVE }

    /**
     * An immutable-ish position. [board] is copied on every move, so undo is a
     * matter of keeping prior positions around.
     */
    data class Position(
        val board: Array<Piece?>,
        val sideToMove: Side,
        val castling: Set<CastleRight>,
        val epTarget: Int?,
        val halfmoveClock: Int,
        val fullmoveNumber: Int,
    ) {
        /** Stable key for repetition detection (board + side + rights + ep target). */
        fun key(): String {
            val sb = StringBuilder(80)
            for (i in 0 until 64) {
                val p = board[i]
                if (p == null) sb.append('.') else sb.append(if (p.side == Side.WHITE) 'w' else 'b').append(p.type.ordinal)
            }
            sb.append(if (sideToMove == Side.WHITE) 'W' else 'B')
            sb.append(castling.joinToString(",") { it.name })
            sb.append(epTarget ?: -1)
            return sb.toString()
        }

        // data class with an array: override equals/hashCode to avoid array identity pitfalls.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Position) return false
            if (sideToMove != other.sideToMove || epTarget != other.epTarget ||
                halfmoveClock != other.halfmoveClock || fullmoveNumber != other.fullmoveNumber ||
                castling != other.castling
            ) return false
            for (i in 0 until 64) if (board[i] != other.board[i]) return false
            return true
        }

        override fun hashCode(): Int {
            var h = sideToMove.hashCode()
            for (i in 0 until 64) h = 31 * h + (board[i]?.hashCode() ?: 0)
            return h
        }
    }

    // --- coordinate helpers ---

    fun rank(sq: Int): Int = sq / 8
    fun file(sq: Int): Int = sq % 8
    fun square(rank: Int, file: Int): Int = rank * 8 + file

    /** "a1".."h8" for display. */
    fun squareName(sq: Int): String = "${('a' + file(sq))}${rank(sq) + 1}"

    // movement direction deltas as (dRank, dFile)
    private val ROOK_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val BISHOP_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val QUEEN_DIRS = ROOK_DIRS + BISHOP_DIRS
    private val KING_DELTAS = QUEEN_DIRS
    private val KNIGHT_DELTAS = listOf(
        2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2,
    )

    /** Standard starting position. */
    fun startingPosition(): Position {
        val board = arrayOfNulls<Piece>(64)
        val back = listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK,
        )
        for (f in 0..7) {
            board[square(0, f)] = Piece(back[f], Side.WHITE)
            board[square(1, f)] = Piece(PieceType.PAWN, Side.WHITE)
            board[square(6, f)] = Piece(PieceType.PAWN, Side.BLACK)
            board[square(7, f)] = Piece(back[f], Side.BLACK)
        }
        return Position(
            board = board,
            sideToMove = Side.WHITE,
            castling = CastleRight.values().toSet(),
            epTarget = null,
            halfmoveClock = 0,
            fullmoveNumber = 1,
        )
    }

    // --- move generation ---

    /** All legal moves for the side to move. */
    fun legalMoves(pos: Position): List<Move> {
        val side = pos.sideToMove
        return pseudoLegalMoves(pos, side).filter { move ->
            val next = makeMove(pos, move)
            !isAttacked(next, kingSquare(next, side), side.opponent)
        }
    }

    /** Legal moves originating from [from] — drives tap-to-select highlighting. */
    fun legalMovesFrom(pos: Position, from: Int): List<Move> =
        legalMoves(pos).filter { it.from == from }

    private fun pseudoLegalMoves(pos: Position, side: Side): List<Move> {
        val moves = mutableListOf<Move>()
        for (i in 0 until 64) {
            val p = pos.board[i] ?: continue
            if (p.side != side) continue
            when (p.type) {
                PieceType.PAWN -> addPawnMoves(pos, i, p, moves)
                PieceType.KNIGHT -> addStepMoves(pos, i, p, KNIGHT_DELTAS, moves)
                PieceType.KING -> {
                    addStepMoves(pos, i, p, KING_DELTAS, moves)
                    addCastling(pos, i, p, moves)
                }
                PieceType.BISHOP -> addSlideMoves(pos, i, p, BISHOP_DIRS, moves)
                PieceType.ROOK -> addSlideMoves(pos, i, p, ROOK_DIRS, moves)
                PieceType.QUEEN -> addSlideMoves(pos, i, p, QUEEN_DIRS, moves)
            }
        }
        return moves
    }

    private fun addSlideMoves(pos: Position, from: Int, piece: Piece, dirs: List<Pair<Int, Int>>, moves: MutableList<Move>) {
        val r = rank(from); val f = file(from)
        for ((dr, df) in dirs) {
            var nr = r + dr; var nf = f + df
            while (nr in 0..7 && nf in 0..7) {
                val to = square(nr, nf)
                val target = pos.board[to]
                if (target == null) {
                    moves.add(Move(from, to))
                } else {
                    if (target.side != piece.side) moves.add(Move(from, to, captured = target.type))
                    break
                }
                nr += dr; nf += df
            }
        }
    }

    private fun addStepMoves(pos: Position, from: Int, piece: Piece, deltas: List<Pair<Int, Int>>, moves: MutableList<Move>) {
        val r = rank(from); val f = file(from)
        for ((dr, df) in deltas) {
            val nr = r + dr; val nf = f + df
            if (nr !in 0..7 || nf !in 0..7) continue
            val to = square(nr, nf)
            val target = pos.board[to]
            if (target == null) moves.add(Move(from, to))
            else if (target.side != piece.side) moves.add(Move(from, to, captured = target.type))
        }
    }

    private fun addPawnMoves(pos: Position, from: Int, pawn: Piece, moves: MutableList<Move>) {
        val r = rank(from); val f = file(from)
        val dir = if (pawn.side == Side.WHITE) 1 else -1
        val startRank = if (pawn.side == Side.WHITE) 1 else 6
        val promoRank = if (pawn.side == Side.WHITE) 7 else 0

        // single + double forward push
        val oneR = r + dir
        if (oneR in 0..7 && pos.board[square(oneR, f)] == null) {
            addPawnLand(from, square(oneR, f), null, oneR == promoRank, moves)
            if (r == startRank) {
                val twoR = r + 2 * dir
                if (pos.board[square(twoR, f)] == null) moves.add(Move(from, square(twoR, f)))
            }
        }
        // diagonal captures + en passant
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val nr = r + dir
            if (nr !in 0..7) continue
            val to = square(nr, nf)
            val target = pos.board[to]
            if (target != null && target.side != pawn.side) {
                addPawnLand(from, to, target.type, nr == promoRank, moves)
            } else if (to == pos.epTarget) {
                moves.add(Move(from, to, isEnPassant = true, captured = PieceType.PAWN))
            }
        }
    }

    private fun addPawnLand(from: Int, to: Int, captured: PieceType?, promotes: Boolean, moves: MutableList<Move>) {
        if (promotes) {
            for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                moves.add(Move(from, to, promotion = promo, captured = captured))
            }
        } else {
            moves.add(Move(from, to, captured = captured))
        }
    }

    private fun addCastling(pos: Position, from: Int, king: Piece, moves: MutableList<Move>) {
        if (from != squareForKingHome(king.side)) return
        if (isAttacked(pos, from, king.side.opponent)) return // cannot castle out of check
        val r = if (king.side == Side.WHITE) 0 else 7
        val ks = if (king.side == Side.WHITE) CastleRight.WHITE_KINGSIDE else CastleRight.BLACK_KINGSIDE
        val qs = if (king.side == Side.WHITE) CastleRight.WHITE_QUEENSIDE else CastleRight.BLACK_QUEENSIDE
        if (ks in pos.castling &&
            pos.board[square(r, 5)] == null && pos.board[square(r, 6)] == null &&
            pos.board[square(r, 7)]?.type == PieceType.ROOK && pos.board[square(r, 7)]?.side == king.side &&
            !isAttacked(pos, square(r, 5), king.side.opponent) &&
            !isAttacked(pos, square(r, 6), king.side.opponent)
        ) {
            moves.add(Move(from, square(r, 6), isCastle = true))
        }
        if (qs in pos.castling &&
            pos.board[square(r, 3)] == null && pos.board[square(r, 2)] == null && pos.board[square(r, 1)] == null &&
            pos.board[square(r, 0)]?.type == PieceType.ROOK && pos.board[square(r, 0)]?.side == king.side &&
            !isAttacked(pos, square(r, 3), king.side.opponent) &&
            !isAttacked(pos, square(r, 2), king.side.opponent)
        ) {
            moves.add(Move(from, square(r, 2), isCastle = true))
        }
    }

    private fun squareForKingHome(side: Side): Int = if (side == Side.WHITE) square(0, 4) else square(7, 4)

    // --- attack detection / check ---

    /** Is [target] attacked by any piece of [by]? */
    fun isAttacked(pos: Position, target: Int, by: Side): Boolean {
        val r = rank(target); val f = file(target)

        // pawn attacks: an enemy pawn sits one rank "back" along its capture direction
        val pawnBack = if (by == Side.WHITE) -1 else 1
        for (df in intArrayOf(-1, 1)) {
            val pr = r + pawnBack; val pf = f + df
            if (pr in 0..7 && pf in 0..7) {
                val p = pos.board[square(pr, pf)]
                if (p != null && p.side == by && p.type == PieceType.PAWN) return true
            }
        }
        // knights
        for ((dr, df) in KNIGHT_DELTAS) {
            val nr = r + dr; val nf = f + df
            if (nr in 0..7 && nf in 0..7) {
                val p = pos.board[square(nr, nf)]
                if (p != null && p.side == by && p.type == PieceType.KNIGHT) return true
            }
        }
        // adjacent king
        for ((dr, df) in KING_DELTAS) {
            val nr = r + dr; val nf = f + df
            if (nr in 0..7 && nf in 0..7) {
                val p = pos.board[square(nr, nf)]
                if (p != null && p.side == by && p.type == PieceType.KING) return true
            }
        }
        // orthogonal sliders (rook, queen)
        for ((dr, df) in ROOK_DIRS) {
            var nr = r + dr; var nf = f + df
            while (nr in 0..7 && nf in 0..7) {
                val p = pos.board[square(nr, nf)]
                if (p != null) {
                    if (p.side == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                nr += dr; nf += df
            }
        }
        // diagonal sliders (bishop, queen)
        for ((dr, df) in BISHOP_DIRS) {
            var nr = r + dr; var nf = f + df
            while (nr in 0..7 && nf in 0..7) {
                val p = pos.board[square(nr, nf)]
                if (p != null) {
                    if (p.side == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                nr += dr; nf += df
            }
        }
        return false
    }

    fun kingSquare(pos: Position, side: Side): Int {
        for (i in 0 until 64) {
            val p = pos.board[i]
            if (p != null && p.side == side && p.type == PieceType.KING) return i
        }
        return -1
    }

    fun inCheck(pos: Position, side: Side): Boolean = isAttacked(pos, kingSquare(pos, side), side.opponent)

    // --- applying a move ---

    /** Returns the position after [move]. Does not mutate [pos]. */
    fun makeMove(pos: Position, move: Move): Position {
        val board = pos.board.copyOf()
        val mover = board[move.from] ?: error("no piece at ${squareName(move.from)}")
        val destRank = rank(move.to)

        // place mover (with promotion if any)
        board[move.to] = if (move.promotion != null) Piece(move.promotion, mover.side) else mover
        board[move.from] = null

        // en passant: the captured pawn is not on the destination square
        if (move.isEnPassant) {
            val capRank = destRank - if (mover.side == Side.WHITE) 1 else -1
            board[square(capRank, file(move.to))] = null
        }

        // castling: shift the rook to its new home
        if (move.isCastle) {
            val r = destRank
            when (file(move.to)) {
                6 -> { board[square(r, 5)] = board[square(r, 7)]; board[square(r, 7)] = null } // king-side
                2 -> { board[square(r, 3)] = board[square(r, 0)]; board[square(r, 0)] = null } // queen-side
            }
        }

        // update castling rights (king move, rook move, or rook captured on home)
        val castling = if (pos.castling.isEmpty()) pos.castling
        else pos.castling - rightsForSquare(move.from) - rightsForSquare(move.to)

        // en passant target only after a double pawn push
        val epTarget = if (mover.type == PieceType.PAWN && kotlin.math.abs(destRank - rank(move.from)) == 2) {
            square((rank(move.from) + destRank) / 2, file(move.from))
        } else null

        val halfmove = if (mover.type == PieceType.PAWN || move.captured != null) 0 else pos.halfmoveClock + 1
        val fullmove = if (pos.sideToMove == Side.BLACK) pos.fullmoveNumber + 1 else pos.fullmoveNumber

        return Position(board, pos.sideToMove.opponent, castling, epTarget, halfmove, fullmove)
    }

    private fun rightsForSquare(sq: Int): Set<CastleRight> = when (sq) {
        square(0, 4) -> setOf(CastleRight.WHITE_KINGSIDE, CastleRight.WHITE_QUEENSIDE) // e1
        square(7, 4) -> setOf(CastleRight.BLACK_KINGSIDE, CastleRight.BLACK_QUEENSIDE) // e8
        square(0, 0) -> setOf(CastleRight.WHITE_QUEENSIDE) // a1
        square(0, 7) -> setOf(CastleRight.WHITE_KINGSIDE)  // h1
        square(7, 0) -> setOf(CastleRight.BLACK_QUEENSIDE) // a8
        square(7, 7) -> setOf(CastleRight.BLACK_KINGSIDE)  // h8
        else -> emptySet()
    }

    // --- status ---

    /** Game status for [pos]. Note: threefold repetition is tracked by the caller. */
    fun status(pos: Position): Status {
        val side = pos.sideToMove
        val hasMoves = legalMoves(pos).isNotEmpty()
        val check = inCheck(pos, side)
        if (!hasMoves) return if (check) Status.CHECKMATE else Status.STALEMATE
        if (pos.halfmoveClock >= 100) return Status.DRAW_FIFTY_MOVE // 50 full moves = 100 half-moves
        if (insufficientMaterial(pos)) return Status.DRAW_INSUFFICIENT
        return if (check) Status.CHECK else Status.ONGOING
    }

    /** K vs K, or K + a single minor vs K. */
    fun insufficientMaterial(pos: Position): Boolean {
        val nonKings = (0 until 64).mapNotNull { pos.board[it] }.filter { it.type != PieceType.KING }
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1) {
            val t = nonKings[0].type
            return t == PieceType.BISHOP || t == PieceType.KNIGHT
        }
        return false
    }
}
