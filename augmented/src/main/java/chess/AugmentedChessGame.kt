package chess

import chess.augment.EndAugment
import chess.augment.MiddleAugment
import chess.augment.OpeningAugment

/**
 * Base ruleset for "증강체스" (Augmented Chess): standard chess movement, but the win/loss
 * conditions differ from a normal [chess.ChessGame]-style game —
 *  - Checkmate does not end the game by itself; a king may move into (or stay in) an attacked
 *    square. The game only ends once a king is actually captured.
 *  - A side with no legal moves at all loses immediately, whether or not it's in check — this
 *    replaces both "checkmate" and "stalemate" from standard chess with a single condition.
 *
 * Castling itself is unaffected: it still requires the king not be in check and not pass through
 * an attacked square, exactly as in [MoveGenerator]'s existing castling logic — the source
 * material only ever describes ordinary king moves as newly legal, not castling's own rule.
 *
 * [whiteAugment]/[blackAugment] are each side's drafted "오프닝 증강" (opening augment, see
 * [OpeningAugment]), applied once per [resetBoard] call and reflected for the rest of that game in
 * [rules]. [recycleRuleEnabled]/[mannersRuleEnabled]/[crownRuleEnabled]/[transcendRuleEnabled] are
 * "규칙 증강" (rule augments) — unlike opening/middle/end augments, these apply identically to both
 * sides regardless of what anyone drafted, so each is a plain flag rather than something a side
 * picks. (The source material says rule augments are now applied randomly before the game starts
 * rather than drafted at all — this module keeps the simpler manual-toggle approach 재활용 already
 * used, for the same reason: there's no drafting UI here that a "random pool" would plug into.)
 */
