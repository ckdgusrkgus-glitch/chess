package chess

/**
 * Per-game rule parameters that an augment can override for the side that picked it. Defaults
 * reproduce standard chess exactly.
 */
data class AugmentRules(
    val promotionRank: (Color) -> Int = { color -> if (color == Color.WHITE) 7 else 0 },
    val promotionChoices: (Color) -> List<PieceType> = {
        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
    },
    /** 퇴각 (Retreat): that color's pawns may also step one square backward, or capture backward-diagonally. */
    val pawnCanRetreat: (Color) -> Boolean = { false },
    /** 승마 (Cavalry): that color's king may also move like a knight, in addition to its normal one-square moves. */
    val kingHasKnightMoves: (Color) -> Boolean = { false }
) {
    companion object {
        val STANDARD = AugmentRules()
    }
}
