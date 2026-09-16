package chess

/** Compact algebraic form used for recording games, e.g. "e2e4" or "e7e8q" for a promotion. */
fun Move.toAlgebraic(): String {
    val promoSuffix = promotion?.symbol?.lowercaseChar()?.toString().orEmpty()
    return "$from$to$promoSuffix"
}

/**
 * Reconstructs the exact [Move] (with all its flags, e.g. castling/en passant) that [notation]
 * refers to, by matching it against the legal moves available in [board]. Returns null if the
 * notation is malformed or doesn't correspond to any currently legal move.
 */
fun parseAlgebraicMove(board: Board, notation: String): Move? {
    if (notation.length < 4) return null
    val from = Square.fromAlgebraic(notation.substring(0, 2)) ?: return null
    val to = Square.fromAlgebraic(notation.substring(2, 4)) ?: return null
    val promotion = if (notation.length >= 5) {
        when (notation[4].lowercaseChar()) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> return null
        }
    } else null
    return MoveGenerator.legalMoves(board, board.sideToMove)
        .find { it.from == from && it.to == to && it.promotion == promotion }
}
