package chess.ai

import chess.Move

/**
 * Supporting facts for a played move that graded as Blunder/Brilliant/Missed Win — enough to show
 * a "why" screen: what was actually played, what the engine's own top choice was, and (for a
 * blunder specifically) what the opponent's best reply would exploit.
 */
data class MoveExplanation(
    val quality: MoveQuality,
    val playedMove: Move,
    val playedScore: Int,
    val bestMove: Move,
    val bestScore: Int,
    val punishingReply: Move? = null,
    val punishingReplyScore: Int? = null
) {
    companion object {
        val EXPLAINABLE: Set<MoveQuality> = setOf(MoveQuality.BLUNDER, MoveQuality.BRILLIANT, MoveQuality.MISSED_WIN)
    }
}
