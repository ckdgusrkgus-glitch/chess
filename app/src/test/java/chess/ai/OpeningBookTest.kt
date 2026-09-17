package chess.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningBookTest {

    @Test
    fun `recognizes the italian game move by move`() {
        val line = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4")
        for (i in 1..line.size) {
            assertTrue("prefix of size $i should be book", OpeningBook.isBookMove(line.subList(0, i)))
        }
    }

    @Test
    fun `recognizes the ruy lopez including castling notation`() {
        val line = "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1".split(" ")
        assertTrue(OpeningBook.isBookMove(line))
    }

    @Test
    fun `an oddball opening is not book`() {
        assertFalse(OpeningBook.isBookMove(listOf("a2a4")))
        assertFalse(OpeningBook.isBookMove(listOf("e2e4", "h7h5")))
    }

    @Test
    fun `stops recognizing once the game deviates from every known line`() {
        val deviated = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "a7a5") // not a stored continuation
        assertFalse(OpeningBook.isBookMove(deviated))
    }

    @Test
    fun `never flags a move sequence beyond the book's depth`() {
        val tooLong = List(11) { "e2e4" }
        assertFalse(OpeningBook.isBookMove(tooLong))
    }
}
