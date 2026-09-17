package chess.ai

/**
 * A hand-picked set of common opening lines (coordinate notation, e.g. "e2e4"), used only to flag
 * a played move as textbook theory ("Book") in game review. This is a convenience approximation,
 * not a real opening database — it recognizes well-known lines out to roughly move 8, nothing
 * obscure or deeply theoretical beyond that.
 */
object OpeningBook {

    // internal (not private) so the test suite can replay every line through the real move
    // generator and catch a typo'd or illegal move sequence instead of trusting it silently.
    internal val LINES: List<List<String>> = listOf(
        // --- King's pawn: Italian / Two Knights ---
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3",
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d4 e5d4 c3d4",
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 b2b4",
        "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6",
        "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 d7d5 e4d5 f6d5",

        // --- King's pawn: Ruy Lopez ---
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7",
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6",
        "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6",
        "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6 e1g1 f6e4 d2d4 e4d6 b5c6 d7c6 d4e5 d6f5",
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5c6 d7c6",
        "e2e4 e7e5 g1f3 b8c6 f1b5 f7f5",

        // --- King's pawn: Scotch / Petrov / Philidor / Four Knights / Vienna / King's Gambit / misc ---
        "e2e4 e7e5 g1f3 b8c6 d2d4",
        "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 g8f6 b1c3 f8b4",
        "e2e4 e7e5 g1f3 g8f6",
        "e2e4 e7e5 g1f3 g8f6 f3e5 d7d6 e5f3 f6e4",
        "e2e4 e7e5 g1f3 d7d6",
        "e2e4 e7e5 g1f3 d7d6 d2d4 g8f6 b1c3 b8d7",
        "e2e4 e7e5 g1f3 b8c6 b1c3 g8f6",
        "e2e4 e7e5 g1f3 b8c6 b1c3 g8f6 f1b5 f8b4",
        "e2e4 e7e5 g1f3 b8c6 b1c3 f8c5",
        "e2e4 e7e5 b1c3",
        "e2e4 e7e5 b1c3 g8f6 f2f4",
        "e2e4 e7e5 f2f4",
        "e2e4 e7e5 f2f4 e5f4 g1f3",
        "e2e4 e7e5 f2f4 f8c5",
        "e2e4 e7e5 f1c4",
        "e2e4 e7e5 d2d4 e5d4 d1d4",
        "e2e4 e7e5 d2d4 e5d4 c2c3",

        // --- Semi-open: Sicilian ---
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6 c1g5 e7e6",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 g7g6 c1e3 f8g7",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 b8c6",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 e7e6",
        "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 e7e5",
        "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 e7e6",
        "e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4 a7a6",
        "e2e4 c7c5 b1c3 b8c6 g2g3",
        "e2e4 c7c5 c2c3",
        "e2e4 c7c5 c2c3 g8f6 e4e5 f6d5 d2d4",
        "e2e4 c7c5 g1f3 b8c6 f1b5",
        "e2e4 c7c5 d2d4 c5d4 c2c3",

        // --- Semi-open: French ---
        "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6",
        "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7",
        "e2e4 e7e6 d2d4 d7d5 b1c3 f8b4",
        "e2e4 e7e6 d2d4 d7d5 b1d2 g8f6",
        "e2e4 e7e6 d2d4 d7d5 e4e5 c7c5",
        "e2e4 e7e6 d2d4 d7d5 e4d5 e6d5",

        // --- Semi-open: Caro-Kann ---
        "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5",
        "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5 e4g3 f5g6",
        "e2e4 c7c6 d2d4 d7d5 b1d2 d5e4 d2e4",
        "e2e4 c7c6 d2d4 d7d5 e4e5 c8f5",
        "e2e4 c7c6 d2d4 d7d5 e4d5 c6d5",
        "e2e4 c7c6 d2d4 d7d5 e4d5 c6d5 c2c4",
        "e2e4 c7c6 b1c3 d7d5 g1f3",

        // --- Semi-open: Pirc / Modern / Scandinavian / Alekhine ---
        "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6",
        "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f1e2 f8g7",
        "e2e4 g7g6",
        "e2e4 g7g6 d2d4 f8g7 b1c3 d7d6",
        "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5",
        "e2e4 d7d5 e4d5 g8f6",
        "e2e4 g8f6",
        "e2e4 g8f6 e4e5 f6d5 d2d4 d7d6",

        // --- Queen's pawn: Queen's Gambit / Slav ---
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6",
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7",
        "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6",
        "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6 e2e3 e7e6",
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6",
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4",
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 e7e6",
        "d2d4 d7d5 c2c4 b8c6",
        "d2d4 d7d5 c2c4 e7e5",

        // --- Queen's pawn: Indian systems ---
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6",
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 g1f3 e8g8",
        "d2d4 g8f6 c2c4 g7g6 b1c3 d7d5",
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4",
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8",
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 g1f3 c7c5",
        "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6",
        "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6 g2g3 c8b7",
        "d2d4 g8f6 c2c4 e7e6 g1f3 f8b4",
        "d2d4 g8f6 c2c4 c7c5 d4d5 e7e6",
        "d2d4 g8f6 c2c4 c7c5 d4d5 b7b5",
        "d2d4 g8f6 c2c4 d7d6",
        "d2d4 f7f5",

        // --- Flank openings: English / Reti / London / Colle / Catalan / Bird / misc ---
        "c2c4 e7e5 b1c3 g8f6",
        "c2c4 e7e5 b1c3 g8f6 g1f3 b8c6",
        "c2c4 c7c5",
        "c2c4 g8f6 b1c3 e7e6",
        "g1f3 d7d5 c2c4",
        "g1f3 d7d5 g2g3",
        "g1f3 g8f6 g2g3",
        "d2d4 d7d5 g1f3 g8f6 c1f4",
        "d2d4 d7d5 g1f3 g8f6 c1f4 e7e6 e2e3 f8d6",
        "d2d4 d7d5 g1f3 g8f6 e2e3",
        "d2d4 g8f6 c2c4 e7e6 g2g3",
        "f2f4",
        "f2f4 d7d5 g1f3",
        "b2b3"
    ).map { it.trim().split(" ") }

    private const val MAX_PLY = 10

    /** True if [movesSoFar] (the game's moves up to and including the one just played) matches a known opening line. */
    fun isBookMove(movesSoFar: List<String>): Boolean {
        if (movesSoFar.isEmpty() || movesSoFar.size > MAX_PLY) return false
        return LINES.any { line -> line.size >= movesSoFar.size && line.subList(0, movesSoFar.size) == movesSoFar }
    }
}
