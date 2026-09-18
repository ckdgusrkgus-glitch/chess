package chess.network

/** How often one candidate move was played, and how those games turned out, in the source database. */
data class MoveStat(val uci: String, val san: String, val white: Int, val draws: Int, val black: Int) {
    val total: Int get() = white + draws + black
}

/** Move popularity for one position, aggregated from real games — not limited to known opening theory. */
data class PositionStats(val totalGames: Int, val moves: List<MoveStat>)
