package chess

object MoveGenerator {

    fun pseudoLegalMoves(board: Board, from: Square): List<Move> {
        val piece = board.pieceAt(from) ?: return emptyList()
        return when (piece.type) {
            PieceType.PAWN -> pawnMoves(board, from, piece.color)
            PieceType.KNIGHT -> knightMoves(board, from, piece.color)
            PieceType.BISHOP -> slidingMoves(board, from, piece.color, DIAGONAL_DIRS)
            PieceType.ROOK -> slidingMoves(board, from, piece.color, STRAIGHT_DIRS)
            PieceType.QUEEN -> slidingMoves(board, from, piece.color, DIAGONAL_DIRS + STRAIGHT_DIRS)
            PieceType.KING -> kingMoves(board, from, piece.color)
        }
    }

    fun allPseudoLegalMoves(board: Board, color: Color): List<Move> {
        val moves = mutableListOf<Move>()
        for (i in 0..63) {
            val p = board.squares[i]
            if (p != null && p.color == color) {
                moves.addAll(pseudoLegalMoves(board, Square.fromIndex(i)))
            }
        }
        return moves
    }

    /** Legal moves for the whole side: pseudo-legal moves that don't leave the mover's own king in check. */
    fun legalMoves(board: Board, color: Color): List<Move> =
        allPseudoLegalMoves(board, color).filter { isSafe(board, it, color) }

    fun legalMovesFrom(board: Board, from: Square): List<Move> {
        val piece = board.pieceAt(from) ?: return emptyList()
        return pseudoLegalMoves(board, from).filter { isSafe(board, it, piece.color) }
    }

    private fun isSafe(board: Board, move: Move, color: Color): Boolean {
        val copy = board.copy()
        copy.applyMove(move)
        return !copy.isInCheck(color)
    }

    private val DIAGONAL_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val STRAIGHT_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val KNIGHT_OFFSETS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)

    private fun pawnMoves(board: Board, from: Square, color: Color): List<Move> {
        val moves = mutableListOf<Move>()
        val dir = if (color == Color.WHITE) 1 else -1
        val startRank = if (color == Color.WHITE) 1 else 6
        val promotionRank = if (color == Color.WHITE) 7 else 0

        val oneStep = from.offset(0, dir)
        if (oneStep.isValid && board.pieceAt(oneStep) == null) {
            addPawnMove(moves, from, oneStep, promotionRank)
            if (from.rank == startRank) {
                val twoStep = from.offset(0, dir * 2)
                if (twoStep.isValid && board.pieceAt(twoStep) == null) {
                    moves.add(Move(from, twoStep))
                }
            }
        }
        for (df in intArrayOf(-1, 1)) {
            val capSq = from.offset(df, dir)
            if (!capSq.isValid) continue
            val target = board.pieceAt(capSq)
            if (target != null && target.color != color) {
                addPawnMove(moves, from, capSq, promotionRank)
            } else if (target == null && capSq == board.enPassantTarget) {
                moves.add(Move(from, capSq, isEnPassant = true))
            }
        }
        return moves
    }

    private fun addPawnMove(moves: MutableList<Move>, from: Square, to: Square, promotionRank: Int) {
        if (to.rank == promotionRank) {
            for (pt in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                moves.add(Move(from, to, promotion = pt))
            }
        } else {
            moves.add(Move(from, to))
        }
    }

    private fun knightMoves(board: Board, from: Square, color: Color): List<Move> {
        val moves = mutableListOf<Move>()
        for ((df, dr) in KNIGHT_OFFSETS) {
            val to = from.offset(df, dr)
            if (to.isValid) {
                val target = board.pieceAt(to)
                if (target == null || target.color != color) moves.add(Move(from, to))
            }
        }
        return moves
    }

    private fun slidingMoves(board: Board, from: Square, color: Color, dirs: List<Pair<Int, Int>>): List<Move> {
        val moves = mutableListOf<Move>()
        for ((df, dr) in dirs) {
            var to = from.offset(df, dr)
            while (to.isValid) {
                val target = board.pieceAt(to)
                if (target == null) {
                    moves.add(Move(from, to))
                } else {
                    if (target.color != color) moves.add(Move(from, to))
                    break
                }
                to = to.offset(df, dr)
            }
        }
        return moves
    }

    private fun kingMoves(board: Board, from: Square, color: Color): List<Move> {
        val moves = mutableListOf<Move>()
        for (df in -1..1) for (dr in -1..1) {
            if (df == 0 && dr == 0) continue
            val to = from.offset(df, dr)
            if (to.isValid) {
                val target = board.pieceAt(to)
                if (target == null || target.color != color) moves.add(Move(from, to))
            }
        }

        val rank = if (color == Color.WHITE) 0 else 7
        val opponent = color.opposite()
        if (from == Square(4, rank) && !board.isSquareAttacked(from, opponent)) {
            val canKingSide = if (color == Color.WHITE) board.whiteCanCastleKingSide else board.blackCanCastleKingSide
            val canQueenSide = if (color == Color.WHITE) board.whiteCanCastleQueenSide else board.blackCanCastleQueenSide

            if (canKingSide) {
                val f = Square(5, rank); val g = Square(6, rank); val h = Square(7, rank)
                val rook = board.pieceAt(h)
                if (board.pieceAt(f) == null && board.pieceAt(g) == null &&
                    rook != null && rook.type == PieceType.ROOK && rook.color == color &&
                    !board.isSquareAttacked(f, opponent) && !board.isSquareAttacked(g, opponent)
                ) {
                    moves.add(Move(from, g, isCastleKingSide = true))
                }
            }
            if (canQueenSide) {
                val d = Square(3, rank); val c = Square(2, rank); val b = Square(1, rank); val a = Square(0, rank)
                val rook = board.pieceAt(a)
                if (board.pieceAt(d) == null && board.pieceAt(c) == null && board.pieceAt(b) == null &&
                    rook != null && rook.type == PieceType.ROOK && rook.color == color &&
                    !board.isSquareAttacked(d, opponent) && !board.isSquareAttacked(c, opponent)
                ) {
                    moves.add(Move(from, c, isCastleQueenSide = true))
                }
            }
        }
        return moves
    }
}
