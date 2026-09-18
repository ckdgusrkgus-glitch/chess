package chess

enum class Color {
    WHITE, BLACK;

    fun opposite(): Color = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbol: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K')
}

data class Piece(val type: PieceType, val color: Color) {
    fun display(): Char = if (color == Color.WHITE) type.symbol else type.symbol.lowercaseChar()
}
