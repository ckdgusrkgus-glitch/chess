package chess.augment

import chess.Board
import chess.Color
import chess.PieceType

/**
 * A "오프닝 증강" (opening augment): each side drafts exactly one of these before the game starts,
 * and it's applied automatically to that side alone as the very first thing that happens.
 *
 * [setupTransform] mutates the just-set-up board (e.g. swapping one starting piece for another).
 * [promotionRankOverride]/[promotionChoicesOverride] adjust that side's pawn promotion rules for
 * the rest of the game — see [chess.AugmentRules], which [chess.AugmentedChessGame] builds from
 * whichever augment each side picked.
 */
data class OpeningAugment(
    val id: String,
    val displayName: String,
    val description: String,
    /** Cost in "stars", as the source material grades these — not used for anything yet beyond display. */
    val cost: Float,
    val setupTransform: (Board, Color) -> Unit = { _, _ -> },
    val promotionRankOverride: ((Color) -> Int)? = null,
    val promotionChoicesOverride: ((Color) -> List<PieceType>)? = null,
    /** Set only for 존버 (Turtling): the file (0=a..7=h) of the pawn to watch — see [chess.AugmentedChessGame]'s turtling bookkeeping. */
    val turtlingFile: Int? = null
)
