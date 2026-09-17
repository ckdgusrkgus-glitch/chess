package chess.ai

/** How a played move compares to the engine's own evaluation of the position it was played from. */
enum class MoveQuality {
    BRILLIANT,
    GREAT,
    BEST,
    EXCELLENT,
    GOOD,
    BOOK,
    INACCURACY,
    MISTAKE,
    BLUNDER,
    MISSED_WIN
}
