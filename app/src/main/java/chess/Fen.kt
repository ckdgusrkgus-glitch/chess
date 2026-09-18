package chess

/**
 * Standard FEN (Forsyth–Edwards Notation) for this position — the format third-party chess tools
 * and APIs (opening explorers, engines) expect a position in, since there's no other common way
 * to describe "this exact position" to something outside this app.
 */
fun Board.toFen(): String {
    val boardPart = (7 downTo 0).joinToString("/") { rank ->
        buildString {
            var empty = 0
            for (file in 0..7) {
                val piece = squares[Square(file, rank).index]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        append(empty)
                        empty = 0
                    }
                    append(piece.display())
                }
            }
            if (empty > 0) append(empty)
        }
    }
    val side = if (sideToMove == Color.WHITE) "w" else "b"
    val castling = buildString {
        if (whiteCanCastleKingSide) append('K')
        if (whiteCanCastleQueenSide) append('Q')
        if (blackCanCastleKingSide) append('k')
        if (blackCanCastleQueenSide) append('q')
    }.ifEmpty { "-" }
    val enPassant = enPassantTarget?.toString() ?: "-"
    return "$boardPart $side $castling $enPassant $halfmoveClock $fullmoveNumber"
}
