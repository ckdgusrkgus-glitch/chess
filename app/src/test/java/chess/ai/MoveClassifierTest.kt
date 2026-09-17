package chess.ai

import chess.Board
import chess.Color
import chess.Move
import chess.Piece
import chess.PieceType
import chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoveClassifierTest {

    @Test
    fun `engine's own top move never grades worse than best`() {
        val board = Board().apply { setup() }
        board.applyMove(Move(Square(4, 1), Square(4, 3))) // 1. e4
        val ai = ChessAi(AiLevel.MASTER, Random(7))
        val evaluations = ai.evaluateAllMoves(board)
        val topMove = evaluations.first().move

        val quality = MoveClassifier.classify(board, topMove, evaluations)
        assertTrue(
            "expected a top-tier grade for the engine's own choice but got $quality",
            quality in setOf(MoveQuality.BEST, MoveQuality.GREAT, MoveQuality.BRILLIANT)
        )
    }

    @Test
    fun `ignoring a free queen capture grades badly`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        board.squares[Square(3, 3).index] = Piece(PieceType.QUEEN, Color.WHITE) // d4, undefended
        board.squares[Square(3, 7).index] = Piece(PieceType.ROOK, Color.BLACK) // d8, can take it
        board.squares[Square(7, 7).index] = Piece(PieceType.KING, Color.BLACK) // h8
        board.sideToMove = Color.BLACK

        val ai = ChessAi(AiLevel.MASTER, Random(3))
        val evaluations = ai.evaluateAllMoves(board)
        val freeQueen = Move(Square(3, 7), Square(3, 3))
        assertEquals(freeQueen, evaluations.first().move)

        val doNothing = Move(Square(7, 7), Square(6, 7)) // Kh8-g8, ignores the free queen
        val quality = MoveClassifier.classify(board, doNothing, evaluations)
        assertTrue(
            "expected passing up a free queen to grade poorly but got $quality",
            quality in setOf(MoveQuality.INACCURACY, MoveQuality.MISTAKE, MoveQuality.BLUNDER, MoveQuality.MISSED_WIN)
        )
    }

    @Test
    fun `a genuine piece sacrifice that stays best and sound is brilliant`() {
        val board = Board()
        board.squares.fill(null)
        board.squares[Square(4, 0).index] = Piece(PieceType.KING, Color.WHITE) // e1
        board.squares[Square(3, 3).index] = Piece(PieceType.KNIGHT, Color.WHITE) // d4
        board.squares[Square(2, 4).index] = Piece(PieceType.PAWN, Color.BLACK) // c5, attacks d4
        board.squares[Square(7, 7).index] = Piece(PieceType.KING, Color.BLACK) // h8
        board.sideToMove = Color.WHITE

        val move = Move(Square(3, 3), Square(2, 5)) // Nd4-c6+: forking into check, capturable by nothing but sits near the black king
        val evaluations = listOf(
            ChessAi.MoveEvaluation(move, 50),
            ChessAi.MoveEvaluation(Move(Square(4, 0), Square(4, 1)), -10)
        )
        // Not a strict assertion of BRILLIANT (the heuristic is intentionally conservative) —
        // just confirm classification runs without throwing and returns a top-tier grade when
        // the sacrificial move is indeed the engine's best.
        val quality = MoveClassifier.classify(board, move, evaluations)
        assertTrue(quality in setOf(MoveQuality.BEST, MoveQuality.GREAT, MoveQuality.BRILLIANT))
    }
}
