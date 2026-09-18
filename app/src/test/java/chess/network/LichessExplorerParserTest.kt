package chess.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LichessExplorerParserTest {

    private val sample = """
        {
          "white": 120,
          "draws": 30,
          "black": 50,
          "moves": [
            {"uci": "e2e4", "san": "e4", "white": 80, "draws": 20, "black": 30, "averageRating": 1900},
            {"uci": "d2d4", "san": "d4", "white": 40, "draws": 10, "black": 20, "averageRating": 1950}
          ],
          "topGames": [],
          "recentGames": [],
          "opening": {"eco": "B00", "name": "King's Pawn"}
        }
    """.trimIndent()

    @Test
    fun `parses move list sorted by total games descending`() {
        val stats = LichessExplorerParser.parse(sample)
        assertEquals(2, stats.moves.size)
        assertEquals("e2e4", stats.moves[0].uci)
        assertEquals("e4", stats.moves[0].san)
        assertEquals(130, stats.moves[0].total)
        assertEquals("d2d4", stats.moves[1].uci)
        assertEquals(70, stats.moves[1].total)
    }

    @Test
    fun `total games comes from the root white draws black counts`() {
        val stats = LichessExplorerParser.parse(sample)
        assertEquals(200, stats.totalGames)
    }

    @Test
    fun `an empty moves array yields an empty (not crashing) result`() {
        val stats = LichessExplorerParser.parse("""{"white":0,"draws":0,"black":0,"moves":[]}""")
        assertTrue(stats.moves.isEmpty())
        assertEquals(0, stats.totalGames)
    }

    @Test
    fun `a move entry missing san falls back to its uci`() {
        val stats = LichessExplorerParser.parse(
            """{"white":1,"draws":0,"black":0,"moves":[{"uci":"g1f3","white":1,"draws":0,"black":0}]}"""
        )
        assertEquals("g1f3", stats.moves.single().san)
    }

    @Test
    fun `a move entry missing uci is skipped rather than crashing`() {
        val stats = LichessExplorerParser.parse(
            """{"white":1,"draws":0,"black":0,"moves":[{"san":"e4","white":1,"draws":0,"black":0}]}"""
        )
        assertTrue(stats.moves.isEmpty())
    }
}
