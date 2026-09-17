package chess.ai

import chess.Move

/**
 * Supporting facts for a played move that graded as Blunder/Brilliant/Missed Win — enough to show
 * a "why" screen: what was actually played, what the engine's own top choice was, (for a blunder
 * specifically) what the opponent's best reply would exploit, and — in [followUpLine] — how the
 * game is expected to continue from there. A concrete continuation is what makes "Brilliant" mean
 * something different from one sacrifice to the next, instead of the same boilerplate sentence
 * every time regardless of what the position actually looks like.
 */
data class MoveExplanation(
    val quality: MoveQuality,
    val playedMove: Move,
    val playedScore: Int,
    val bestMove: Move,
    val bestScore: Int,
    val punishingReply: Move? = null,
    val punishingReplyScore: Int? = null,
    /**
     * Best play for both sides, a few plies deep, starting right after the move that matters for
     * this verdict: after [playedMove] for Brilliant, after [punishingReply] for Blunder, after
     * [bestMove] for Missed Win.
     */
    val followUpLine: List<Move> = emptyList()
) {
    companion object {
        val EXPLAINABLE: Set<MoveQuality> = setOf(MoveQuality.BLUNDER, MoveQuality.BRILLIANT, MoveQuality.MISSED_WIN)
    }
}
