package chess.network

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up how often each move from a position was played across Lichess's rated-games database —
 * this covers whatever position their games actually reached, not just known opening theory, which
 * is why it works from any position reviewed in this app, not only the opening.
 */
object LichessExplorer {
    private const val BASE_URL = "https://explorer.lichess.ovh/lichess"
    private const val TIMEOUT_MS = 8000

    /**
     * Runs a blocking network call — callers must invoke this off the main thread. Returns null on
     * any failure (offline, timeout, unexpected response) rather than throwing, so the UI can show
     * a plain "couldn't load" state instead of crashing.
     */
    fun fetch(fen: String, maxMoves: Int = 15): PositionStats? = runCatching {
        val encodedFen = URLEncoder.encode(fen, "UTF-8")
        val url = URL("$BASE_URL?variant=standard&fen=$encodedFen&moves=$maxMoves&topGames=0&recentGames=0")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val body = connection.inputStream.bufferedReader().readText()
            LichessExplorerParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
