package chess.ai

import chess.Board
import chess.Move
import chess.PieceType

/**
 * Classifies a played move against the engine's own evaluation of the position it was played
 * from — a chess.com "Game Review" style label (Brilliant/Great/Best/.../Blunder/Missed Win).
 *
 * Scores are centipawn-like (a pawn is ~100), from the mover's point of view, matching
 * [ChessAi.evaluateAllMoves]'s convention.
 */
object MoveClassifier {

    fun classify(boardBefore: Board, played: Move, evaluations: List<ChessAi.MoveEvaluation>): MoveQuality {
        if (evaluations.isEmpty()) return MoveQuality.BEST
        val best = evaluations.first()
        val playedEval = evaluations.find { it.move == played } ?: evaluations.last()
        val second = evaluations.getOrNull(1)
        val cpLoss = (best.score - playedEval.score).coerceAtLeast(0)

        if (playedEval.move == best.move) {
            if (isSacrifice(boardBefore, played) && best.score >= -50) return MoveQuality.BRILLIANT
            // An "only move" that matters: a big drop-off to the next-best option, and the mover
            // isn't already comfortably winning (padding an already-won position isn't "great").
            if (second != null && best.score - second.score >= 150 && best.score < 300) return MoveQuality.GREAT
        }

        // The mover had a clearly winning move available and gave most of it back.
        if (best.score >= 300 && cpLoss >= 200) return MoveQuality.MISSED_WIN

        return when {
            cpLoss < 10 -> MoveQuality.BEST
            cpLoss < 50 -> MoveQuality.EXCELLENT
            cpLoss < 100 -> MoveQuality.GOOD
            cpLoss < 200 -> MoveQuality.INACCURACY
            cpLoss < 450 -> MoveQuality.MISTAKE
            else -> MoveQuality.BLUNDER
        }
    }

    /**
     * True if [played] moves a knight-or-heavier piece onto a square the opponent can capture it
     * on for less than it's worth — the surface signature of a sacrifice. Combined with the move
     * still being the engine's top choice and not losing, this is what earns a "Brilliant" label.
     */
    private fun isSacrifice(boardBefore: Board, played: Move): Boolean {
        val piece = boardBefore.pieceAt(played.from) ?: return false
        val movedValue = PIECE_VALUE.getValue(piece.type)
        if (movedValue < PIECE_VALUE.getValue(PieceType.KNIGHT)) return false // pawn/king moves don't count

        val captured = boardBefore.pieceAt(played.to)
        if (captured != null && PIECE_VALUE.getValue(captured.type) >= movedValue) return false // even or good trade

        val after = boardBefore.copy()
        after.applyMove(played)
        return after.isSquareAttacked(played.to, piece.color.opposite())
    }

    private val PIECE_VALUE = mapOf(
        PieceType.PAWN to 100,
        PieceType.KNIGHT to 320,
        PieceType.BISHOP to 330,
        PieceType.ROOK to 500,
        PieceType.QUEEN to 900,
        PieceType.KING to 0
    )
}
