package chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoveNotationTest {

    @Test
    fun `round trips a simple move`() {
        val board = Board().apply { setup() }
        val move = MoveGenerator.legalMoves(board, Color.WHITE)
            .first { it.from == Square.fromAlgebraic("e2") && it.to == Square.fromAlgebraic("e4") }

        assertEquals("e2e4", move.toAlgebraic())
        assertEquals(move, parseAlgebraicMove(board, "e2e4"))
    }

    @Test
    fun `round trips a promotion move`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // a7
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        board.sideToMove = Color.WHITE

        val move = MoveGenerator.legalMoves(board, Color.WHITE)
            .first { it.to == Square.fromAlgebraic("a8") && it.promotion == PieceType.QUEEN }

        assertEquals("a7a8q", move.toAlgebraic())
        assertEquals(move, parseAlgebraicMove(board, "a7a8q"))
    }

    @Test
    fun `rejects malformed or illegal notation`() {
        val board = Board().apply { setup() }
        assertNull(parseAlgebraicMove(board, "zz"))
        assertNull(parseAlgebraicMove(board, "e2e5"))
    }

    @Test
    fun `replaying a full game via notation matches direct application`() {
        val board = Board().apply { setup() }
        val algebraic = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5")
        for (notation in algebraic) {
            val move = parseAlgebraicMove(board, notation)
            assertEquals(notation, move?.toAlgebraic())
            board.applyMove(move!!)
        }
        assertEquals(Color.BLACK, board.sideToMove)
    }
}
