package chess.ai

/**
 * Move qualities that get a dedicated "why" screen in [chess.ui.MoveExplanationActivity] instead
 * of just a grade badge — the ones where knowing the played move and the engine's best move alone
 * isn't the interesting part; what happens next is.
 */
val EXPLAINABLE_MOVE_QUALITIES: Set<MoveQuality> = setOf(MoveQuality.BLUNDER, MoveQuality.BRILLIANT, MoveQuality.MISSED_WIN)
