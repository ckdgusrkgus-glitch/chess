package chess.augment

import chess.AugmentedChessGame
import chess.Color
import chess.Piece
import chess.PieceType
import chess.Square
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiddleAugmentsTest {

    private fun minimalKingsAndPawnBoard(pawnSquare: Square, pawnColor: Color): AugmentedChessGame {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.squares[pawnSquare.index] = Piece(PieceType.PAWN, pawnColor)
        game.board.sideToMove = if (pawnColor == Color.WHITE) Color.WHITE else Color.BLACK
        return game
    }

    @Test
    fun `without retreat a pawn has no backward move at all`() {
        val game = minimalKingsAndPawnBoard(Square(3, 3), Color.WHITE) // d4
        assertTrue(game.movesFrom(Square(3, 3)).none { it.to == Square(3, 2) })
    }

    @Test
    fun `retreat lets a pawn step one square backward into an empty square`() {
        val game = minimalKingsAndPawnBoard(Square(3, 3), Color.WHITE) // d4
        game.applyMiddleAugment(MiddleAugments.RETREAT, Color.WHITE)
        assertTrue(game.movesFrom(Square(3, 3)).any { it.to == Square(3, 2) }) // d3
    }

    @Test
    fun `retreat never grants a two-square backward move`() {
        val game = minimalKingsAndPawnBoard(Square(3, 3), Color.WHITE) // d4
        game.applyMiddleAugment(MiddleAugments.RETREAT, Color.WHITE)
        assertTrue(game.movesFrom(Square(3, 3)).none { it.to == Square(3, 1) }) // d2, two squares back
    }

    @Test
    fun `retreat lets a pawn capture backward-diagonally`() {
        val game = minimalKingsAndPawnBoard(Square(3, 3), Color.WHITE) // d4
        game.board.squares[Square(2, 2).index] = Piece(PieceType.KNIGHT, Color.BLACK) // c3, backward-diagonal from d4
        game.applyMiddleAugment(MiddleAugments.RETREAT, Color.WHITE)
        assertNotNull(game.tryMove(Square(3, 3), Square(2, 2)))
        assertEqualsPiece(PieceType.PAWN, Color.WHITE, game, Square(2, 2))
    }

    @Test
    fun `retreat granted to white does not affect black`() {
        val game = minimalKingsAndPawnBoard(Square(3, 4), Color.BLACK) // d5
        game.applyMiddleAugment(MiddleAugments.RETREAT, Color.WHITE)
        // Black's "backward" is toward rank 7 (its own back rank) — must still be absent.
        assertTrue(game.movesFrom(Square(3, 4)).none { it.to == Square(3, 5) }) // d6
    }

    private fun minimalKingsBoard(): AugmentedChessGame {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK) // e8
        return game
    }

    @Test
    fun `without cavalry a king has no knight-shaped move`() {
        val game = minimalKingsBoard()
        assertTrue(game.movesFrom(Square(4, 0)).none { it.to == Square(5, 2) }) // f3, a knight jump from e1
    }

    @Test
    fun `cavalry lets a king move like a knight, in addition to its normal steps`() {
        val game = minimalKingsBoard()
        game.applyMiddleAugment(MiddleAugments.CAVALRY, Color.WHITE)
        val targets = game.movesFrom(Square(4, 0)).map { it.to }
        assertTrue(targets.contains(Square(5, 2))) // f3, a knight jump
        assertTrue(targets.contains(Square(5, 1))) // f2, still a normal one-step move
    }

    @Test
    fun `cavalry granted to white does not affect black`() {
        val game = minimalKingsBoard()
        game.applyMiddleAugment(MiddleAugments.CAVALRY, Color.WHITE)
        assertTrue(game.movesFrom(Square(4, 7)).none { it.to == Square(5, 5) }) // f6, a knight jump from e8
    }

    /**
     * Shuffles the b1/b8 knights out to c3/c6 and back, for [totalPlies] total half-moves. Reads
     * whether each knight is currently "out" from the board itself rather than a local flag reset to
     * false on entry — this can be called multiple times on the same [game], and a knight left out
     * from a previous call must not be assumed to still be home.
     */
    private fun shuffleKnights(game: AugmentedChessGame, totalPlies: Int) {
        var whiteOut = game.board.pieceAt(Square(2, 2))?.color == Color.WHITE
        var blackOut = game.board.pieceAt(Square(2, 5))?.color == Color.BLACK
        repeat(totalPlies) {
            if (game.board.sideToMove == Color.WHITE) {
                val move = if (!whiteOut) game.tryMove(Square(1, 0), Square(2, 2)) else game.tryMove(Square(2, 2), Square(1, 0))
                assertNotNull(move)
                whiteOut = !whiteOut
            } else {
                val move = if (!blackOut) game.tryMove(Square(1, 7), Square(2, 5)) else game.tryMove(Square(2, 5), Square(1, 7))
                assertNotNull(move)
                blackOut = !blackOut
            }
        }
    }

    @Test
    fun `middle draft is not due before ply 10, and becomes due at exactly ply 10`() {
        val game = AugmentedChessGame()
        assertFalse(game.isMiddleDraftDue())
        shuffleKnights(game, 9)
        assertFalse(game.isMiddleDraftDue())
        shuffleKnights(game, 1)
        assertTrue(game.isMiddleDraftDue())
    }

    @Test
    fun `marking the middle draft offered suppresses it until the next resetBoard`() {
        val game = AugmentedChessGame()
        shuffleKnights(game, 10)
        assertTrue(game.isMiddleDraftDue())
        game.markMiddleDraftOffered()
        assertFalse(game.isMiddleDraftDue())
        shuffleKnights(game, 5) // still past ply 10, but already offered this game
        assertFalse(game.isMiddleDraftDue())

        game.resetBoard()
        assertFalse(game.isMiddleDraftDue())
        shuffleKnights(game, 10)
        assertTrue(game.isMiddleDraftDue())
    }

    @Test
    fun `resetBoard clears every middle augment effect granted so far`() {
        val game = minimalKingsBoard()
        game.applyMiddleAugment(MiddleAugments.CAVALRY, Color.WHITE)
        assertTrue(game.movesFrom(Square(4, 0)).any { it.to == Square(5, 2) })

        game.resetBoard()
        // resetBoard() also restores the standard starting position, so re-check from e1 there.
        assertTrue(game.movesFrom(Square(4, 0)).none { it.to == Square(5, 2) })
    }

    private fun assertEqualsPiece(type: PieceType, color: Color, game: AugmentedChessGame, square: Square) {
        val piece = game.board.pieceAt(square)
        assertNotNull(piece)
        assertTrue(piece!!.type == type && piece.color == color)
    }
}
