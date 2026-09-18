package chess.augment

/**
 * The end augments implemented so far, all picked from the 나무위키 "엔드 증강" list's PASSIVE,
 * alternative-win-condition entries — no new piece kind, no timed status tracking, just a board-state
 * predicate checked in [chess.AugmentedChessGame.status]. Everything else in that list (강제 교환's
 * one-shot sacrifice, 최종병기's new Amazon piece, 언더프로모션's bonus move, etc.) needs either an
 * interactive "pick a target" UI this module doesn't have yet, or a new piece kind (Tier 3+ per
 * docs/augmented-chess-design.md) — out of scope for this batch.
 */
object EndAugments {

    val RACING_KING = EndAugment(
        id = "racing_king",
        displayName = "레이싱 킹",
        description = "아군 킹이 상대 진영 끝 랭크에 도달하면 즉시 승리합니다.",
        cost = 3f,
        grantsRacingKing = true
    )

    val DOUBLE_CHECK = EndAugment(
        id = "double_check",
        displayName = "더블 체크",
        description = "서로 다른 아군 기물 2개 이상이 동시에 상대 킹을 공격하면 즉시 승리합니다.",
        cost = 3.5f,
        grantsDoubleCheck = true
    )

    val HIGHLANDER = EndAugment(
        id = "highlander",
        displayName = "하이랜더",
        description = "폰을 포함해 아군 진영에 같은 종류의 기물이 2개 이상 남아있지 않다면 즉시 승리합니다.",
        cost = 5f,
        grantsHighlander = true
    )

    val ALL: List<EndAugment> = listOf(RACING_KING, DOUBLE_CHECK, HIGHLANDER)

    fun byId(id: String?): EndAugment? = ALL.find { it.id == id }
}
