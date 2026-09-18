package chess.network

import org.json.JSONObject

/**
 * Parses a Lichess Opening Explorer response body (the "lichess" database — real players' rated
 * games, not just master games or known theory) into [PositionStats]. Kept separate from the
 * actual HTTP call in [LichessExplorer] so the parsing logic can be tested against a fixed JSON
 * string without needing a network connection.
 */
object LichessExplorerParser {
    fun parse(json: String): PositionStats {
        val root = JSONObject(json)
        val movesArray = root.optJSONArray("moves")
        val moves = mutableListOf<MoveStat>()
        if (movesArray != null) {
            for (i in 0 until movesArray.length()) {
                val entry = movesArray.getJSONObject(i)
                val uci = entry.optString("uci")
                if (uci.isEmpty()) continue
                moves += MoveStat(
                    uci = uci,
                    san = entry.optString("san").ifEmpty { uci },
                    white = entry.optInt("white", 0),
                    draws = entry.optInt("draws", 0),
                    black = entry.optInt("black", 0)
                )
            }
        }
        val totalGames = root.optInt("white", 0) + root.optInt("draws", 0) + root.optInt("black", 0)
        return PositionStats(totalGames, moves.sortedByDescending { it.total })
    }
}
