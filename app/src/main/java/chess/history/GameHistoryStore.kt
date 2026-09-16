package chess.history

import android.content.Context

/** Thin SharedPreferences-backed store for the most recent [MAX_GAMES] completed games. */
class GameHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Newest first. */
    fun loadAll(): List<GameRecord> = GameRecordCodec.decodeAll(prefs.getString(KEY_GAMES, null).orEmpty())

    fun addGame(record: GameRecord) {
        val updated = (listOf(record) + loadAll()).take(MAX_GAMES)
        prefs.edit().putString(KEY_GAMES, GameRecordCodec.encodeAll(updated)).apply()
    }

    companion object {
        private const val PREFS_NAME = "chess_game_history"
        private const val KEY_GAMES = "games"
        const val MAX_GAMES = 20
    }
}
