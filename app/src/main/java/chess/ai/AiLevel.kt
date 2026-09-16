package chess.ai

/**
 * A selectable AI opponent strength, loosely calibrated to an approximate chess rating.
 *
 * [depth] is the full-width search depth in plies. [blunderChance] is the probability of
 * ignoring the search entirely and playing a uniformly random legal move (simulating a weak
 * player's outright mistakes). [randomPoolSize] is how many of the top-scoring moves are
 * eligible to be picked from at random, so even a "strong" move choice isn't perfectly robotic
 * at the lower levels.
 */
enum class AiLevel(val label: String, val rating: Int, val depth: Int, val blunderChance: Double, val randomPoolSize: Int) {
    BEGINNER("초급", 800, 1, 0.30, 5),
    INTERMEDIATE("중급", 1200, 2, 0.12, 3),
    ADVANCED("상급", 1600, 2, 0.0, 2),
    MASTER("최상급", 2000, 3, 0.0, 1)
}
