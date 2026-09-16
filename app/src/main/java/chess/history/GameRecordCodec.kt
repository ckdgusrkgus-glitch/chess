package chess.history

import chess.Color

/**
 * A tiny hand-rolled serialization for [GameRecord] — one game per line, fields separated by "|",
 * moves separated by ",". Deliberately not JSON: the app has no JSON library dependency, and every
 * field here is either a number or text this app itself generates (Korean UI labels, algebraic
 * move strings), so it never contains the separator characters.
 */
object GameRecordCodec {
    private const val FIELD_SEP = "|"
    private const val MOVE_SEP = ","
    private const val NO_COLOR = "NONE"

    fun encode(record: GameRecord): String = listOf(
        record.timestampMillis.toString(),
        record.humanColor?.name ?: NO_COLOR,
        record.opponentLabel,
        record.result,
        record.moves.joinToString(MOVE_SEP)
    ).joinToString(FIELD_SEP)

    fun decode(line: String): GameRecord? {
        val parts = line.split(FIELD_SEP, limit = 5)
        if (parts.size != 5) return null
        val timestamp = parts[0].toLongOrNull() ?: return null
        val humanColor = when {
            parts[1] == NO_COLOR -> null
            else -> runCatching { Color.valueOf(parts[1]) }.getOrNull() ?: return null
        }
        val moves = if (parts[4].isEmpty()) emptyList() else parts[4].split(MOVE_SEP)
        return GameRecord(timestamp, humanColor, parts[2], parts[3], moves)
    }

    fun encodeAll(records: List<GameRecord>): String = records.joinToString("\n") { encode(it) }

    fun decodeAll(text: String): List<GameRecord> =
        if (text.isEmpty()) emptyList() else text.split("\n").mapNotNull { decode(it) }
}
