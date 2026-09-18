package chess.augment

/**
 * A "미들 증강" (middle augment): drafted mid-game rather than at setup, applied to whichever side
 * picks it. Unlike [OpeningAugment] (which transforms the starting [chess.Board]), a middle augment
 * only ever grants a static [chess.AugmentRules] flag for the rest of the game — see
 * [chess.AugmentedChessGame.applyMiddleAugment].
 */
data class MiddleAugment(
    val id: String,
    val displayName: String,
    val description: String,
    val cost: Float,
    val grantsPawnRetreat: Boolean = false,
    val grantsKingKnightMoves: Boolean = false
)
