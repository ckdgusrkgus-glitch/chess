package chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AugmentedChessGameTest {

    @Test
    fun `starting position is ongoing`() {
        val game = AugmentedChessGame()
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    @Test
    fun `a king may move into an attacked square (no longer illegal)`() {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        game.board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.BLACK) // a8, out of the way
        game.board.squares[Square(4, 4).index] = Piece(PieceType.ROOK, Color.BLACK) // e5, controls the whole e-file
        game.board.sideToMove = Color.WHITE

        // e1-e2 walks onto a square the black rook covers along the e-file — illegal in standard
        // chess, legal here.
        val moves = game.movesFrom(Square(4, 0))
        assertTrue(moves.any { it.to == Square(4, 1) })
        val applied = game.tryMove(Square(4, 0), Square(4, 1))
        assertNotNull(applied)
        assertEquals(PieceType.KING, game.board.pieceAt(Square(4, 1))?.type)
    }

    @Test
    fun `a pinned piece may still move and expose its own king (pins don't apply)`() {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        game.board.squares[Square(4, 1).index] = Piece(PieceType.KNIGHT, Color.WHITE) // e2, "pinned"
        game.board.squares[Square(4, 7).index] = Piece(PieceType.ROOK, Color.BLACK) // e8, pins along e-file
        game.board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        // In standard chess this knight can't move (would expose the king to the rook). Here it can.
        val moves = game.movesFrom(Square(4, 1))
        assertTrue(moves.isNotEmpty())
    }

    @Test
    fun `capturing the enemy king ends the game for the capturing side`() {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        game.board.squares[Square(4, 1).index] = Piece(PieceType.KING, Color.BLACK) // e2, adjacent
        game.board.sideToMove = Color.WHITE

        val applied = game.tryMove(Square(4, 0), Square(4, 1))
        assertNotNull(applied)
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }

    @Test
    fun `a side with zero legal moves loses even if not in check (unified stalemate rule)`() {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        // Black king boxed into the a8 corner by its own pawns (own-color occupancy is the only
        // thing that stops a king move now, since moving into check is legal). Every other black
        // pawn on the a/b files is placed so its forward square is always occupied (blocking the
        // push) and its diagonal-capture square is always empty or black (never an enemy to take),
        // so none of them has a move either — down to the back rank, where a pawn's own forward
        // and diagonal targets fall off the board and it always has zero moves regardless.
        game.board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.BLACK) // a8
        for (rank in 0..6) {
            game.board.squares[Square(0, rank).index] = Piece(PieceType.PAWN, Color.BLACK) // a1..a7
        }
        for (rank in 0..7) {
            game.board.squares[Square(1, rank).index] = Piece(PieceType.PAWN, Color.BLACK) // b1..b8
        }
        game.board.squares[Square(7, 0).index] = Piece(PieceType.KING, Color.WHITE) // h1, far away
        game.board.sideToMove = Color.BLACK

        assertFalse(game.board.isInCheck(Color.BLACK))
        assertTrue(game.movesForSideToMove().isEmpty())
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }

    @Test
    fun `promotion still offers all four piece choices`() {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // a7
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        val applied = game.tryMove(Square(0, 6), Square(0, 7), PieceType.ROOK)
        assertNotNull(applied)
        assertEquals(PieceType.ROOK, game.board.pieceAt(Square(0, 7))?.type)
    }
}
