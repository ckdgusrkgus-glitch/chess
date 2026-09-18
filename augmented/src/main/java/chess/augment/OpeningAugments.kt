package chess.augment

import chess.Color
import chess.Piece
import chess.PieceType
import chess.Square

/**
 * The Tier 1 opening augments from docs/augmented-chess-design.md's rollout plan: pure static
 * setup/parameter changes, needing no new piece kind and no per-turn status-effect tracking.
 *
 * Still left out of this batch, and why: 존버 / 재활용 need a "designate a specific pawn" or "track
 * captured pieces per side" mechanism this milestone doesn't build yet. (부정출발 was too, until
 * chess.Board grew configurable castling home squares — see [FALSE_START].)
 */
object OpeningAugments {

    val QUEENS_CAVALRY = OpeningAugment(
        id = "queens_cavalry",
        displayName = "퀸의 기병대",
        description = "아군 퀸 파일(d)의 폰을 나이트로 바꿉니다.",
        cost = 2f,
        setupTransform = { board, color ->
            val pawnRank = if (color == Color.WHITE) 1 else 6
            board.squares[Square(3, pawnRank).index] = Piece(PieceType.KNIGHT, color)
        }
    )

    val EARLY_PROMOTION = OpeningAugment(
        id = "early_promotion",
        displayName = "조기 진급",
        description = "아군 폰의 프로모션 랭크가 2칸 내려옵니다.",
        cost = 3.5f,
        promotionRankOverride = { color -> if (color == Color.WHITE) 5 else 2 }
    )

    val HASTY_PROMOTION = OpeningAugment(
        id = "hasty_promotion",
        displayName = "성급한 승진",
        description = "아군 폰의 프로모션 랭크가 3칸 내려옵니다. 단, 메이저 피스로는 변할 수 없습니다.",
        cost = 3.5f,
        promotionRankOverride = { color -> if (color == Color.WHITE) 4 else 3 },
        promotionChoicesOverride = { listOf(PieceType.BISHOP, PieceType.KNIGHT) }
    )

    val FALSE_START = OpeningAugment(
        id = "false_start",
        displayName = "부정출발",
        description = "아군 모든 기물이 2칸 전진된 상태로 시작합니다.",
        cost = 3f,
        setupTransform = { board, color ->
            val fromPawnRank = if (color == Color.WHITE) 1 else 6
            val fromBackRank = if (color == Color.WHITE) 0 else 7
            val toPawnRank = if (color == Color.WHITE) 3 else 4
            val toBackRank = if (color == Color.WHITE) 2 else 5

            for (file in 0..7) {
                val pawn = board.squares[Square(file, fromPawnRank).index]
                val back = board.squares[Square(file, fromBackRank).index]
                board.squares[Square(file, fromPawnRank).index] = null
                board.squares[Square(file, fromBackRank).index] = null
                board.squares[Square(file, toPawnRank).index] = pawn
                board.squares[Square(file, toBackRank).index] = back
            }

            // Castling rights are tracked against these squares (see chess.Board and
            // MoveGenerator.kingMoves) — without updating them, castling would silently stop
            // working the moment the king/rooks land somewhere other than rank 0/7.
            if (color == Color.WHITE) {
                board.whiteKingHome = Square(4, toBackRank)
                board.whiteQueenRookHome = Square(0, toBackRank)
                board.whiteKingRookHome = Square(7, toBackRank)
            } else {
                board.blackKingHome = Square(4, toBackRank)
                board.blackQueenRookHome = Square(0, toBackRank)
                board.blackKingRookHome = Square(7, toBackRank)
            }
        }
    )

    val ALL: List<OpeningAugment> = listOf(QUEENS_CAVALRY, EARLY_PROMOTION, HASTY_PROMOTION, FALSE_START)

    fun byId(id: String?): OpeningAugment? = ALL.find { it.id == id }
}
