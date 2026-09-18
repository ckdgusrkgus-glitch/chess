package chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for making castling's home squares configurable (chess.Board's
 * white/blackKingHome etc.) instead of hardcoded rank 0/7 — needed so False Start (which
 * relocates a side's whole back rank) doesn't silently break that side's castling.
 */
class CastlingHomeSquaresTest {

    @Test
    fun `standard chess castling still works from the default home squares`() {
        val board = Board().apply { setup() }
        // Clear the squares between king and rook on White's kingside, as a real game would after
        // the knight and bishop move out of the way.
        board.squares[Square(5, 0).index] = null // f1
        board.squares[Square(6, 0).index] = null // g1

        val moves = MoveGenerator.pseudoLegalMoves(board, Square(4, 0))
        val castle = moves.find { it.isCastleKingSide }
        assertNotNull(castle)

        board.applyMove(castle!!)
        assertEquals(PieceType.KING, board.pieceAt(Square(6, 0))?.type) // g1
        assertEquals(PieceType.ROOK, board.pieceAt(Square(5, 0))?.type) // f1
        assertEquals(null, board.pieceAt(Square(4, 0))) // e1 vacated
        assertEquals(null, board.pieceAt(Square(7, 0))) // h1 vacated
    }

    @Test
    fun `something landing on a rook's home square revokes that side's rights even if the rook never moved`() {
        // Board.applyMove doesn't validate move legality/geometry — it just applies whatever Move
        // it's given — so this directly exercises invalidateCastlingIfRookHome's "to" check in
        // isolation, standing in for "White's own a1 rook gets captured there".
        val board = Board().apply { setup() }
        board.squares[Square(0, 0).index] = null // remove White's queenside rook (as if just captured)
        board.squares[Square(1, 5).index] = Piece(PieceType.KNIGHT, Color.BLACK) // b6
        assertTrue(board.whiteCanCastleQueenSide)

        board.applyMove(Move(Square(1, 5), Square(0, 0)))
        assertFalse(board.whiteCanCastleQueenSide)
    }

    @Test
    fun `false start relocates a side's castling home squares and castling works from there`() {
        val board = Board().apply { setup() }
        chess.augment.OpeningAugments.FALSE_START.setupTransform(board, Color.WHITE)

        assertEquals(Square(4, 2), board.whiteKingHome)
        assertEquals(Square(0, 2), board.whiteQueenRookHome)
        assertEquals(Square(7, 2), board.whiteKingRookHome)
        assertEquals(PieceType.KING, board.pieceAt(Square(4, 2))?.type)
        assertEquals(PieceType.ROOK, board.pieceAt(Square(7, 2))?.type)
        assertEquals(PieceType.PAWN, board.pieceAt(Square(4, 3))?.type)
        assertTrue(board.squares[Square(4, 0).index] == null) // e1 vacated

        // Clear the kingside gap on the NEW back rank (f3/g3) and confirm castling is generated
        // and executes correctly there, not on the old rank 0.
        board.squares[Square(5, 2).index] = null
        board.squares[Square(6, 2).index] = null
        val moves = MoveGenerator.pseudoLegalMoves(board, Square(4, 2))
        val castle = moves.find { it.isCastleKingSide }
        assertNotNull(castle)
        board.applyMove(castle!!)
        assertEquals(PieceType.KING, board.pieceAt(Square(6, 2))?.type)
        assertEquals(PieceType.ROOK, board.pieceAt(Square(5, 2))?.type)
    }

    @Test
    fun `false start leaves the side that didn't pick it on the normal home squares`() {
        val board = Board().apply { setup() }
        chess.augment.OpeningAugments.FALSE_START.setupTransform(board, Color.WHITE)

        assertEquals(Square(4, 7), board.blackKingHome)
        assertEquals(PieceType.KING, board.pieceAt(Square(4, 7))?.type)
        assertEquals(PieceType.PAWN, board.pieceAt(Square(4, 6))?.type)
    }

    @Test
    fun `moving a rook off its false-start home revokes castling on that side only`() {
        val board = Board().apply { setup() }
        chess.augment.OpeningAugments.FALSE_START.setupTransform(board, Color.WHITE)

        assertTrue(board.whiteCanCastleQueenSide)
        assertTrue(board.whiteCanCastleKingSide)
        board.applyMove(Move(Square(0, 2), Square(0, 3))) // the relocated queenside rook steps forward
        assertFalse(board.whiteCanCastleQueenSide)
        assertTrue(board.whiteCanCastleKingSide)
    }
}
