package chess.ai

import chess.Board
import chess.Color
import chess.MoveGenerator
import chess.Piece
import chess.PieceType
import chess.Square
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessAiTest {

    @Test
    fun `ai returns a legal move from the starting position`() {
        val board = Board().apply { setup() }
        val ai = ChessAi(AiLevel.BEGINNER, Random(42))
        val move = ai.chooseMove(board)
        assertTrue(move != null && MoveGenerator.legalMoves(board, Color.WHITE).contains(move))
    }

    @Test
    fun `ai at master level finds back rank mate in one`() {
        val board = Board()
        board.squares.fill(null)
        // White rook delivers mate on the back rank; the black king is boxed in by its own pawns.
        board.squares[Square(0, 0).index] = Piece(PieceType.ROOK, Color.WHITE) // a1
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        board.squares[Square(6, 7).index] = Piece(PieceType.KING, Color.BLACK) // g8
        board.squares[Square(5, 6).index] = Piece(PieceType.PAWN, Color.BLACK) // f7
        board.squares[Square(6, 6).index] = Piece(PieceType.PAWN, Color.BLACK) // g7
        board.squares[Square(7, 6).index] = Piece(PieceType.PAWN, Color.BLACK) // h7
        board.sideToMove = Color.WHITE

        val ai = ChessAi(AiLevel.MASTER, Random(1))
        val move = ai.chooseMove(board)
        assertTrue(move != null)

        val after = board.copy()
        after.applyMove(move!!)

        assertTrue(after.isInCheck(Color.BLACK))
        assertTrue(MoveGenerator.legalMoves(after, Color.BLACK).isEmpty())
    }
}
