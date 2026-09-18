package chess

/**
 * Per-game rule parameters that an opening augment can override for the side that picked it.
 * Defaults reproduce standard chess promotion exactly.
 */
data class AugmentRules(
    val promotionRank: (Color) -> Int = { color -> if (color == Color.WHITE) 7 else 0 },
    val promotionChoices: (Color) -> List<PieceType> = {
        listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
    }
) {
    companion object {
        val STANDARD = AugmentRules()
    }
}
