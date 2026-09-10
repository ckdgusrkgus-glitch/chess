package chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveGeneratorTest {

    private fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1
        val moves = MoveGenerator.legalMoves(board, board.sideToMove)
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (move in moves) {
            val copy = board.copy()
            copy.applyMove(move)
            nodes += perft(copy, depth - 1)
        }
        return nodes
    }

    @Test
    fun `initial position has 20 legal moves`() {
        val board = Board().apply { setup() }
        assertEquals(20, perft(board, 1))
    }

    @Test
    fun `perft depth 2 matches known value`() {
        val board = Board().apply { setup() }
        assertEquals(400, perft(board, 2))
    }

    @Test
    fun `perft depth 3 matches known value`() {
        val board = Board().apply { setup() }
        assertEquals(8902, perft(board, 3))
    }

    @Test
    fun `perft depth 4 matches known value`() {
        val board = Board().apply { setup() }
        assertEquals(197281, perft(board, 4))
    }

    @Test
    fun `fools mate results in checkmate`() {
        val game = ChessGame()
        assertNotNull(game.tryMove(Square.fromAlgebraic("f2")!!, Square.fromAlgebraic("f3")!!))
        assertNotNull(game.tryMove(Square.fromAlgebraic("e7")!!, Square.fromAlgebraic("e5")!!))
        assertNotNull(game.tryMove(Square.fromAlgebraic("g2")!!, Square.fromAlgebraic("g4")!!))
        assertNotNull(game.tryMove(Square.fromAlgebraic("d8")!!, Square.fromAlgebraic("h4")!!))
        assertEquals(GameStatus.CHECKMATE, game.status())
    }

    @Test
    fun `white kingside castling moves king and rook`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        board.squares[Square(7, 0).index] = Piece(PieceType.ROOK, Color.WHITE)
        board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        board.sideToMove = Color.WHITE

        val move = MoveGenerator.legalMovesFrom(board, Square(4, 0)).find { it.isCastleKingSide }
        assertNotNull(move)
        board.applyMove(move)

        assertEquals(Piece(PieceType.KING, Color.WHITE), board.pieceAt(Square(6, 0)))
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), board.pieceAt(Square(5, 0)))
        assertNull(board.pieceAt(Square(4, 0)))
        assertNull(board.pieceAt(Square(7, 0)))
    }

    @Test
    fun `en passant capture is legal immediately after a double pawn push`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(4, 4).index] = Piece(PieceType.PAWN, Color.WHITE) // e5
        board.squares[Square(3, 6).index] = Piece(PieceType.PAWN, Color.BLACK) // d7
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        board.sideToMove = Color.BLACK

        val doublePush = Move(Square(3, 6), Square(3, 4))
        board.applyMove(doublePush)
        assertEquals(Square(3, 5), board.enPassantTarget)

        val epMove = MoveGenerator.legalMovesFrom(board, Square(4, 4)).find { it.isEnPassant }
        assertNotNull(epMove)
        board.applyMove(epMove)

        assertEquals(Piece(PieceType.PAWN, Color.WHITE), board.pieceAt(Square(3, 5)))
        assertNull(board.pieceAt(Square(3, 4)))
        assertNull(board.pieceAt(Square(4, 4)))
    }

    @Test
    fun `pawn promotes to queen by default`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // a7
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        board.sideToMove = Color.WHITE

        val game = ChessGame()
        // Replace game board contents directly for this scenario.
        for (i in 0..63) game.board.squares[i] = board.squares[i]
        game.board.sideToMove = Color.WHITE

        val move = game.tryMove(Square(0, 6), Square(0, 7))
        assertNotNull(move)
        assertEquals(Piece(PieceType.QUEEN, Color.WHITE), game.board.pieceAt(Square(0, 7)))
    }

    @Test
    fun `king cannot move into check`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        board.squares[Square(0, 1).index] = Piece(PieceType.ROOK, Color.BLACK) // a2, attacks rank 2
        board.sideToMove = Color.WHITE

        val moves = MoveGenerator.legalMovesFrom(board, Square(4, 0))
        assertTrue(moves.none { it.to == Square(4, 1) })
    }

    @Test
    fun `stalemate is detected`() {
        val board = Board()
        board.squares.fill(null)
        // Classic stalemate: black king a8, white king c7, white queen b6 -> black to move, no legal moves, not in check.
        board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.BLACK) // a8
        board.squares[Square(2, 6).index] = Piece(PieceType.KING, Color.WHITE) // c7
        board.squares[Square(1, 5).index] = Piece(PieceType.QUEEN, Color.WHITE) // b6
        board.sideToMove = Color.BLACK

        val game = ChessGame()
        for (i in 0..63) game.board.squares[i] = board.squares[i]
        game.board.sideToMove = Color.BLACK

        assertEquals(GameStatus.STALEMATE, game.status())
    }
}
