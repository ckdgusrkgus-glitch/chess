package chess.augment

/**
 * The middle augments implemented so far. Both are Tier 1 (static [chess.AugmentRules] flags, no
 * new piece kind, no per-turn status tracking) — the same class of augment as most of
 * [OpeningAugments], just granted mid-game instead of at setup.
 *
 * Only these two are wired up: everything else in the 미들 증강 list either needs a new piece kind
 * (Tier 3+) or a timed status effect (Tier 2), neither of which exist in this module yet per
 * docs/augmented-chess-design.md's rollout plan.
 */
object MiddleAugments {

    val RETREAT = MiddleAugment(
        id = "retreat",
        displayName = "퇴각",
        description = "아군 폰이 한 칸 후퇴하거나 대각선 뒤로 상대 기물을 잡을 수 있게 됩니다.",
        cost = 3f,
        grantsPawnRetreat = true
    )

    val CAVALRY = MiddleAugment(
        id = "cavalry",
        displayName = "승마",
        description = "아군 킹이 나이트처럼도 움직일 수 있게 됩니다.",
        cost = 4f,
        grantsKingKnightMoves = true
    )

    val ALL: List<MiddleAugment> = listOf(RETREAT, CAVALRY)

    fun byId(id: String?): MiddleAugment? = ALL.find { it.id == id }
}
