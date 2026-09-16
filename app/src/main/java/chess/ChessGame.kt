package chess

enum class GameStatus { ONGOING, CHECKMATE, STALEMATE, DRAW_FIFTY_MOVE, DRAW_INSUFFICIENT_MATERIAL }

class ChessGame {
    val board = Board().apply { setup() }

    fun status(): GameStatus {
        val color = board.sideToMove
        val moves = MoveGenerator.legalMoves(board, color)
        if (moves.isEmpty()) {
            return if (board.isInCheck(color)) GameStatus.CHECKMATE else GameStatus.STALEMATE
        }
        if (board.halfmoveClock >= 100) return GameStatus.DRAW_FIFTY_MOVE
        if (isInsufficientMaterial()) return GameStatus.DRAW_INSUFFICIENT_MATERIAL
        return GameStatus.ONGOING
    }

    private fun isInsufficientMaterial(): Boolean {
        val pieces = board.squares.filterNotNull()
        if (pieces.all { it.type == PieceType.KING }) return true
        if (pieces.size == 3 && pieces.any { it.type == PieceType.BISHOP || it.type == PieceType.KNIGHT }) return true
        return false
    }

    fun legalMovesFrom(square: Square): List<Move> = MoveGenerator.legalMovesFrom(board, square)

    /** Attempts to play a move from [from] to [to]. Returns the applied [Move], or null if illegal. */
    fun tryMove(from: Square, to: Square, promotion: PieceType? = null): Move? {
        val candidates = legalMovesFrom(from).filter { it.to == to }
        if (candidates.isEmpty()) return null
        val move = if (candidates.size == 1) candidates[0]
        else candidates.find { it.promotion == (promotion ?: PieceType.QUEEN) } ?: candidates.first()
        board.applyMove(move)
        return move
    }
}
