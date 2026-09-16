package chess.ai

import chess.Board
import chess.Move
import chess.MoveGenerator
import chess.PieceType
import kotlin.random.Random

/**
 * A minimax + alpha-beta search AI. Runs synchronously to completion (or until its time
 * budget expires) — callers on Android must invoke [chooseMove] off the main thread.
 */
class ChessAi(private val level: AiLevel, private val random: Random = Random.Default) {

    /** Picks a move for the side to move on [board], or null if no legal move exists. */
    fun chooseMove(board: Board): Move? {
        val legalMoves = MoveGenerator.legalMoves(board, board.sideToMove)
        if (legalMoves.isEmpty()) return null

        if (level.blunderChance > 0.0 && random.nextDouble() < level.blunderChance) {
            return legalMoves[random.nextInt(legalMoves.size)]
        }

        val deadline = System.nanoTime() + TIME_BUDGET_NANOS
        val scored = orderedMoves(board, legalMoves).map { move ->
            val copy = board.copy()
            copy.applyMove(move)
            // Once time is up, stop recursing altogether and fall back to a cheap static eval so a
            // slow device still finishes the root loop quickly instead of overrunning the budget.
            val score = if (System.nanoTime() > deadline) {
                -Evaluator.evaluate(copy)
            } else {
                -search(copy, level.depth - 1, 1, -INFINITY, INFINITY, deadline)
            }
            move to score
        }.sortedByDescending { it.second }

        val poolSize = level.randomPoolSize.coerceIn(1, scored.size)
        return scored.subList(0, poolSize)[random.nextInt(poolSize)].first
    }

    private fun search(board: Board, depth: Int, ply: Int, alphaIn: Int, beta: Int, deadline: Long): Int {
        // Checked before generating moves: once time is up, bail out in O(1) rather than paying for
        // a full legal-move generation pass on every remaining node in the tree.
        if (System.nanoTime() > deadline) {
            return quiescence(board, alphaIn, beta, deadline, MAX_QUIESCENCE_DEPTH)
        }

        val moves = MoveGenerator.legalMoves(board, board.sideToMove)
        if (moves.isEmpty()) {
            return if (board.isInCheck(board.sideToMove)) -(MATE_SCORE - ply) else 0
        }
        if (depth <= 0) {
            return quiescence(board, alphaIn, beta, deadline, 0)
        }

        var alpha = alphaIn
        for (move in orderedMoves(board, moves)) {
            val copy = board.copy()
            copy.applyMove(move)
            val score = -search(copy, depth - 1, ply + 1, -beta, -alpha, deadline)
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** Extends the search through captures only, to avoid misjudging a position mid-exchange. */
    private fun quiescence(board: Board, alphaIn: Int, beta: Int, deadline: Long, qDepth: Int): Int {
        val standPat = Evaluator.evaluate(board)
        if (qDepth >= MAX_QUIESCENCE_DEPTH || System.nanoTime() > deadline) return standPat

        var alpha = alphaIn
        if (standPat >= beta) return beta
        if (standPat > alpha) alpha = standPat

        val captures = MoveGenerator.legalMoves(board, board.sideToMove)
            .filter { it.isEnPassant || board.pieceAt(it.to) != null }
        for (move in orderedMoves(board, captures)) {
            val copy = board.copy()
            copy.applyMove(move)
            val score = -quiescence(copy, -beta, -alpha, deadline, qDepth + 1)
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun orderedMoves(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { moveOrderScore(board, it) }

    /** Rough MVV-LVA-style ordering so alpha-beta prunes effectively: try promising moves first. */
    private fun moveOrderScore(board: Board, move: Move): Int {
        var score = 0
        val victim = board.pieceAt(move.to)
        if (victim != null) {
            val aggressorValue = board.pieceAt(move.from)?.let { PIECE_ORDER_VALUE.getValue(it.type) } ?: 0
            score += 10_000 + PIECE_ORDER_VALUE.getValue(victim.type) * 10 - aggressorValue
        }
        if (move.isEnPassant) score += 10_050
        if (move.promotion != null) score += 9_000
        return score
    }

    companion object {
        private const val INFINITY = 1_000_000
        private const val MATE_SCORE = 100_000
        private const val TIME_BUDGET_NANOS = 2_500_000_000L
        private const val MAX_QUIESCENCE_DEPTH = 6

        private val PIECE_ORDER_VALUE = mapOf(
            PieceType.PAWN to 100,
            PieceType.KNIGHT to 320,
            PieceType.BISHOP to 330,
            PieceType.ROOK to 500,
            PieceType.QUEEN to 900,
            PieceType.KING to 20_000
        )
    }
}