class AugmentedChessGame(
    private val whiteAugment: OpeningAugment? = null,
    private val blackAugment: OpeningAugment? = null,
    private val recycleRuleEnabled: Boolean = false,
    private val mannersRuleEnabled: Boolean = false,
    private val crownRuleEnabled: Boolean = false,
    private val transcendRuleEnabled: Boolean = false
) {
    val board = Board()

    val rules = AugmentRules(
        promotionRank = { color ->
            augmentFor(color)?.promotionRankOverride?.invoke(color) ?: AugmentRules.STANDARD.promotionRank(color)
        },
        promotionChoices = { color -> resolvePromotionChoices(color) },
        pawnCanRetreat = { color -> middlePawnRetreat[color] == true },
        kingHasKnightMoves = { color -> middleKingKnight[color] == true }
    )

    /** Total plies (half-moves) played since [resetBoard] — 존버's 14-ply timer, and the middle-augment
     *  draft trigger, both count against this. */
    var plyCount = 0
        private set

    /** Which sides have been granted each 미들 증강 (middle augment) effect so far this game — see
     *  [applyMiddleAugment]. Unlike an opening augment (one pick per side, applied at setup), a
     *  middle augment is granted mid-game, so these start empty and can only ever be added to. */
    private val middlePawnRetreat = mutableMapOf<Color, Boolean>()
    private val middleKingKnight = mutableMapOf<Color, Boolean>()

    /** Whether this game's single middle-augment draft (see [isMiddleDraftDue]) has already been offered. */
    private var middleDraftOffered = false

    /** Which sides have been granted each 엔드 증강 (end augment) win condition so far — see
     *  [applyEndAugment] and [status]. */
    private val endRacingKing = mutableSetOf<Color>()
    private val endDoubleCheck = mutableSetOf<Color>()
    private val endHighlander = mutableSetOf<Color>()

    /** Whether this game's single end-augment draft (see [isEndDraftDue]) has already been offered. */
    private var endDraftOffered = false

    /** One entry per side that drafted 존버, tracking where that pawn currently is. Removed the
     *  moment it's captured, promoted some other way, or its 14-ply timer fires. */
    private class TurtlingWatch(val color: Color, var square: Square)
    private val turtlingWatches = mutableListOf<TurtlingWatch>()

    /** How many of each piece type each color has had captured — 재활용's "already lost" credits. */
    private val capturedCounts = mutableMapOf<Color, MutableMap<PieceType, Int>>()
    /** How many of those credits each color has already spent on a promotion. */
    private val usedPromotionCounts = mutableMapOf<Color, MutableMap<PieceType, Int>>()

    /** Squares currently holding a piece that captured and hasn't made a non-capturing move since —
     *  매너's "no consecutive captures" bar. Tracked by square and followed as that piece moves,
     *  the same way [turtlingWatches] follows a specific pawn. */
    private val captureBarred = mutableSetOf<Square>()

    /** 왕관's randomly-chosen center square this game, or null if [crownRuleEnabled] is off. */
    private var crownSquare: Square? = null
    private var crownHolder: Color? = null
    private var crownHeldPlies = 0

    init {
        resetBoard()
    }

    private fun augmentFor(color: Color): OpeningAugment? = if (color == Color.WHITE) whiteAugment else blackAugment

    private fun resolvePromotionChoices(color: Color): List<PieceType> {
        val base = augmentFor(color)?.promotionChoicesOverride?.invoke(color) ?: AugmentRules.STANDARD.promotionChoices(color)
        if (!recycleRuleEnabled) return base
        val captured = capturedCounts[color].orEmpty()
        val used = usedPromotionCounts[color].orEmpty()
        return base.filter { type -> (captured[type] ?: 0) > (used[type] ?: 0) }
    }

    /** Resets to the standard starting position, re-applies each side's opening augment, and clears all per-game tracking. */
    fun resetBoard() {
        board.setup()
        whiteAugment?.setupTransform?.invoke(board, Color.WHITE)
        blackAugment?.setupTransform?.invoke(board, Color.BLACK)

        plyCount = 0
        capturedCounts.clear()
        usedPromotionCounts.clear()
        turtlingWatches.clear()
        whiteAugment?.turtlingFile?.let { turtlingWatches += TurtlingWatch(Color.WHITE, Square(it, 1)) }
        blackAugment?.turtlingFile?.let { turtlingWatches += TurtlingWatch(Color.BLACK, Square(it, 6)) }

        middlePawnRetreat.clear()
        middleKingKnight.clear()
        middleDraftOffered = false

        endRacingKing.clear()
        endDoubleCheck.clear()
        endHighlander.clear()
        endDraftOffered = false

        captureBarred.clear()
        crownSquare = if (crownRuleEnabled) CROWN_CANDIDATE_SQUARES.random() else null
        crownHolder = null
        crownHeldPlies = 0
    }

    /**
     * Whether the (single, per-game) 미들 증강 draft should be offered now. True exactly once per
     * game, the first time [plyCount] reaches [MIDDLE_DRAFT_PLY], until [markMiddleDraftOffered] is
     * called.
     *
     * The source material doesn't say how many plies apart middle-augment drafts actually occur in
     * the original game (docs/augmented-chess-design.md flags this as an open question) — this is a
     * documented assumption (one draft per game, at ply 10), not a sourced fact, and should be
     * revisited once that's confirmed.
     */
    fun isMiddleDraftDue(): Boolean = !middleDraftOffered && plyCount >= MIDDLE_DRAFT_PLY

    fun markMiddleDraftOffered() {
        middleDraftOffered = true
    }

    /** Grants [color] the effect(s) of [augment] for the rest of this game. */
    fun applyMiddleAugment(augment: MiddleAugment, color: Color) {
        if (augment.grantsPawnRetreat) middlePawnRetreat[color] = true
        if (augment.grantsKingKnightMoves) middleKingKnight[color] = true
    }

    /**
     * Whether the (single, per-game) 엔드 증강 draft should be offered now. Same shape as
     * [isMiddleDraftDue], just later and independent of it — true once [plyCount] reaches
     * [END_DRAFT_PLY], until [markEndDraftOffered] is called.
     *
     * Like the middle-draft cadence, the source material doesn't say how many plies apart
     * end-augment drafts actually occur — this is a documented assumption (one draft per game, at
     * ply 24, comfortably after the middle draft), not a sourced fact.
     */
    fun isEndDraftDue(): Boolean = !endDraftOffered && plyCount >= END_DRAFT_PLY

    fun markEndDraftOffered() {
        endDraftOffered = true
    }

    /** Grants [color] the win condition(s) of [augment] for the rest of this game. */
    fun applyEndAugment(augment: EndAugment, color: Color) {
        if (augment.grantsRacingKing) endRacingKing += color
        if (augment.grantsDoubleCheck) endDoubleCheck += color
        if (augment.grantsHighlander) endHighlander += color
    }

    /** Every geometrically legal move for the side to move. Unlike a check-filtered "legal moves"
     *  list, nothing is filtered out for leaving the mover's own king in check, since that's now allowed. */
    fun movesForSideToMove(): List<Move> = applyManners(MoveGenerator.allPseudoLegalMoves(board, board.sideToMove, rules))

    fun movesFrom(square: Square): List<Move> {
        val piece = board.pieceAt(square) ?: return emptyList()
        if (piece.color != board.sideToMove) return emptyList()
        return applyManners(MoveGenerator.pseudoLegalMoves(board, square, rules))
    }

    /** 매너: a move whose origin square is currently barred (see [captureBarred]) may not capture. */
    private fun applyManners(moves: List<Move>): List<Move> {
        if (!mannersRuleEnabled) return moves
        return moves.filterNot { move -> move.from in captureBarred && wouldCapture(move) }
    }

    private fun wouldCapture(move: Move): Boolean = move.isEnPassant || board.pieceAt(move.to) != null

    fun status(): AugmentedGameStatus {
        if (!kingAlive(Color.BLACK)) return AugmentedGameStatus.WHITE_WINS
        if (!kingAlive(Color.WHITE)) return AugmentedGameStatus.BLACK_WINS

        endGameWinner()?.let { return it }
        crownWinner()?.let { return it }

        if (movesForSideToMove().isEmpty()) {
            return if (board.sideToMove == Color.WHITE) AugmentedGameStatus.BLACK_WINS else AugmentedGameStatus.WHITE_WINS
        }
        return AugmentedGameStatus.ONGOING
    }

    private fun kingAlive(color: Color): Boolean =
        board.squares.any { it != null && it.type == PieceType.KING && it.color == color }

    /** Checks every granted 엔드 증강 win condition, in no particular priority — a pure board-state
     *  predicate for each, independent of whose move just happened. */
    private fun endGameWinner(): AugmentedGameStatus? {
        for (color in endRacingKing) {
            val backRank = if (color == Color.WHITE) 7 else 0
            if (board.findKing(color).rank == backRank) return winFor(color)
        }
        for (color in endDoubleCheck) {
            if (board.attackersOf(board.findKing(color.opposite()), color).size >= 2) return winFor(color)
        }
        for (color in endHighlander) {
            if (hasNoDuplicatePieces(color)) return winFor(color)
        }
        return null
    }

    private fun winFor(color: Color): AugmentedGameStatus =
        if (color == Color.WHITE) AugmentedGameStatus.WHITE_WINS else AugmentedGameStatus.BLACK_WINS

    /** 왕관: whoever has held [crownSquare] continuously for [CROWN_WIN_PLIES] plies wins — tracked
     *  ply-by-ply in [onMoveApplied], this just reads the result. */
    private fun crownWinner(): AugmentedGameStatus? {
        val holder = crownHolder ?: return null
        return if (crownHeldPlies >= CROWN_WIN_PLIES) winFor(holder) else null
    }

    /** True if [color] has at most one of each piece type on the board — 하이랜더's win condition.
     *  A lone king alone also satisfies this (0 of everything else counts as "no duplicates"),
     *  matching the source material's literal wording rather than requiring a minimum piece count. */
    private fun hasNoDuplicatePieces(color: Color): Boolean {
        val counts = mutableMapOf<PieceType, Int>()
        for (piece in board.squares) {
            if (piece != null && piece.color == color) counts[piece.type] = (counts[piece.type] ?: 0) + 1
        }
        return counts.values.all { it <= 1 }
    }

    /** Attempts to play a move from [from] to [to]. Returns the applied [Move], or null if illegal. */
    fun tryMove(from: Square, to: Square, promotion: PieceType? = null): Move? {
        val candidates = movesFrom(from).filter { it.to == to }
        if (candidates.isEmpty()) return null
        val move = if (candidates.size == 1) candidates[0]
        else candidates.find { it.promotion == (promotion ?: PieceType.QUEEN) } ?: candidates.first()

        // Captured-for-en-passant lands on a different square than `move.to`, so it has to be read
        // off the board before applyMove clears it, same as the direct-capture case.
        val captured = if (move.isEnPassant) board.pieceAt(Square(move.to.file, move.from.rank)) else board.pieceAt(move.to)

        board.applyMove(move)
        onMoveApplied(move, captured)
        return move
    }

    private fun onMoveApplied(move: Move, captured: Piece?) {
        plyCount++

        if (captured != null) {
            val counts = capturedCounts.getOrPut(captured.color) { mutableMapOf() }
            counts[captured.type] = (counts[captured.type] ?: 0) + 1
        }
        if (move.promotion != null) {
            val promotedColor = board.pieceAt(move.to)?.color
            if (promotedColor != null) {
                val used = usedPromotionCounts.getOrPut(promotedColor) { mutableMapOf() }
                used[move.promotion] = (used[move.promotion] ?: 0) + 1
            }
        }

        for (watch in turtlingWatches) {
            if (watch.square == move.from) watch.square = move.to
        }
        // Drops a watch the moment its square no longer holds that color's pawn — captured, or
        // already promoted some other way (e.g. it reached the normal promotion rank early).
        turtlingWatches.removeAll { watch ->
            val piece = board.pieceAt(watch.square)
            piece == null || piece.type != PieceType.PAWN || piece.color != watch.color
        }
        if (plyCount >= TURTLING_TRIGGER_PLIES && turtlingWatches.isNotEmpty()) {
            turtlingWatches.forEach { board.squares[it.square.index] = Piece(PieceType.QUEEN, it.color) }
            turtlingWatches.clear()
        }

        if (mannersRuleEnabled) {
            captureBarred.remove(move.from)
            if (captured != null) captureBarred.add(move.to) else captureBarred.remove(move.to)
        }

        if (crownRuleEnabled) {
            val square = crownSquare
            val occupant = if (square != null) board.pieceAt(square) else null
            val previousHolder = crownHolder
            crownHolder = occupant?.color
            crownHeldPlies = when {
                occupant == null -> 0
                occupant.color == previousHolder -> crownHeldPlies + 1
                else -> 1
            }
        }

        // 초월: a piece that just captured (and didn't also promote this same move — a promotion
        // already decided its final type) steps to the next tier. 퀸/킹 have no next tier, matching
        // the source material excluding the queen (and, implicitly, the king) from transcending.
        if (transcendRuleEnabled && captured != null && move.promotion == null) {
            val piece = board.pieceAt(move.to)
            val nextType = piece?.let { transcendedTypeOf(it.type) }
            if (piece != null && nextType != null) {
                board.squares[move.to.index] = Piece(nextType, piece.color)
            }
        }
    }

    /** The next tier in 초월's capture chain (폰 → 나이트 → 룩 → 퀸, or 비숍 → 룩 → 퀸), or null once a
     *  piece has nothing left to transcend into. Which minor piece a transcending pawn becomes isn't
     *  specified by the source material (only that it does), so this always picks 나이트 — a
     *  documented simplification, not a sourced rule, made to avoid adding a whole second
     *  choice-dialog flow (like promotion's) for a single ambiguous step. */
    private fun transcendedTypeOf(type: PieceType): PieceType? = when (type) {
        PieceType.PAWN -> PieceType.KNIGHT
        PieceType.KNIGHT, PieceType.BISHOP -> PieceType.ROOK
        PieceType.ROOK -> PieceType.QUEEN
        PieceType.QUEEN, PieceType.KING -> null
    }

    companion object {
        private const val TURTLING_TRIGGER_PLIES = 14
        const val MIDDLE_DRAFT_PLY = 10
        const val END_DRAFT_PLY = 24
        const val CROWN_WIN_PLIES = 10
        private val CROWN_CANDIDATE_SQUARES = listOf(Square(3, 3), Square(3, 4), Square(4, 3), Square(4, 4))
    }
}

enum class AugmentedGameStatus { ONGOING, WHITE_WINS, BLACK_WINS }
