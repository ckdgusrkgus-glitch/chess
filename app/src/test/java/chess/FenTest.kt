package chess

import org.junit.Assert.assertEquals
import org.junit.Test

class FenTest {

    @Test
    fun `starting position matches the well-known FEN`() {
        val board = Board().apply { setup() }
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", board.toFen())
    }

    @Test
    fun `after 1 e4 the en passant square and side to move update`() {
        val board = Board().apply { setup() }
        board.applyMove(parseAlgebraicMove(board, "e2e4")!!)
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", board.toFen())
    }

    @Test
    fun `after 1 e4 e5 the fullmove counter advances and en passant clears`() {
        val board = Board().apply { setup() }
        board.applyMove(parseAlgebraicMove(board, "e2e4")!!)
        board.applyMove(parseAlgebraicMove(board, "e7e5")!!)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", board.toFen())
    }

    @Test
    fun `castling rights drop once a king or rook moves`() {
        val board = Board().apply { setup() }
        board.applyMove(parseAlgebraicMove(board, "e2e4")!!)
        board.applyMove(parseAlgebraicMove(board, "e7e5")!!)
        board.applyMove(parseAlgebraicMove(board, "e1e2")!!)
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPPKPPP/RNBQ1BNR b kq - 1 2", board.toFen())
    }
}
