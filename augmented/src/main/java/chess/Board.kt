package chess

class Board {
    val squares: Array<Piece?> = arrayOfNulls(64)
    var sideToMove: Color = Color.WHITE
    var enPassantTarget: Square? = null
    var whiteCanCastleKingSide = true
    var whiteCanCastleQueenSide = true
    var blackCanCastleKingSide = true
    var blackCanCastleQueenSide = true
    var halfmoveClock = 0
    var fullmoveNumber = 1

    fun pieceAt(sq: Square): Piece? = squares[sq.index]

    fun setup() {
        squares.fill(null)
        val backRank = listOf(
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        )
        for (file in 0..7) {
            squares[Square(file, 0).index] = Piece(backRank[file], Color.WHITE)
            squares[Square(file, 1).index] = Piece(PieceType.PAWN, Color.WHITE)
            squares[Square(file, 6).index] = Piece(PieceType.PAWN, Color.BLACK)
            squares[Square(file, 7).index] = Piece(backRank[file], Color.BLACK)
        }
        sideToMove = Color.WHITE
        enPassantTarget = null
        whiteCanCastleKingSide = true
        whiteCanCastleQueenSide = true
        blackCanCastleKingSide = true
        blackCanCastleQueenSide = true
        halfmoveClock = 0
        fullmoveNumber = 1
    }

    fun copy(): Board {
        val b = Board()
        System.arraycopy(squares, 0, b.squares, 0, 64)
        b.sideToMove = sideToMove
        b.enPassantTarget = enPassantTarget
        b.whiteCanCastleKingSide = whiteCanCastleKingSide
        b.whiteCanCastleQueenSide = whiteCanCastleQueenSide
        b.blackCanCastleKingSide = blackCanCastleKingSide
        b.blackCanCastleQueenSide = blackCanCastleQueenSide
        b.halfmoveClock = halfmoveClock
        b.fullmoveNumber = fullmoveNumber
        return b
    }

    fun findKing(color: Color): Square {
        for (i in 0..63) {
            val p = squares[i]
            if (p != null && p.type == PieceType.KING && p.color == color) return Square.fromIndex(i)
        }
        throw IllegalStateException("King not found for $color")
    }

    /** Applies a pseudo-legal move to the board, updating all game state. */
    fun applyMove(move: Move) {
        val moving = pieceAt(move.from) ?: throw IllegalStateException("No piece at ${move.from}")
        val captured = pieceAt(move.to)

        if (move.isEnPassant) {
            val capturedPawnSquare = Square(move.to.file, move.from.rank)
            squares[capturedPawnSquare.index] = null
        }

        squares[move.from.index] = null
        squares[move.to.index] = if (move.promotion != null) Piece(move.promotion, moving.color) else moving

        if (move.isCastleKingSide) {
            val rank = move.from.rank
            val rookFrom = Square(7, rank)
            val rookTo = Square(5, rank)
            squares[rookTo.index] = squares[rookFrom.index]
            squares[rookFrom.index] = null
        } else if (move.isCastleQueenSide) {
            val rank = move.from.rank
            val rookFrom = Square(0, rank)
            val rookTo = Square(3, rank)
            squares[rookTo.index] = squares[rookFrom.index]
            squares[rookFrom.index] = null
        }

        if (moving.type == PieceType.KING) {
            if (moving.color == Color.WHITE) {
                whiteCanCastleKingSide = false
                whiteCanCastleQueenSide = false
            } else {
                blackCanCastleKingSide = false
                blackCanCastleQueenSide = false
            }
        }
        if (moving.type == PieceType.ROOK) {
            when (move.from) {
                Square(0, 0) -> whiteCanCastleQueenSide = false
                Square(7, 0) -> whiteCanCastleKingSide = false
                Square(0, 7) -> blackCanCastleQueenSide = false
                Square(7, 7) -> blackCanCastleKingSide = false
                else -> {}
            }
        }
        when (move.to) {
            Square(0, 0) -> whiteCanCastleQueenSide = false
            Square(7, 0) -> whiteCanCastleKingSide = false
            Square(0, 7) -> blackCanCastleQueenSide = false
            Square(7, 7) -> blackCanCastleKingSide = false
            else -> {}
        }

        enPassantTarget = if (moving.type == PieceType.PAWN && Math.abs(move.to.rank - move.from.rank) == 2) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null

        halfmoveClock = if (moving.type == PieceType.PAWN || captured != null || move.isEnPassant) 0 else halfmoveClock + 1

        if (sideToMove == Color.BLACK) fullmoveNumber++
        sideToMove = sideToMove.opposite()
    }

    fun isSquareAttacked(square: Square, byColor: Color): Boolean {
        val attackerPawnRankOffset = if (byColor == Color.WHITE) -1 else 1
        for (df in intArrayOf(-1, 1)) {
            val sq = square.offset(df, attackerPawnRankOffset)
            if (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
        }

        val knightOffsets = arrayOf(
            1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2
        )
        for ((df, dr) in knightOffsets) {
            val sq = square.offset(df, dr)
            if (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val sq = square.offset(df, dr)
            if (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null && p.color == byColor && p.type == PieceType.KING) return true
            }
        }

        val diagonalDirs = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((df, dr) in diagonalDirs) {
            var sq = square.offset(df, dr)
            while (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                sq = sq.offset(df, dr)
            }
        }

        val straightDirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((df, dr) in straightDirs) {
            var sq = square.offset(df, dr)
            while (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                sq = sq.offset(df, dr)
            }
        }

        return false
    }

    fun isInCheck(color: Color): Boolean = isSquareAttacked(findKing(color), color.opposite())

    fun render(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            sb.append("${rank + 1} ")
            for (file in 0..7) {
                val p = squares[Square(file, rank).index]
                sb.append(p?.display() ?: '.')
                sb.append(' ')
            }
            sb.append('\n')
        }
        sb.append("  a b c d e f g h\n")
        return sb.toString()
    }
}
