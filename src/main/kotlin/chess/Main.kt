package chess

import java.io.PrintStream

fun main() {
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8))
    System.setErr(PrintStream(System.err, true, Charsets.UTF_8))

    val game = ChessGame()
    println("=== Kotlin Chess ===")
    println("이동 입력: e2e4  (프로모션은 e7e8q 처럼 마지막에 q/r/b/n 추가)")
    println("명령어: moves <칸>  (예: moves e2), resign (기권), quit (종료)")
    println()

    while (true) {
        println(game.board.render())

        when (game.status()) {
            GameStatus.CHECKMATE -> {
                val winner = if (game.board.sideToMove == Color.WHITE) "흑" else "백"
                println("체크메이트! $winner 승리!")
                return
            }
            GameStatus.STALEMATE -> {
                println("스테일메이트 - 무승부")
                return
            }
            GameStatus.DRAW_FIFTY_MOVE -> {
                println("50수 규칙 - 무승부")
                return
            }
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> {
                println("기물 부족 - 무승부")
                return
            }
            GameStatus.ONGOING -> {}
        }

        if (game.board.isInCheck(game.board.sideToMove)) {
            println("체크!")
        }

        val turnName = if (game.board.sideToMove == Color.WHITE) "백" else "흑"
        print("$turnName 차례 > ")
        val input = readLine()?.trim() ?: break
        if (input.isEmpty()) continue

        when {
            input == "resign" -> {
                println("${turnName}이(가) 기권했습니다.")
                return
            }
            input == "quit" || input == "exit" -> return
            input.startsWith("moves ") -> {
                val sq = Square.fromAlgebraic(input.removePrefix("moves ").trim())
                if (sq == null) {
                    println("잘못된 좌표입니다.")
                } else {
                    val moves = game.legalMovesFrom(sq)
                    println(
                        if (moves.isEmpty()) "가능한 수가 없습니다."
                        else "가능한 수: " + moves.joinToString(", ") { m -> m.to.toString() + (m.promotion?.symbol?.toString() ?: "") }
                    )
                }
            }
            else -> handleMove(game, input, turnName)
        }
        println()
    }
}

private fun handleMove(game: ChessGame, input: String, turnName: String) {
    val parsed = parseMoveInput(input)
    if (parsed == null) {
        println("입력 형식이 올바르지 않습니다. 예: e2e4")
        return
    }
    val (from, to, promo) = parsed
    val piece = game.board.pieceAt(from)
    if (piece == null || piece.color != game.board.sideToMove) {
        println("해당 칸에 옮길 수 있는 ${turnName}의 기물이 없습니다.")
        return
    }
    val move = game.tryMove(from, to, promo)
    if (move == null) {
        println("불법적인 수입니다.")
    }
}

private fun parseMoveInput(input: String): Triple<Square, Square, PieceType?>? {
    val cleaned = input.replace(" ", "")
    if (cleaned.length < 4) return null
    val from = Square.fromAlgebraic(cleaned.substring(0, 2)) ?: return null
    val to = Square.fromAlgebraic(cleaned.substring(2, 4)) ?: return null
    val promo = if (cleaned.length >= 5) {
        when (cleaned[4].lowercaseChar()) {
            'q' -> PieceType.QUEEN
            'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP
            'n' -> PieceType.KNIGHT
            else -> null
        }
    } else null
    return Triple(from, to, promo)
}
