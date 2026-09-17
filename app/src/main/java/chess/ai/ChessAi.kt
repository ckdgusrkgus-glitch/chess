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

    /** A candidate root move together with its search score, from the mover's point of view. */
    data class MoveEvaluation(val move: Move, val score: Int)

    /**
     * Nodes visited in the current [chooseMove] call. Checked alongside the wall-clock deadline
     * so the worst case is bounded even on a device far slower than expected — time alone isn't
     * enough, since on a slow enough interpreter even the "already out of time" bail-out path
     * (one legal-move generation per node) adds up before it's checked again.
     */
    private var nodesVisited = 0

    /** Picks a move for the side to move on [board], or null if no legal move exists. */
    fun chooseMove(board: Board): Move? {
        val legalMoves = MoveGenerator.legalMoves(board, board.sideToMove)
        if (legalMoves.isEmpty()) return null

        if (level.blunderChance > 0.0 && random.nextDouble() < level.blunderChance) {
            return legalMoves[random.nextInt(legalMoves.size)]
        }

        val scored = evaluateAllMoves(board)
        val poolSize = level.randomPoolSize.coerceIn(1, scored.size)
        return scored.subList(0, poolSize)[random.nextInt(poolSize)].move
    }

    /**
     * Scores every legal move for the side to move on [board], best first. Used by [chooseMove]
     * and by post-game analysis (e.g. classifying how good a played move was against what the
     * engine itself would have played from the same position).
     */
    fun evaluateAllMoves(board: Board): List<MoveEvaluation> {
        val legalMoves = MoveGenerator.legalMoves(board, board.sideToMove)
        if (legalMoves.isEmpty()) return emptyList()

        nodesVisited = 0
        val deadline = System.nanoTime() + TIME_BUDGET_NANOS
        return orderedMoves(board, legalMoves).map { move ->
            val copy = board.copy()
            copy.applyMove(move)
            // Once the budget is spent, stop recursing altogether and fall back to a cheap static
            // eval so a slow device still finishes the root loop quickly.
            val score = if (budgetExceeded(deadline)) {
                -Evaluator.evaluate(copy)
            } else {
                -search(copy, level.depth - 1, 1, -INFINITY, INFINITY, deadline)
            }
            MoveEvaluation(move, score)
        }.sortedByDescending { it.score }
    }

    /**
     * Greedily walks out a full line to checkmate by taking the engine's own top choice at every
     * ply for both sides, starting from [board]. Only meaningful when [board]'s own best move
     * already scored as [isForcedMateScore] — used to actually show the mate the engine found,
     * rather than just claiming one exists. Stops early once nobody has a legal move (checkmate,
     * or a rare mistaken stalemate if the "forced" mate wasn't as forced as the shallow search
     * thought) or after [maxPlies] as a hard bound in case neither happens.
     */
    fun findMateLine(board: Board, maxPlies: Int = 12): List<Move> {
        val line = mutableListOf<Move>()
        val current = board.copy()
        repeat(maxPlies) {
            val top = evaluateAllMoves(current).firstOrNull() ?: return line
            line.add(top.move)
            current.applyMove(top.move)
            if (MoveGenerator.legalMoves(current, current.sideToMove).isEmpty()) return line
        }
        return line
    }

    /** True once either budget is spent. Always call at most once per node — it also counts the node. */
    private fun budgetExceeded(deadline: Long): Boolean {
        nodesVisited++
        return nodesVisited > MAX_NODES || System.nanoTime() > deadline
    }

    private fun search(board: Board, depth: Int, ply: Int, alphaIn: Int, beta: Int, deadline: Long): Int {
        // Checked before generating moves: once the budget is spent, bail out in O(1) rather than
        // paying for a full legal-move generation pass on every remaining node in the tree.
        if (budgetExceeded(deadline)) {
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
        if (qDepth >= MAX_QUIESCENCE_DEPTH || budgetExceeded(deadline)) return standPat

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
        private const val TIME_BUDGET_NANOS = 1_000_000_000L
        private const val MAX_NODES = 15_000
        private const val MAX_QUIESCENCE_DEPTH = 4

        /**
         * True if [score] (as returned by [evaluateAllMoves], from the mover's point of view)
         * means the engine has proven the mover can force checkmate. Comfortably below
         * [MATE_SCORE] so it never misfires on an ordinary large material advantage.
         */
        fun isForcedMateScore(score: Int): Boolean = score >= MATE_SCORE - 1000

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
