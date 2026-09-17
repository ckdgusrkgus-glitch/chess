package chess.ai

import chess.Board
import chess.parseAlgebraicMove
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every line in OpeningBook.LINES must be a sequence of actually-legal moves from the starting
 * position — a typo'd square or an illegal move order would silently make that line (and every
 * shorter prefix that's also wrong) never match a real game, defeating the point of adding it.
 */
class OpeningBookLinesTest {

    @Test
    fun `every opening book line is a sequence of legal moves from the start`() {
        val failures = mutableListOf<String>()
        for (line in OpeningBook.LINES) {
            val board = Board().apply { setup() }
            for ((ply, notation) in line.withIndex()) {
                val move = parseAlgebraicMove(board, notation)
                if (move == null) {
                    failures.add("Illegal move '$notation' at ply ${ply + 1} in line: ${line.joinToString(" ")}")
                    break
                }
                board.applyMove(move)
            }
        }
        assertTrue("Found illegal moves in OpeningBook.LINES:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `no duplicate lines`() {
        val asStrings = OpeningBook.LINES.map { it.joinToString(" ") }
        val duplicates = asStrings.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate lines found: $duplicates", duplicates.isEmpty())
    }
}
