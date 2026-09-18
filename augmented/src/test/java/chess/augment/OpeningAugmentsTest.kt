package chess.augment

import chess.AugmentedChessGame
import chess.AugmentedGameStatus
import chess.Color
import chess.Piece
import chess.PieceType
import chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningAugmentsTest {

    @Test
    fun `queens cavalry replaces only the picking side's d-pawn with a knight`() {
        val game = AugmentedChessGame(whiteAugment = OpeningAugments.QUEENS_CAVALRY, blackAugment = null)
        assertEquals(PieceType.KNIGHT, game.board.pieceAt(Square(3, 1))?.type) // d2
        assertEquals(Color.WHITE, game.board.pieceAt(Square(3, 1))?.color)
        assertEquals(PieceType.PAWN, game.board.pieceAt(Square(3, 6))?.type) // d7 untouched (Black didn't pick it)
    }

    @Test
    fun `early promotion lets a pawn promote two ranks sooner`() {
        val game = AugmentedChessGame(whiteAugment = OpeningAugments.EARLY_PROMOTION, blackAugment = null)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 4).index] = Piece(PieceType.PAWN, Color.WHITE) // a5
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        // Standard chess would need a2 to reach a8; Early Promotion promotes at rank 6 (a6) instead.
        val applied = game.tryMove(Square(0, 4), Square(0, 5), PieceType.QUEEN)
        assertNotNull(applied)
        assertEquals(PieceType.QUEEN, game.board.pieceAt(Square(0, 5))?.type)
    }

    @Test
    fun `hasty promotion cannot produce a queen or rook`() {
        val game = AugmentedChessGame(whiteAugment = OpeningAugments.HASTY_PROMOTION, blackAugment = null)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 3).index] = Piece(PieceType.PAWN, Color.WHITE) // a4
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        // Hasty Promotion's rank for White is 4 (a5) — one step from a4.
        val choices = game.movesFrom(Square(0, 3)).filter { it.to == Square(0, 4) }.mapNotNull { it.promotion }
        assertEquals(setOf(PieceType.BISHOP, PieceType.KNIGHT), choices.toSet())

        // Asking for a queen anyway falls back to whatever IS available rather than crashing.
        val applied = game.tryMove(Square(0, 3), Square(0, 4), PieceType.QUEEN)
        assertNotNull(applied)
        assertTrue(game.board.pieceAt(Square(0, 4))?.type in setOf(PieceType.BISHOP, PieceType.KNIGHT))
    }

    @Test
    fun `a lowered promotion rank is honored even on the two-square opening move`() {
        // A custom (test-only) augment whose promotion rank is reachable straight from White's
        // pawn start rank via the two-square opening move — regression check for addPawnMove
        // being wired into that code path too, not just the single-step move.
        val steepAugment = OpeningAugment(
            id = "test_steep",
            displayName = "test",
            description = "test",
            cost = 0f,
            promotionRankOverride = { color -> if (color == Color.WHITE) 3 else 4 }
        )
        val game = AugmentedChessGame(whiteAugment = steepAugment, blackAugment = null)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 1).index] = Piece(PieceType.PAWN, Color.WHITE) // a2, start rank
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        val moves = game.movesFrom(Square(0, 1)).filter { it.to == Square(0, 3) }
        assertEquals(4, moves.size) // one per promotion choice, not a single plain move
        assertTrue(moves.all { it.promotion != null })
    }

    @Test
    fun `a side with no augment plays exactly like standard chess promotion`() {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        game.board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // a7
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        val choices = game.movesFrom(Square(0, 6)).filter { it.to == Square(0, 7) }.mapNotNull { it.promotion }
        assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), choices.toSet())
        val applied = game.tryMove(Square(0, 6), Square(0, 7), PieceType.QUEEN)
        assertNotNull(applied)
        assertEquals(PieceType.QUEEN, game.board.pieceAt(Square(0, 7))?.type)
    }

    @Test
    fun `byId resolves known ids and returns null for unknown ones`() {
        assertEquals(OpeningAugments.QUEENS_CAVALRY, OpeningAugments.byId("queens_cavalry"))
        assertNull(OpeningAugments.byId("does_not_exist"))
        assertNull(OpeningAugments.byId(null))
    }

    @Test
    fun `capturing the enemy king still ends the game with an augment active`() {
        val game = AugmentedChessGame(whiteAugment = OpeningAugments.QUEENS_CAVALRY)
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 1).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        game.tryMove(Square(4, 0), Square(4, 1))
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }
}
