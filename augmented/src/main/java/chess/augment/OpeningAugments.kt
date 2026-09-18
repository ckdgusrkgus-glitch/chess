package chess.augment

import chess.Color
import chess.Piece
import chess.PieceType
import chess.Square

/**
 * The Tier 1 opening augments from docs/augmented-chess-design.md's rollout plan: pure static
 * setup/parameter changes, needing no new piece kind and no per-turn status-effect tracking.
 *
 * Left out of this first batch, and why:
 *  - 부정출발 (all pieces start 2 ranks advanced): moves the king/rook off the ranks this engine's
 *    castling logic hardcodes (rank 0/7), which would silently break castling for that side.
 *  - 존버 / 재활용: need a "designate a specific pawn" or "track captured pieces per side"
 *    mechanism this milestone doesn't build yet.
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

    val ALL: List<OpeningAugment> = listOf(QUEENS_CAVALRY, EARLY_PROMOTION, HASTY_PROMOTION)

    fun byId(id: String?): OpeningAugment? = ALL.find { it.id == id }
}
