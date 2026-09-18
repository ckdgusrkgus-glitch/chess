package chess

import chess.augment.OpeningAugment

/**
 * Base ruleset for "증강체스" (Augmented Chess): standard chess movement, but the win/loss
 * conditions differ from a normal [chess.ChessGame]-style game —
 *  - Checkmate does not end the game by itself; a king may move into (or stay in) an attacked
 *    square. The game only ends once a king is actually captured.
 *  - A side with no legal moves at all loses immediately, whether or not it's in check — this
 *    replaces both "checkmate" and "stalemate" from standard chess with a single condition.
 *
 * Castling itself is unaffected: it still requires the king not be in check and not pass through
 * an attacked square, exactly as in [MoveGenerator]'s existing castling logic — the source
 * material only ever describes ordinary king moves as newly legal, not castling's own rule.
 *
 * [whiteAugment]/[blackAugment] are each side's drafted "오프닝 증강" (opening augment, see
 * [OpeningAugment]), applied once per [resetBoard] call and reflected for the rest of that game in
 * [rules]. [recycleRuleEnabled] turns on "재활용" (Recycle), a "규칙 증강" (rule augment) that — unlike
 * opening augments — applies identically to both sides regardless of what they drafted, so it's a
 * separate flag rather than something either side picks.
 */
class AugmentedChessGame(
    private val whiteAugment: OpeningAugment? = null,
    private val blackAugment: OpeningAugment? = null,
    private val recycleRuleEnabled: Boolean = false
) {
    val board = Board()

    val rules = AugmentRules(
        promotionRank = { color ->
            augmentFor(color)?.promotionRankOverride?.invoke(color) ?: AugmentRules.STANDARD.promotionRank(color)
        },
        promotionChoices = { color -> resolvePromotionChoices(color) }
    )

    /** Total plies (half-moves) played since [resetBoard] — 존버's 14-ply timer counts against this. */
    private var plyCount = 0

    /** One entry per side that drafted 존버, tracking where that pawn currently is. Removed the
     *  moment it's captured, promoted some other way, or its 14-ply timer fires. */
    private class TurtlingWatch(val color: Color, var square: Square)
    private val turtlingWatches = mutableListOf<TurtlingWatch>()

    /** How many of each piece type each color has had captured — 재활용's "already lost" credits. */
    private val capturedCounts = mutableMapOf<Color, MutableMap<PieceType, Int>>()
    /** How many of those credits each color has already spent on a promotion. */
    private val usedPromotionCounts = mutableMapOf<Color, MutableMap<PieceType, Int>>()

    init {
        resetBoard()
    }

    private fun augmentFor(color: Color): OpeningAugment? = if (color == Color.WHITE) whiteAugment else blackAugment

    private fun resolvePromotionChoices(color: Color): List<PieceType> {
        val base = augmentFor(color)?.promotionChoicesOverride?.invoke(color) ?: AugmentRules.STANDARD.promotionChoices(color)
        if (!recycleRuleEnabled) return base
        val captured = capturedCounts[color].orEmpty()
        val used = usedPromotionCounts[color].orEmpty()
        return base.filter { type -> (captured[type] ?: 0) > (used[type] ?: 0) }
    }

    /** Resets to the standard starting position, re-applies each side's opening augment, and clears all per-game tracking. */
    fun resetBoard() {
        board.setup()
        whiteAugment?.setupTransform?.invoke(board, Color.WHITE)
        blackAugment?.setupTransform?.invoke(board, Color.BLACK)

        plyCount = 0
        capturedCounts.clear()
        usedPromotionCounts.clear()
        turtlingWatches.clear()
        whiteAugment?.turtlingFile?.let { turtlingWatches += TurtlingWatch(Color.WHITE, Square(it, 1)) }
        blackAugment?.turtlingFile?.let { turtlingWatches += TurtlingWatch(Color.BLACK, Square(it, 6)) }
    }

    /** Every geometrically legal move for the side to move. Unlike a check-filtered "legal moves"
     *  list, nothing is filtered out for leaving the mover's own king in check, since that's now allowed. */
    fun movesForSideToMove(): List<Move> = MoveGenerator.allPseudoLegalMoves(board, board.sideToMove, rules)

    fun movesFrom(square: Square): List<Move> {
        val piece = board.pieceAt(square) ?: return emptyList()
        if (piece.color != board.sideToMove) return emptyList()
        return MoveGenerator.pseudoLegalMoves(board, square, rules)
    }

    fun status(): AugmentedGameStatus {
        if (!kingAlive(Color.BLACK)) return AugmentedGameStatus.WHITE_WINS
        if (!kingAlive(Color.WHITE)) return AugmentedGameStatus.BLACK_WINS
        if (movesForSideToMove().isEmpty()) {
            return if (board.sideToMove == Color.WHITE) AugmentedGameStatus.BLACK_WINS else AugmentedGameStatus.WHITE_WINS
        }
        return AugmentedGameStatus.ONGOING
    }

    private fun kingAlive(color: Color): Boolean =
        board.squares.any { it != null && it.type == PieceType.KING && it.color == color }

    /** Attempts to play a move from [from] to [to]. Returns the applied [Move], or null if illegal. */
    fun tryMove(from: Square, to: Square, promotion: PieceType? = null): Move? {
        val candidates = movesFrom(from).filter { it.to == to }
        if (candidates.isEmpty()) return null
        val move = if (candidates.size == 1) candidates[0]
        else candidates.find { it.promotion == (promotion ?: PieceType.QUEEN) } ?: candidates.first()

        // Captured-for-en-passant lands on a different square than `move.to`, so it has to be read
        // off the board before applyMove clears it, same as the direct-capture case.
        val captured = if (move.isEnPassant) board.pieceAt(Square(move.to.file, move.from.rank)) else board.pieceAt(move.to)

        board.applyMove(move)
        onMoveApplied(move, captured)
        return move
    }

    private fun onMoveApplied(move: Move, captured: Piece?) {
        plyCount++

        if (captured != null) {
            val counts = capturedCounts.getOrPut(captured.color) { mutableMapOf() }
            counts[captured.type] = (counts[captured.type] ?: 0) + 1
        }
        if (move.promotion != null) {
            val promotedColor = board.pieceAt(move.to)?.color
            if (promotedColor != null) {
                val used = usedPromotionCounts.getOrPut(promotedColor) { mutableMapOf() }
                used[move.promotion] = (used[move.promotion] ?: 0) + 1
            }
        }

        for (watch in turtlingWatches) {
            if (watch.square == move.from) watch.square = move.to
        }
        // Drops a watch the moment its square no longer holds that color's pawn — captured, or
        // already promoted some other way (e.g. it reached the normal promotion rank early).
        turtlingWatches.removeAll { watch ->
            val piece = board.pieceAt(watch.square)
            piece == null || piece.type != PieceType.PAWN || piece.color != watch.color
        }
        if (plyCount >= TURTLING_TRIGGER_PLIES && turtlingWatches.isNotEmpty()) {
            turtlingWatches.forEach { board.squares[it.square.index] = Piece(PieceType.QUEEN, it.color) }
            turtlingWatches.clear()
        }
    }

    companion object {
        private const val TURTLING_TRIGGER_PLIES = 14
    }
}

enum class AugmentedGameStatus { ONGOING, WHITE_WINS, BLACK_WINS }
