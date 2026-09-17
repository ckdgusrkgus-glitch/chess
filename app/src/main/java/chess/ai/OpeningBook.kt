package chess.ai

/**
 * A small hand-picked set of common opening lines (coordinate notation, e.g. "e2e4"), used only
 * to flag a played move as textbook theory ("Book") in game review. This is a convenience
 * approximation, not a real opening database — it recognizes some of the most common lines for
 * roughly the first 10 plies of a game, nothing deeper or more obscure.
 */
object OpeningBook {

    private val LINES: List<List<String>> = listOf(
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3",
        "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6",
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7",
        "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6",
        "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6",
        "e2e4 e7e6 d2d4 d7d5 b1d2 g8f6",
        "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5",
        "e2e4 c7c6 d2d4 d7d5 b1d2 d5e4 d2e4",
        "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5",
        "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6",
        "e2e4 e7e5 f2f4",
        "e2e4 e7e5 b1c3",
        "e2e4 e7e5 g1f3 b8c6 d2d4",
        "e2e4 e7e5 g1f3 b8c6 b1c3 g8f6",
        "e2e4 e7e5 g1f3 g8f6",
        "e2e4 e7e5 g1f3 d7d6",
        "e2e4 g8f6",
        "e2e4 g7g6",
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6",
        "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6",
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6",
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6",
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4",
        "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6",
        "d2d4 d7d5 g1f3 g8f6 c1f4",
        "c2c4 e7e5 b1c3 g8f6",
        "c2c4 c7c5",
        "g1f3 d7d5 c2c4",
        "f2f4"
    ).map { it.trim().split(" ") }

    private const val MAX_PLY = 10

    /** True if [movesSoFar] (the game's moves up to and including the one just played) matches a known opening line. */
    fun isBookMove(movesSoFar: List<String>): Boolean {
        if (movesSoFar.isEmpty() || movesSoFar.size > MAX_PLY) return false
        return LINES.any { line -> line.size >= movesSoFar.size && line.subList(0, movesSoFar.size) == movesSoFar }
    }
}
