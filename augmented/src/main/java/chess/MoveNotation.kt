package chess

/** Compact algebraic form used for recording games, e.g. "e2e4" or "e7e8q" for a promotion. */
fun Move.toAlgebraic(): String {
    val promoSuffix = promotion?.symbol?.lowercaseChar()?.toString().orEmpty()
    return "$from$to$promoSuffix"
}

/**
 * Reconstructs the exact [Move] (with all its flags, e.g. castling/en passant) that [notation]
 * refers to, by matching it against the moves available in [board] under [rules]. Returns null if
 * the notation is malformed or doesn't correspond to any currently available move.
 *
 * Uses [MoveGenerator.allPseudoLegalMoves] rather than a check-filtered "legal moves" list: under
 * [AugmentedChessGame]'s rules, every pseudo-legal move already is legal (a king may move into or
 * stay in check), so there's no separate safety filter to apply here.
 */
fun parseAlgebraicMove(board: Board, notation: String, rules: AugmentRules = AugmentRules.STANDARD): Move? {
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
    return MoveGenerator.allPseudoLegalMoves(board, board.sideToMove, rules)
        .find { it.from == from && it.to == to && it.promotion == promotion }
}
