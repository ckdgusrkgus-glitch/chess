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

    // The squares castling rights are tracked against. Standard chess never needs these to be
    // anything but the usual e1/a1/h1/e8/a8/h8, but an opening augment that relocates a side's
    // back rank (e.g. False Start) moves its king and rooks off those squares entirely, so both
    // move generation (see MoveGenerator.kingMoves) and rights-tracking (below, in [applyMove])
    // need to know where "home" actually is for this game rather than assuming rank 0/7.
    var whiteKingHome = Square(4, 0)
    var whiteQueenRookHome = Square(0, 0)
    var whiteKingRookHome = Square(7, 0)
    var blackKingHome = Square(4, 7)
    var blackQueenRookHome = Square(0, 7)
    var blackKingRookHome = Square(7, 7)

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
        whiteKingHome = Square(4, 0)
        whiteQueenRookHome = Square(0, 0)
        whiteKingRookHome = Square(7, 0)
        blackKingHome = Square(4, 7)
        blackQueenRookHome = Square(0, 7)
        blackKingRookHome = Square(7, 7)
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
        b.whiteKingHome = whiteKingHome
        b.whiteQueenRookHome = whiteQueenRookHome
        b.whiteKingRookHome = whiteKingRookHome
        b.blackKingHome = blackKingHome
        b.blackQueenRookHome = blackQueenRookHome
        b.blackKingRookHome = blackKingRookHome
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
            invalidateCastlingIfRookHome(move.from)
        }
        // A rook captured on its own home square loses that side's castling rights too, even
        // though it's the CAPTURING piece that moved, not the rook itself.
        invalidateCastlingIfRookHome(move.to)

        enPassantTarget = if (moving.type == PieceType.PAWN && Math.abs(move.to.rank - move.from.rank) == 2) {
            Square(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null

        halfmoveClock = if (moving.type == PieceType.PAWN || captured != null || move.isEnPassant) 0 else halfmoveClock + 1

        if (sideToMove == Color.BLACK) fullmoveNumber++
        sideToMove = sideToMove.opposite()
    }

    /** Clears whichever side's castling right corresponds to [square] being one of the configured rook homes. */
    private fun invalidateCastlingIfRookHome(square: Square) {
        when (square) {
            whiteQueenRookHome -> whiteCanCastleQueenSide = false
            whiteKingRookHome -> whiteCanCastleKingSide = false
            blackQueenRookHome -> blackCanCastleQueenSide = false
            blackKingRookHome -> blackCanCastleKingSide = false
            else -> {}
        }
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

    /**
     * Every square holding a [byColor] piece that attacks [square] — the same piece-by-piece rules
     * as [isSquareAttacked], but collecting every attacker instead of stopping at the first one.
     * Needed by 더블 체크 (Double Check), which cares whether a king is attacked by two or more
     * *distinct* pieces at once, not just whether it's attacked at all.
     */
    fun attackersOf(square: Square, byColor: Color): List<Square> {
        val attackers = mutableListOf<Square>()

        val attackerPawnRankOffset = if (byColor == Color.WHITE) -1 else 1
        for (df in intArrayOf(-1, 1)) {
            val sq = square.offset(df, attackerPawnRankOffset)
            if (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) attackers.add(sq)
            }
        }

        val knightOffsets = arrayOf(
            1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2
        )
        for ((df, dr) in knightOffsets) {
            val sq = square.offset(df, dr)
            if (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) attackers.add(sq)
            }
        }

        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val sq = square.offset(df, dr)
            if (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null && p.color == byColor && p.type == PieceType.KING) attackers.add(sq)
            }
        }

        val diagonalDirs = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((df, dr) in diagonalDirs) {
            var sq = square.offset(df, dr)
            while (sq.isValid) {
                val p = pieceAt(sq)
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) attackers.add(sq)
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
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) attackers.add(sq)
                    break
                }
                sq = sq.offset(df, dr)
            }
        }

        return attackers
    }

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
