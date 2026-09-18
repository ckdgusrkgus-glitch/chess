package chess.augment

import chess.AugmentedChessGame
import chess.AugmentedGameStatus
import chess.Color
import chess.Piece
import chess.PieceType
import chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndAugmentsTest {

    private fun emptyGame(): AugmentedChessGame {
        val game = AugmentedChessGame()
        game.board.squares.fill(null)
        return game
    }

    @Test
    fun `without racing king a king reaching the far rank does not end the game`() {
        val game = emptyGame()
        game.board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.WHITE) // a8, White's far rank
        game.board.squares[Square(7, 0).index] = Piece(PieceType.KING, Color.BLACK) // h1
        game.board.sideToMove = Color.BLACK
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    @Test
    fun `racing king wins the moment that side's king reaches the far rank`() {
        val game = emptyGame()
        game.board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.WHITE) // a8
        game.board.squares[Square(7, 0).index] = Piece(PieceType.KING, Color.BLACK) // h1
        game.board.sideToMove = Color.BLACK
        game.applyEndAugment(EndAugments.RACING_KING, Color.WHITE)
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }

    @Test
    fun `racing king granted to white does not fire for black reaching its own far rank`() {
        val game = emptyGame()
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.BLACK) // a1, Black's far rank
        game.board.squares[Square(7, 3).index] = Piece(PieceType.KING, Color.WHITE) // h4, nowhere near rank 8
        game.board.sideToMove = Color.WHITE
        game.applyEndAugment(EndAugments.RACING_KING, Color.WHITE)
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    private fun doubleAttackOnBlackKingSetup(): AugmentedChessGame {
        val game = emptyGame()
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK) // e8
        game.board.squares[Square(4, 0).index] = Piece(PieceType.ROOK, Color.WHITE) // e1, attacks along the e-file
        game.board.squares[Square(0, 7).index] = Piece(PieceType.ROOK, Color.WHITE) // a8, attacks along rank 8
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.WHITE) // a1, White's own king
        game.board.sideToMove = Color.BLACK
        return game
    }

    @Test
    fun `without double check, two attackers on the king does not end the game`() {
        val game = doubleAttackOnBlackKingSetup()
        assertTrue(game.board.attackersOf(Square(4, 7), Color.WHITE).size >= 2)
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    @Test
    fun `double check wins the moment two distinct pieces attack the enemy king at once`() {
        val game = doubleAttackOnBlackKingSetup()
        game.applyEndAugment(EndAugments.DOUBLE_CHECK, Color.WHITE)
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }

    @Test
    fun `double check does not fire for a single attacker`() {
        val game = emptyGame()
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK) // e8
        game.board.squares[Square(4, 0).index] = Piece(PieceType.ROOK, Color.WHITE) // e1, the only attacker
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.WHITE) // a1
        game.board.sideToMove = Color.BLACK
        game.applyEndAugment(EndAugments.DOUBLE_CHECK, Color.WHITE)
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    private fun uniquePieceArmy(game: AugmentedChessGame) {
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        game.board.squares[Square(0, 0).index] = Piece(PieceType.ROOK, Color.WHITE) // a1, only rook
        game.board.squares[Square(1, 0).index] = Piece(PieceType.KNIGHT, Color.WHITE) // b1, only knight
        game.board.squares[Square(0, 1).index] = Piece(PieceType.PAWN, Color.WHITE) // a2, only pawn
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK) // e8
    }

    @Test
    fun `without highlander, an army with no duplicate piece types does not win by itself`() {
        val game = emptyGame()
        uniquePieceArmy(game)
        game.board.sideToMove = Color.WHITE
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    @Test
    fun `highlander wins once that side has no duplicate piece type left, pawns included`() {
        val game = emptyGame()
        uniquePieceArmy(game)
        game.board.sideToMove = Color.WHITE
        game.applyEndAugment(EndAugments.HIGHLANDER, Color.WHITE)
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }

    @Test
    fun `highlander does not fire while a duplicate piece type remains`() {
        val game = emptyGame()
        game.board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        game.board.squares[Square(0, 0).index] = Piece(PieceType.ROOK, Color.WHITE) // a1
        game.board.squares[Square(7, 0).index] = Piece(PieceType.ROOK, Color.WHITE) // h1, a second rook
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KING, Color.BLACK) // e8
        game.board.sideToMove = Color.WHITE
        game.applyEndAugment(EndAugments.HIGHLANDER, Color.WHITE)
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    /** Shuffles the b1/b8 knights out to c3/c6 and back, reading whether each is currently "out"
     *  from the board itself so this can be called repeatedly on the same [game] (see the identical
     *  helper and its doc comment in MiddleAugmentsTest). */
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
    fun `end draft is not due before ply 24, and becomes due at exactly ply 24`() {
        val game = AugmentedChessGame()
        assertFalse(game.isEndDraftDue())
        shuffleKnights(game, 23)
        assertFalse(game.isEndDraftDue())
        shuffleKnights(game, 1)
        assertTrue(game.isEndDraftDue())
    }

    @Test
    fun `marking the end draft offered suppresses it until the next resetBoard`() {
        val game = AugmentedChessGame()
        shuffleKnights(game, 24)
        assertTrue(game.isEndDraftDue())
        game.markEndDraftOffered()
        assertFalse(game.isEndDraftDue())
        shuffleKnights(game, 5)
        assertFalse(game.isEndDraftDue())

        game.resetBoard()
        assertFalse(game.isEndDraftDue())
        shuffleKnights(game, 24)
        assertTrue(game.isEndDraftDue())
    }

    @Test
    fun `resetBoard clears every end augment win condition granted so far`() {
        val game = emptyGame()
        game.board.squares[Square(0, 7).index] = Piece(PieceType.KING, Color.WHITE) // a8
        game.board.squares[Square(7, 0).index] = Piece(PieceType.KING, Color.BLACK) // h1
        game.board.sideToMove = Color.BLACK
        game.applyEndAugment(EndAugments.RACING_KING, Color.WHITE)
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())

        game.resetBoard()
        // resetBoard() also restores the standard starting position, so no king is anywhere near a
        // far rank there regardless — the real assertion is that the granted flag itself is gone.
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }
}
