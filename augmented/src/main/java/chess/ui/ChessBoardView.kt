package chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import chess.AugmentedChessGame
import chess.AugmentedGameStatus
import chess.Color
import chess.Move
import chess.Piece
import chess.PieceType
import chess.Square
import chess.augment.OpeningAugment
import com.ckdgusrkgus.augmentedchess.R

/**
 * Draws an 8x8 chess board (white at the bottom, or black at the bottom when [flipped]) and
 * handles tap-to-move input, playing by [AugmentedChessGame]'s rules.
 *
 * Tapping a piece shows every reachable square as a legal move — unlike the standard-chess board
 * this was adapted from, there's no "geometrically reachable but unsafe" category to distinguish
 * here, since a king may freely move into (or stay in) check under these rules.
 */
class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onStatusChanged(status: AugmentedGameStatus, sideToMove: Color, inCheck: Boolean)
        fun onPromotionNeeded(from: Square, to: Square, choices: List<PieceType>, onChosen: (PieceType) -> Unit)
        fun onMoveMade(move: Move) {}
    }

    var listener: Listener? = null

    /** While false, taps are ignored — used to lock the board out while an opponent move is computing. */
    var inputEnabled: Boolean = true

    /** True to draw/interpret the board from Black's side (Black's pieces at the bottom). */
    var flipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var _game = AugmentedChessGame()
    val game: AugmentedChessGame get() = _game

    /** Bumped by [newGame]; lets a caller discard a stale async result. */
    val currentGeneration: Int get() = generation
    private var generation = 0

    private var selected: Square? = null
    private var legalTargets: List<Move> = emptyList()
    private var lastMove: Move? = null

    private var cellSize = 0f

    private val lightPaint = solidPaint(R.color.board_light)
    private val darkPaint = solidPaint(R.color.board_dark)
    private val selectedLightPaint = solidPaint(R.color.board_light_selected)
    private val selectedDarkPaint = solidPaint(R.color.board_dark_selected)
    private val lastMoveLightPaint = solidPaint(R.color.board_light_last_move)
    private val lastMoveDarkPaint = solidPaint(R.color.board_dark_last_move)
    private val checkPaint = solidPaint(R.color.check_highlight)

    private val hintDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.move_hint_dot)
        style = Paint.Style.FILL
    }
    private val captureRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.move_hint_capture_ring)
        style = Paint.Style.STROKE
    }

    // Pieces are hand-drawn shapes, not text glyphs: some devices render the ♔♛♜ Unicode chess
    // symbols through a color-emoji font that ignores Paint's color entirely, making every piece
    // look identical regardless of side. A Path we draw ourselves can't be overridden like that.
    private val whiteFillPaint = shapePaint(R.color.piece_white_fill, Paint.Style.FILL)
    private val whiteStrokePaint = shapePaint(R.color.piece_white_stroke, Paint.Style.STROKE)
    private val blackFillPaint = shapePaint(R.color.piece_black_fill, Paint.Style.FILL)
    private val blackStrokePaint = shapePaint(R.color.piece_black_stroke, Paint.Style.STROKE)

    // The knight's eye is a small dot drawn in the opposite tone from its own body, not the
    // square color, so it reads the same on light and dark squares.
    private val whiteEyePaint = shapePaint(R.color.piece_white_stroke, Paint.Style.FILL)
    private val blackEyePaint = shapePaint(R.color.piece_white_fill, Paint.Style.FILL)
    private val knightEyePath = Path().apply { addCircle(24f, 37f, 2.4f, Path.Direction.CW) }

    /** Piece silhouettes, each authored in a fixed 0..100 unit square and scaled to [cellSize] when drawn. */
    private val piecePaths: Map<PieceType, Path> = mapOf(
        PieceType.PAWN to pawnPath(),
        PieceType.ROOK to rookPath(),
        PieceType.BISHOP to bishopPath(),
        PieceType.KNIGHT to knightPath(),
        PieceType.QUEEN to queenPath(),
        PieceType.KING to kingPath()
    )

    private val labelOnLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_dark)
        textAlign = Paint.Align.LEFT
    }
    private val labelOnDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_light)
        textAlign = Paint.Align.LEFT
    }

    init {
        isClickable = true
    }

    private fun solidPaint(colorRes: Int) = Paint().apply { color = ContextCompat.getColor(context, colorRes) }

    private fun shapePaint(colorRes: Int, paintStyle: Paint.Style) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        style = paintStyle
        if (paintStyle == Paint.Style.STROKE) {
            strokeWidth = PIECE_STROKE_WIDTH
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    // Each shape is authored in a 0..100 unit square and drawn via a canvas scale, so
    // PIECE_STROKE_WIDTH is in the same units. Bodies are deliberately stout (short and wide) to
    // match the reference set, which favors chunky, rounded silhouettes over tall/thin ones.
    private fun pawnPath() = Path().apply {
        addCircle(50f, 22f, 12f, Path.Direction.CW)
        moveTo(40f, 32f)
        cubicTo(30f, 38f, 24f, 46f, 24f, 54f)
        cubicTo(24f, 60f, 29f, 64f, 36f, 66f)
        lineTo(64f, 66f)
        cubicTo(71f, 64f, 76f, 60f, 76f, 54f)
        cubicTo(76f, 46f, 70f, 38f, 60f, 32f)
        cubicTo(55f, 35f, 45f, 35f, 40f, 32f)
        close()
        addRoundRect(22f, 66f, 78f, 80f, 6f, 6f, Path.Direction.CW)
    }

    private fun rookPath() = Path().apply {
        moveTo(22f, 14f); lineTo(34f, 14f); lineTo(34f, 26f); lineTo(44f, 26f); lineTo(44f, 14f)
        lineTo(56f, 14f); lineTo(56f, 26f); lineTo(66f, 26f); lineTo(66f, 14f); lineTo(78f, 14f)
        lineTo(78f, 32f); lineTo(22f, 32f); close()
        moveTo(28f, 32f); lineTo(72f, 32f); lineTo(70f, 62f); lineTo(30f, 62f); close()
        addRoundRect(22f, 62f, 78f, 78f, 6f, 6f, Path.Direction.CW)
    }

    private fun bishopPath() = Path().apply {
        addCircle(50f, 12f, 4.5f, Path.Direction.CW)
        moveTo(50f, 18f)
        cubicTo(60f, 19f, 65f, 28f, 60f, 36f)
        cubicTo(69f, 42f, 73f, 50f, 73f, 58f)
        cubicTo(73f, 66f, 63f, 72f, 50f, 74f)
        cubicTo(37f, 72f, 27f, 66f, 27f, 58f)
        cubicTo(27f, 50f, 31f, 42f, 40f, 36f)
        cubicTo(35f, 28f, 40f, 19f, 50f, 18f)
        close()
        addRoundRect(24f, 74f, 76f, 88f, 6f, 6f, Path.Direction.CW)
        moveTo(38f, 32f); lineTo(58f, 22f) // the mitre's diagonal slit; open contour, stroke-only
    }

    private fun knightPath() = Path().apply {
        moveTo(64f, 68f)
        lineTo(64f, 50f)
        quadTo(69f, 46f, 74f, 42f)
        quadTo(68f, 37f, 62f, 33f)
        lineTo(64f, 22f)
        lineTo(54f, 9f)
        lineTo(46f, 22f)
        quadTo(38f, 18f, 30f, 17f)
        quadTo(22f, 25f, 18f, 35f)
        quadTo(15f, 38f, 16f, 42f)
        quadTo(17f, 46f, 22f, 49f)
        quadTo(27f, 52f, 32f, 55f)
        lineTo(33f, 68f)
        close()
        addRoundRect(22f, 68f, 78f, 82f, 6f, 6f, Path.Direction.CW)
    }

    private fun queenPath() = Path().apply {
        moveTo(34f, 44f)
        cubicTo(29f, 50f, 27f, 56f, 27f, 62f)
        cubicTo(27f, 68f, 31f, 72f, 37f, 75f)
        lineTo(63f, 75f)
        cubicTo(69f, 72f, 73f, 68f, 73f, 62f)
        cubicTo(73f, 56f, 71f, 50f, 66f, 44f)
        close()
        moveTo(24f, 36f)
        lineTo(29f, 26f); lineTo(35f, 34f); lineTo(41f, 24f); lineTo(46f, 33f); lineTo(50f, 22f)
        lineTo(54f, 33f); lineTo(59f, 24f); lineTo(65f, 34f); lineTo(71f, 26f); lineTo(76f, 36f)
        lineTo(76f, 44f); lineTo(24f, 44f)
        close()
        addCircle(29f, 26f, 4f, Path.Direction.CW)
        addCircle(41f, 24f, 4f, Path.Direction.CW)
        addCircle(50f, 22f, 4.4f, Path.Direction.CW)
        addCircle(59f, 24f, 4f, Path.Direction.CW)
        addCircle(71f, 26f, 4f, Path.Direction.CW)
        addRoundRect(22f, 75f, 78f, 89f, 6f, 6f, Path.Direction.CW)
    }

    private fun kingPath() = Path().apply {
        moveTo(36f, 48f)
        cubicTo(31f, 54f, 29f, 60f, 29f, 65f)
        cubicTo(29f, 71f, 33f, 75f, 39f, 78f)
        lineTo(61f, 78f)
        cubicTo(67f, 75f, 71f, 71f, 71f, 65f)
        cubicTo(71f, 60f, 69f, 54f, 64f, 48f)
        close()
        moveTo(33f, 42f); quadTo(50f, 35f, 67f, 42f); lineTo(67f, 48f); lineTo(33f, 48f); close()
        addRoundRect(47f, 12f, 53f, 30f, 2f, 2f, Path.Direction.CW)
        addRoundRect(41f, 17f, 59f, 23f, 2f, 2f, Path.Direction.CW)
        addRoundRect(22f, 78f, 78f, 92f, 6f, 6f, Path.Direction.CW)
    }

    /** Maps a screen (row, col) grid cell to a board square, honoring [flipped]. */
    private fun squareAt(row: Int, col: Int): Square =
        if (flipped) Square(7 - col, row) else Square(col, 7 - row)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = minOf(w, h) / 8f

        captureRingPaint.strokeWidth = cellSize * 0.06f

        labelOnLightPaint.textSize = cellSize * 0.16f
        labelOnDarkPaint.textSize = cellSize * 0.16f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSize <= 0f) return

        val checkedKingSquare = if (game.board.isInCheck(game.board.sideToMove)) {
            game.board.findKing(game.board.sideToMove)
        } else null
        val move = lastMove

        for (row in 0..7) {
            for (col in 0..7) {
                val square = squareAt(row, col)
                val file = square.file
                val rank = square.rank
                val isDark = (file + rank) % 2 == 0
                val left = col * cellSize
                val top = row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                val bgPaint = when {
                    square == selected -> if (isDark) selectedDarkPaint else selectedLightPaint
                    square == checkedKingSquare -> checkPaint
                    move != null && (square == move.from || square == move.to) ->
                        if (isDark) lastMoveDarkPaint else lastMoveLightPaint
                    isDark -> darkPaint
                    else -> lightPaint
                }
                canvas.drawRect(left, top, right, bottom, bgPaint)

                if (col == 0) {
                    val labelPaint = if (isDark) labelOnDarkPaint else labelOnLightPaint
                    canvas.drawText((rank + 1).toString(), left + cellSize * 0.05f, top + cellSize * 0.22f, labelPaint)
                }
                if (row == 7) {
                    val labelPaint = if (isDark) labelOnDarkPaint else labelOnLightPaint
                    val letter = ('a' + file).toString()
                    val tw = labelPaint.measureText(letter)
                    canvas.drawText(letter, right - tw - cellSize * 0.06f, bottom - cellSize * 0.08f, labelPaint)
                }

                val piece = game.board.pieceAt(square)
                if (piece != null) {
                    drawPiece(canvas, piece, left, top)
                }

                if (legalTargets.any { it.to == square }) {
                    val cx = left + cellSize / 2
                    val cy = top + cellSize / 2
                    if (piece != null) {
                        canvas.drawCircle(cx, cy, cellSize * 0.42f, captureRingPaint)
                    } else {
                        canvas.drawCircle(cx, cy, cellSize * 0.14f, hintDotPaint)
                    }
                }
            }
        }
    }

    private fun drawPiece(canvas: Canvas, piece: Piece, left: Float, top: Float) {
        val path = piecePaths.getValue(piece.type)
        val fillPaint = if (piece.color == Color.WHITE) whiteFillPaint else blackFillPaint
        val strokePaint = if (piece.color == Color.WHITE) whiteStrokePaint else blackStrokePaint
        val scale = cellSize / 100f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        if (piece.type == PieceType.KNIGHT) {
            canvas.drawPath(knightEyePath, if (piece.color == Color.WHITE) whiteEyePaint else blackEyePaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && cellSize > 0f) {
            val col = (event.x / cellSize).toInt().coerceIn(0, 7)
            val row = (event.y / cellSize).toInt().coerceIn(0, 7)
            handleTap(squareAt(row, col))
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(square: Square) {
        if (!inputEnabled) return
        if (game.status() != AugmentedGameStatus.ONGOING) return

        val currentSelected = selected
        val tappedPiece = game.board.pieceAt(square)

        if (currentSelected == null) {
            if (tappedPiece != null && tappedPiece.color == game.board.sideToMove) select(square)
            return
        }

        if (square == currentSelected) {
            deselect()
            return
        }

        if (tappedPiece != null && tappedPiece.color == game.board.sideToMove) {
            select(square)
            return
        }

        val candidates = legalTargets.filter { it.to == square }
        if (candidates.isEmpty()) {
            deselect()
            return
        }

        if (candidates.size > 1) {
            val from = currentSelected
            val listenerRef = listener
            // The candidates ARE the promotion options for this move (one per possible piece,
            // per chess.AugmentRules.promotionChoices for the mover's color) — never assume the
            // standard Q/R/B/N set, since an augment like Hasty Promotion narrows it.
            val choices = candidates.mapNotNull { it.promotion }
            if (listenerRef != null) {
                listenerRef.onPromotionNeeded(from, square, choices) { chosen ->
                    val applied = game.tryMove(from, square, chosen)
                    if (applied != null) afterMove(applied) else deselect()
                }
            } else {
                val applied = game.tryMove(from, square, choices.first())
                if (applied != null) afterMove(applied) else deselect()
            }
        } else {
            val applied = game.tryMove(currentSelected, square)
            if (applied != null) afterMove(applied) else deselect()
        }
    }

    private fun select(square: Square) {
        selected = square
        legalTargets = game.movesFrom(square)
        invalidate()
    }

    private fun deselect() {
        selected = null
        legalTargets = emptyList()
        invalidate()
    }

    private fun afterMove(move: Move) {
        lastMove = move
        selected = null
        legalTargets = emptyList()
        invalidate()
        listener?.onMoveMade(move)
        refreshStatus()
    }

    /** Resets to the current game's starting position (re-applying whichever opening augments [configureAugments] set). */
    fun newGame() {
        generation++
        _game.resetBoard()
        lastMove = null
        inputEnabled = true
        deselect()
        refreshStatus()
    }

    /**
     * Sets each side's drafted opening augment (see [chess.augment.OpeningAugment]) and starts a
     * fresh game with them applied. Must be called before the board is otherwise used — a
     * [ChessBoardView] is inflated from XML with no constructor arguments, so this is how a caller
     * (e.g. an Activity reading Intent extras) hands it the augments picked for this game.
     */
    fun configureAugments(whiteAugment: OpeningAugment?, blackAugment: OpeningAugment?) {
        _game = AugmentedChessGame(whiteAugment, blackAugment)
        generation++
        lastMove = null
        inputEnabled = true
        deselect()
        refreshStatus()
    }

    /** Applies a move computed off-thread. Ignored if [expectedGeneration] no longer matches
     *  [currentGeneration], meaning the game was reset while the move was being computed. */
    fun applyExternalMove(move: Move, expectedGeneration: Int) {
        if (expectedGeneration != generation) return
        game.board.applyMove(move)
        afterMove(move)
    }

    fun refreshStatus() {
        listener?.onStatusChanged(game.status(), game.board.sideToMove, game.board.isInCheck(game.board.sideToMove))
    }

    companion object {
        /** Stroke width in the same 0..100 unit space the piece paths are authored in. */
        private const val PIECE_STROKE_WIDTH = 4f
    }
}
