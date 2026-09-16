package chess.history

import chess.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameRecordCodecTest {

    @Test
    fun `round trips a record with a human color`() {
        val record = GameRecord(
            timestampMillis = 1_234_567_890L,
            humanColor = Color.BLACK,
            opponentLabel = "AI 초급 (800)",
            result = "체크메이트! 백 승리",
            moves = listOf("e2e4", "e7e5", "g1f3")
        )
        assertEquals(record, GameRecordCodec.decode(GameRecordCodec.encode(record)))
    }

    @Test
    fun `round trips a two-player record with no human color and no moves`() {
        val record = GameRecord(
            timestampMillis = 42L,
            humanColor = null,
            opponentLabel = "2인 대전",
            result = "무승부",
            moves = emptyList()
        )
        assertEquals(record, GameRecordCodec.decode(GameRecordCodec.encode(record)))
    }

    @Test
    fun `encodeAll and decodeAll round trip multiple records in order`() {
        val records = listOf(
            GameRecord(1L, Color.WHITE, "AI 최상급 (2000)", "패배", listOf("e2e4")),
            GameRecord(2L, null, "2인 대전", "무승부", emptyList())
        )
        assertEquals(records, GameRecordCodec.decodeAll(GameRecordCodec.encodeAll(records)))
    }

    @Test
    fun `decode rejects garbage input`() {
        assertNull(GameRecordCodec.decode("not a valid record"))
        assertNull(GameRecordCodec.decode(""))
    }

    @Test
    fun `decodeAll on empty text yields an empty list`() {
        assertEquals(emptyList<GameRecord>(), GameRecordCodec.decodeAll(""))
    }
}
