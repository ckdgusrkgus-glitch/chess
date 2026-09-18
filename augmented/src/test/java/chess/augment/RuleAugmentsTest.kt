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

/**
 * Covers the three 규칙 증강 (rule augments) implemented so far: 매너, 왕관, 초월. 재활용 already has its
 * own test file, TurtlingAndRecycleTest.
 */
class RuleAugmentsTest {

    // ---- 매너 (Manners): a piece that just captured can't capture again until a normal move ----

    /** White king a1, black king h8, a white knight on d4, and two capture targets reachable in a
     *  d4 -> b3 -> d4 -> c5 -> e6 knight tour: a black pawn on b3, a black rook on d4's follow-up
     *  target... concretely: b3 (first capture), then (from b3) d4 (second capture, now empty since
     *  the knight vacated it), and (from b3's alternate, non-capturing branch) c5 then e6. */
    private fun mannersSetup(mannersEnabled: Boolean): AugmentedChessGame {
        val game = AugmentedChessGame(mannersRuleEnabled = mannersEnabled)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.WHITE) // a1
        game.board.squares[Square(7, 7).index] = Piece(PieceType.KING, Color.BLACK) // h8
        game.board.squares[Square(3, 3).index] = Piece(PieceType.KNIGHT, Color.WHITE) // d4
        game.board.squares[Square(1, 2).index] = Piece(PieceType.PAWN, Color.BLACK) // b3
        game.board.squares[Square(4, 5).index] = Piece(PieceType.BISHOP, Color.BLACK) // e6
        game.board.sideToMove = Color.WHITE
        return game
    }

    private fun shuffleBlackKing(game: AugmentedChessGame, toH7: Boolean) {
        val move = if (toH7) game.tryMove(Square(7, 7), Square(7, 6)) else game.tryMove(Square(7, 6), Square(7, 7))
        assertNotNull(move)
    }

    @Test
    fun `without manners, the same piece can capture twice in a row`() {
        val game = mannersSetup(mannersEnabled = false)
        assertNotNull(game.tryMove(Square(3, 3), Square(1, 2))) // d4xb3
        shuffleBlackKing(game, toH7 = true)
        game.board.squares[Square(3, 3).index] = Piece(PieceType.ROOK, Color.BLACK) // d4, second target
        assertNotNull(game.tryMove(Square(1, 2), Square(3, 3))) // b3xd4, same knight, second capture in a row
    }

    @Test
    fun `manners blocks a second consecutive capture, but a normal move is still available`() {
        val game = mannersSetup(mannersEnabled = true)
        assertNotNull(game.tryMove(Square(3, 3), Square(1, 2))) // d4xb3
        shuffleBlackKing(game, toH7 = true)
        game.board.squares[Square(3, 3).index] = Piece(PieceType.ROOK, Color.BLACK) // d4, would-be second target

        assertTrue(game.movesFrom(Square(1, 2)).none { it.to == Square(3, 3) })
        assertNull(game.tryMove(Square(1, 2), Square(3, 3)))

        assertTrue(game.movesFrom(Square(1, 2)).any { it.to == Square(2, 4) }) // c5, empty, non-capturing
        assertNotNull(game.tryMove(Square(1, 2), Square(2, 4))) // b3-c5, a normal move
    }

    @Test
    fun `manners' bar clears once the piece makes a normal move, allowing it to capture again later`() {
        val game = mannersSetup(mannersEnabled = true)
        assertNotNull(game.tryMove(Square(3, 3), Square(1, 2))) // d4xb3
        shuffleBlackKing(game, toH7 = true)
        assertNotNull(game.tryMove(Square(1, 2), Square(2, 4))) // b3-c5, normal move, clears the bar
        shuffleBlackKing(game, toH7 = false)
        assertNotNull(game.tryMove(Square(2, 4), Square(4, 5))) // c5xe6, capturing again, now allowed
    }

    // ---- 왕관 (Crown): whoever holds one of the 4 center squares for 10 plies wins ----

    /** White king a1, black king h8, and a white knight on all 4 candidate crown squares (d4/d5/e4/e5)
     *  — whichever one [AugmentedChessGame] randomly picked as this game's actual crown square is
     *  therefore always occupied by White from the start, so these tests don't need to know which
     *  one it was. */
    private fun crownSetup(crownEnabled: Boolean): AugmentedChessGame {
        val game = AugmentedChessGame(crownRuleEnabled = crownEnabled)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.WHITE) // a1
        game.board.squares[Square(7, 7).index] = Piece(PieceType.KING, Color.BLACK) // h8
        for (sq in listOf(Square(3, 3), Square(3, 4), Square(4, 3), Square(4, 4))) {
            game.board.squares[sq.index] = Piece(PieceType.KNIGHT, Color.WHITE)
        }
        game.board.sideToMove = Color.WHITE
        return game
    }

    /** Shuffles the a1/h8 kings back and forth, [totalPlies] total half-moves, without touching any
     *  of the 4 center squares. */
    private fun shuffleCornerKings(game: AugmentedChessGame, totalPlies: Int) {
        var whiteOut = game.board.pieceAt(Square(0, 1))?.color == Color.WHITE
        var blackOut = game.board.pieceAt(Square(7, 6))?.color == Color.BLACK
        repeat(totalPlies) {
            if (game.board.sideToMove == Color.WHITE) {
                val move = if (!whiteOut) game.tryMove(Square(0, 0), Square(0, 1)) else game.tryMove(Square(0, 1), Square(0, 0))
                assertNotNull(move)
                whiteOut = !whiteOut
            } else {
                val move = if (!blackOut) game.tryMove(Square(7, 7), Square(7, 6)) else game.tryMove(Square(7, 6), Square(7, 7))
                assertNotNull(move)
                blackOut = !blackOut
            }
        }
    }

    @Test
    fun `crown wins for whoever holds it 10 plies, whichever of the 4 squares was picked`() {
        val game = crownSetup(crownEnabled = true)
        shuffleCornerKings(game, 10)
        assertEquals(AugmentedGameStatus.WHITE_WINS, game.status())
    }

    @Test
    fun `crown is not won yet one ply short of the threshold`() {
        val game = crownSetup(crownEnabled = true)
        shuffleCornerKings(game, 9)
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    @Test
    fun `without the crown rule, holding all 4 center squares for 10 plies does nothing`() {
        val game = crownSetup(crownEnabled = false)
        shuffleCornerKings(game, 10)
        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    @Test
    fun `losing the true crown square resets the streak, regardless of which square it was`() {
        val game = crownSetup(crownEnabled = true)
        game.board.squares[Square(1, 2).index] = Piece(PieceType.KNIGHT, Color.BLACK) // b3, guards d4
        game.board.squares[Square(1, 5).index] = Piece(PieceType.KNIGHT, Color.BLACK) // b6, guards d5
        game.board.squares[Square(2, 2).index] = Piece(PieceType.KNIGHT, Color.BLACK) // c3, guards e4
        game.board.squares[Square(6, 5).index] = Piece(PieceType.KNIGHT, Color.BLACK) // g6, guards e5

        // White fills 4 turns while Black spends its 4 turns capturing every center-square knight —
        // whichever one is the true crown square gets captured somewhere in here, at the latest on
        // ply 8, so the streak (for whoever ends up holding it) can be at most a handful of plies —
        // nowhere near the 10-ply threshold, regardless of which square was the real one or when in
        // this sequence it changed hands.
        assertNotNull(game.tryMove(Square(0, 0), Square(0, 1))) // ply1 Ka1-a2
        assertNotNull(game.tryMove(Square(1, 2), Square(3, 3))) // ply2 Nb3xd4
        assertNotNull(game.tryMove(Square(0, 1), Square(0, 0))) // ply3 Ka2-a1
        assertNotNull(game.tryMove(Square(1, 5), Square(3, 4))) // ply4 Nb6xd5
        assertNotNull(game.tryMove(Square(0, 0), Square(0, 1))) // ply5 Ka1-a2
        assertNotNull(game.tryMove(Square(2, 2), Square(4, 3))) // ply6 Nc3xe4
        assertNotNull(game.tryMove(Square(0, 1), Square(0, 0))) // ply7 Ka2-a1
        assertNotNull(game.tryMove(Square(6, 5), Square(4, 4))) // ply8 Ng6xe5

        assertEquals(AugmentedGameStatus.ONGOING, game.status())
    }

    // ---- 초월 (Transcend): a piece that captures steps to the next tier (폰→나이트→룩→퀸, 비숍→룩→퀸) ----

    private fun transcendSetup(transcendEnabled: Boolean, moverType: PieceType, moverSquare: Square, targetSquare: Square): AugmentedChessGame {
        val game = AugmentedChessGame(transcendRuleEnabled = transcendEnabled)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.WHITE) // a1
        game.board.squares[Square(7, 7).index] = Piece(PieceType.KING, Color.BLACK) // h8
        game.board.squares[moverSquare.index] = Piece(moverType, Color.WHITE)
        game.board.squares[targetSquare.index] = Piece(PieceType.PAWN, Color.BLACK)
        game.board.sideToMove = Color.WHITE
        return game
    }

    @Test
    fun `without transcend, a capturing pawn stays a pawn`() {
        val game = transcendSetup(false, PieceType.PAWN, Square(3, 3), Square(4, 4)) // d4xe5
        assertNotNull(game.tryMove(Square(3, 3), Square(4, 4)))
        assertEquals(PieceType.PAWN, game.board.pieceAt(Square(4, 4))?.type)
    }

    @Test
    fun `transcend turns a capturing pawn into a knight`() {
        val game = transcendSetup(true, PieceType.PAWN, Square(3, 3), Square(4, 4)) // d4xe5
        assertNotNull(game.tryMove(Square(3, 3), Square(4, 4)))
        assertEquals(PieceType.KNIGHT, game.board.pieceAt(Square(4, 4))?.type)
        assertEquals(Color.WHITE, game.board.pieceAt(Square(4, 4))?.color)
    }

    @Test
    fun `transcend turns a capturing knight into a rook`() {
        val game = transcendSetup(true, PieceType.KNIGHT, Square(3, 3), Square(1, 2)) // d4xb3
        assertNotNull(game.tryMove(Square(3, 3), Square(1, 2)))
        assertEquals(PieceType.ROOK, game.board.pieceAt(Square(1, 2))?.type)
    }

    @Test
    fun `transcend turns a capturing bishop into a rook too`() {
        val game = transcendSetup(true, PieceType.BISHOP, Square(3, 3), Square(5, 5)) // d4xf6
        assertNotNull(game.tryMove(Square(3, 3), Square(5, 5)))
        assertEquals(PieceType.ROOK, game.board.pieceAt(Square(5, 5))?.type)
    }

    @Test
    fun `transcend turns a capturing rook into a queen`() {
        val game = transcendSetup(true, PieceType.ROOK, Square(3, 3), Square(3, 6)) // d4xd7
        assertNotNull(game.tryMove(Square(3, 3), Square(3, 6)))
        assertEquals(PieceType.QUEEN, game.board.pieceAt(Square(3, 6))?.type)
    }

    @Test
    fun `transcend leaves a capturing queen as a queen`() {
        val game = transcendSetup(true, PieceType.QUEEN, Square(3, 3), Square(3, 6)) // d4xd7
        assertNotNull(game.tryMove(Square(3, 3), Square(3, 6)))
        assertEquals(PieceType.QUEEN, game.board.pieceAt(Square(3, 6))?.type)
    }

    @Test
    fun `transcend leaves a capturing king as a king`() {
        val game = AugmentedChessGame(transcendRuleEnabled = true)
        game.board.squares.fill(null)
        game.board.squares[Square(3, 3).index] = Piece(PieceType.KING, Color.WHITE) // d4
        game.board.squares[Square(7, 7).index] = Piece(PieceType.KING, Color.BLACK) // h8
        game.board.squares[Square(3, 4).index] = Piece(PieceType.PAWN, Color.BLACK) // d5, adjacent
        game.board.sideToMove = Color.WHITE

        assertNotNull(game.tryMove(Square(3, 3), Square(3, 4))) // Kd4xd5
        assertEquals(PieceType.KING, game.board.pieceAt(Square(3, 4))?.type)
        assertEquals(Color.WHITE, game.board.pieceAt(Square(3, 4))?.color)
    }

    @Test
    fun `a capturing move that also promotes keeps the chosen promotion, and does not additionally transcend`() {
        val game = AugmentedChessGame(transcendRuleEnabled = true)
        game.board.squares.fill(null)
        game.board.squares[Square(0, 0).index] = Piece(PieceType.KING, Color.WHITE) // a1
        game.board.squares[Square(7, 0).index] = Piece(PieceType.KING, Color.BLACK) // h1, far from the action
        game.board.squares[Square(3, 6).index] = Piece(PieceType.PAWN, Color.WHITE) // d7
        game.board.squares[Square(4, 7).index] = Piece(PieceType.KNIGHT, Color.BLACK) // e8
        game.board.sideToMove = Color.WHITE

        assertNotNull(game.tryMove(Square(3, 6), Square(4, 7), PieceType.ROOK)) // d7xe8=R
        // If transcend also fired on this move, a promoted rook's next tier (QUEEN) would show up
        // here instead — the move.promotion != null guard in onMoveApplied is what prevents that.
        assertEquals(PieceType.ROOK, game.board.pieceAt(Square(4, 7))?.type)
    }
}
