package chess.augment

/**
 * An "엔드 증강" (end augment): drafted even later than a [MiddleAugment], granted to whichever side
 * picks it. Every one implemented so far is a PASSIVE alternative win condition, so — unlike
 * [MiddleAugment]'s per-turn movement flags — these are only ever read from
 * [chess.AugmentedChessGame.status]() rather than [chess.MoveGenerator].
 */
data class EndAugment(
    val id: String,
    val displayName: String,
    val description: String,
    val cost: Float,
    val grantsRacingKing: Boolean = false,
    val grantsDoubleCheck: Boolean = false,
    val grantsHighlander: Boolean = false
)
