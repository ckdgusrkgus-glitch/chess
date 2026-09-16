package chess.history

import chess.Color

/**
 * A completed (or resigned) game, kept for the "복기" (review) screen.
 *
 * [humanColor] is null for a local two-player game, where there's no single human side.
 * [moves] are algebraic strings (see [chess.toAlgebraic]) in the order they were played.
 */
data class GameRecord(
    val timestampMillis: Long,
    val humanColor: Color?,
    val opponentLabel: String,
    val result: String,
    val moves: List<String>
)
