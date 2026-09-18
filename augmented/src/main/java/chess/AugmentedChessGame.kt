package chess

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
 */
class AugmentedChessGame {
    val board = Board().apply { setup() }

    /**
     * Every geometrically legal move for the side to move. Unlike [MoveGenerator.legalMoves],
     * nothing is filtered out for leaving the mover's own king in check, since that's now allowed.
     */
    fun movesForSideToMove(): List<Move> = MoveGenerator.allPseudoLegalMoves(board, board.sideToMove)

    fun movesFrom(square: Square): List<Move> {
        val piece = board.pieceAt(square) ?: return emptyList()
        if (piece.color != board.sideToMove) return emptyList()
        return MoveGenerator.pseudoLegalMoves(board, square)
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
        board.applyMove(move)
        return move
    }
}

enum class AugmentedGameStatus { ONGOING, WHITE_WINS, BLACK_WINS }
