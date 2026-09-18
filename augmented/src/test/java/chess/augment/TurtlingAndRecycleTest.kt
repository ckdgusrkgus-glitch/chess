package chess.augment

import chess.AugmentedChessGame
import chess.Color
import chess.Piece
import chess.PieceType
import chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurtlingAndRecycleTest {

    /**
     * Shuffles whichever king it actually is to move, back and forth, for [totalPlies] total
     * half-moves. Reads chess.Board.sideToMove rather than assuming White always moves first —
     * this can be called after other moves already happened (e.g. a pawn push), leaving Black to
     * move first.
     */
    private fun shuffleKings(game: AugmentedChessGame, totalPlies: Int) {
        var whiteAtHome = true
        var blackAtHome = true
        repeat(totalPlies) {
            if (game.board.sideToMove == Color.WHITE) {
                val (from, to) = if (whiteAtHome) Square(4, 0) to Square(4, 1) else Square(4, 1) to Square(4, 0)
                assertNotNull(game.tryMove(from, to))
                whiteAtHome = !whiteAtHome
            } else {
                val (from, to) = if (blackAtHome) Square(4, 7) to Square(4, 6) else Square(4, 6) to Square(4, 7)
                assertNotNull(game.tryMove(from, to))
                blackAtHome = !blackAtHome
            }
        }
    }

    private fun minimalKingsAndPawnBoard(pawnSquare: Square, pawnColor: Color): AugmentedChessGame {
        val turtling = OpeningAugments.turtling(pawnSquare.file)
        val game = if (pawnColor == Color.WHITE) {
            AugmentedChessGame(whiteAugment = turtling)
        } else {
            AugmentedChessGame(blackAugment = turtling)
        }
        game.board.squares.fill(null)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.squares[pawnSquare.index] = Piece(PieceType.PAWN, pawnColor)
        game.board.sideToMove = Color.WHITE
        return game
    }

    @Test
    fun `turtling promotes the designated pawn once 14 plies have passed`() {
        val game = minimalKingsAndPawnBoard(Square(0, 1), Color.WHITE) // a2
        shuffleKings(game, 14)
        assertEquals(PieceType.QUEEN, game.board.pieceAt(Square(0, 1))?.type)
        assertEquals(Color.WHITE, game.board.pieceAt(Square(0, 1))?.color)
    }

    @Test
    fun `turtling has not fired yet one ply short of 14`() {
        val game = minimalKingsAndPawnBoard(Square(0, 1), Color.WHITE)
        shuffleKings(game, 13)
        assertEquals(PieceType.PAWN, game.board.pieceAt(Square(0, 1))?.type)
    }

    @Test
    fun `turtling follows the pawn if it moves, and still promotes it there`() {
        val game = minimalKingsAndPawnBoard(Square(0, 1), Color.WHITE) // a2
        assertNotNull(game.tryMove(Square(0, 1), Square(0, 2))) // a2-a3, ply 1
        shuffleKings(game, 13) // plies 2..14
        assertEquals(PieceType.QUEEN, game.board.pieceAt(Square(0, 2))?.type)
    }

    @Test
    fun `a captured turtling pawn never promotes, and the game keeps going without crashing`() {
        val game = minimalKingsAndPawnBoard(Square(0, 1), Color.WHITE)
        game.board.squares[Square(0, 1).index] = null // simulate the pawn having just been captured
        shuffleKings(game, 14)
        assertNull(game.board.pieceAt(Square(0, 1)))
    }

    @Test
    fun `a turtling pawn that promotes normally first is left alone at 14 plies`() {
        val game = minimalKingsAndPawnBoard(Square(0, 6), Color.WHITE) // a7, one step from promoting
        val applied = game.tryMove(Square(0, 6), Square(0, 7), PieceType.ROOK) // ply 1
        assertNotNull(applied)
        assertEquals(PieceType.ROOK, game.board.pieceAt(Square(0, 7))?.type)
        shuffleKings(game, 13) // plies 2..14
        assertEquals(PieceType.ROOK, game.board.pieceAt(Square(0, 7))?.type) // still a rook, not overwritten to queen
    }

    @Test
    fun `recycle blocks promotion entirely when nothing of that side has been captured yet`() {
        val game = AugmentedChessGame(recycleRuleEnabled = true)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // a7
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.sideToMove = Color.WHITE

        assertTrue(game.movesFrom(Square(0, 6)).none { it.to == Square(0, 7) })
        assertNull(game.tryMove(Square(0, 6), Square(0, 7), PieceType.QUEEN))
    }

    @Test
    fun `recycle allows promoting to exactly the piece type white has lost, once`() {
        val game = AugmentedChessGame(recycleRuleEnabled = true)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // a7
        game.board.squares[Square(1, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // b7, a second candidate
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.squares[Square(3, 3).index] = Piece(PieceType.KNIGHT, Color.WHITE) // d4, about to be captured
        game.board.squares[Square(0, 0).index] = Piece(PieceType.BISHOP, Color.BLACK) // a1, on the a1-d4 diagonal
        game.board.sideToMove = Color.BLACK

        // Black captures White's knight — White now has one "knight" credit.
        assertNotNull(game.tryMove(Square(0, 0), Square(3, 3)))

        val choices = game.movesFrom(Square(0, 6)).filter { it.to == Square(0, 7) }.mapNotNull { it.promotion }
        assertEquals(setOf(PieceType.KNIGHT), choices.toSet())

        assertNotNull(game.tryMove(Square(0, 6), Square(0, 7), PieceType.KNIGHT))
        assertEquals(PieceType.KNIGHT, game.board.pieceAt(Square(0, 7))?.type)

        // The one knight credit is now spent — the second pawn has nothing left to promote to.
        assertTrue(game.movesFrom(Square(1, 6)).none { it.to == Square(1, 7) })
    }

    @Test
    fun `recycle credits are tracked per color independently`() {
        val game = AugmentedChessGame(recycleRuleEnabled = true)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 1).index] = Piece(PieceType.PAWN, Color.BLACK) // a2, black pawn about to promote
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)
        game.board.squares[Square(3, 3).index] = Piece(PieceType.ROOK, Color.WHITE) // d4, White's own rook, about to be lost
        game.board.squares[Square(1, 4).index] = Piece(PieceType.KNIGHT, Color.BLACK) // b5, a knight's-move from d4
        game.board.sideToMove = Color.BLACK

        assertNotNull(game.tryMove(Square(1, 4), Square(3, 3))) // Black captures White's rook: a WHITE credit, not Black's.
        assertTrue(game.movesFrom(Square(0, 1)).none { it.to == Square(0, 0) }) // Black still has no credit of its own.
    }

    @Test
    fun `recycle has no effect when disabled, even with captures on the board`() {
        val game = AugmentedChessGame(recycleRuleEnabled = false)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 6).index] = Piece(PieceType.PAWN, Color.WHITE)
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE)
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK)

        val choices = game.movesFrom(Square(0, 6)).filter { it.to == Square(0, 7) }.mapNotNull { it.promotion }
        assertEquals(setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT), choices.toSet())
    }
}
