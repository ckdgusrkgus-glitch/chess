package chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import chess.ChessGame
import chess.Color
import chess.GameStatus
import chess.Move
import chess.MoveGenerator
import chess.Piece
import chess.PieceType
import chess.Square
import com.ckdgusrkgus.chess.R

/**
 * Draws an 8x8 chess board (white at the bottom, or black at the bottom when [flipped]) and
 * handles tap-to-move input.
 *
 * Tapping a piece shows every reachable square: a dot for a fully legal move, a ring for a
 * legal capture, and a small X for a square the piece could geometrically reach but that would
 * leave the mover's own king in check (illegal, shown only for feedback).
 */
class ChessBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onStatusChanged(status: GameStatus, sideToMove: Color, inCheck: Boolean)
        fun onPromotionNeeded(from: Square, to: Square, onChosen: (PieceType) -> Unit)
        fun onMoveMade(move: Move) {}
    }

    var listener: Listener? = null

    /** While false, taps are ignored — used to lock the board out while an AI move is computing. */
    var inputEnabled: Boolean = true

    /** True to draw/interpret the board from Black's side (Black's pieces at the bottom). */
    var flipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    val game = ChessGame()

    /** Bumped by [newGame]; lets a caller discard a stale async result (e.g. a superseded AI move). */
    val currentGeneration: Int get() = generation
    private var generation = 0

    private var selected: Square? = null
    private var legalTargets: List<Move> = emptyList()
    private var unsafeTargets: List<Move> = emptyList()
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
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.move_hint_unsafe_x)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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
    private val knightEyePath = Path().apply { addCircle(16f, 45f, 2.3f, Path.Direction.CW) }

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

    // Each shape is authored in a 0..100 unit square (see the scratch SVG prototype this was
    // checked against) and drawn via a canvas scale, so PIECE_STROKE_WIDTH is in the same units.
    /** The rounded, flared plinth shared by every piece (a trapezoid collar with an outward curl at each bottom corner). */
    private fun Path.addFlaredBase(left: Float, right: Float, topY: Float, bottomY: Float) {
        val flare = 4f
        moveTo(left, topY)
        lineTo(right, topY)
        lineTo(right + flare, bottomY - flare)
        quadTo(right + flare, bottomY, right, bottomY)
        lineTo(left, bottomY)
        quadTo(left - flare, bottomY, left - flare, bottomY - flare)
        close()
    }

    private fun pawnPath() = Path().apply {
        addCircle(50f, 24f, 11f, Path.Direction.CW)
        moveTo(42f, 37f)
        cubicTo(34f, 42f, 29f, 49f, 29f, 57f)
        cubicTo(29f, 64f, 33f, 69f, 40f, 72f)
        lineTo(60f, 72f)
        cubicTo(67f, 69f, 71f, 64f, 71f, 57f)
        cubicTo(71f, 49f, 66f, 42f, 58f, 37f)
        cubicTo(54f, 40f, 46f, 40f, 42f, 37f)
        close()
        addFlaredBase(24f, 76f, 72f, 84f)
    }

    private fun rookPath() = Path().apply {
        moveTo(27f, 15f); lineTo(38f, 15f); lineTo(38f, 24f); lineTo(44f, 24f); lineTo(44f, 15f)
        lineTo(56f, 15f); lineTo(56f, 24f); lineTo(62f, 24f); lineTo(62f, 15f); lineTo(73f, 15f)
        lineTo(73f, 32f); lineTo(27f, 32f); close()
        moveTo(30f, 32f); lineTo(70f, 32f); lineTo(67f, 66f); lineTo(33f, 66f); close()
        addFlaredBase(22f, 78f, 66f, 84f)
    }

    private fun bishopPath() = Path().apply {
        addCircle(50f, 10f, 4f, Path.Direction.CW)
        moveTo(50f, 16f)
        cubicTo(58f, 17f, 62f, 25f, 58f, 32f)
        cubicTo(66f, 38f, 70f, 47f, 70f, 56f)
        cubicTo(70f, 65f, 61f, 72f, 50f, 74f)
        cubicTo(39f, 72f, 30f, 65f, 30f, 56f)
        cubicTo(30f, 47f, 34f, 38f, 42f, 32f)
        cubicTo(38f, 25f, 42f, 17f, 50f, 16f)
        close()
        addFlaredBase(24f, 76f, 74f, 85f)
        moveTo(39f, 30f); lineTo(58f, 20f) // the mitre's diagonal slit; open contour, stroke-only
    }

    private fun knightPath() = Path().apply {
        moveTo(68f, 72f)
        lineTo(68f, 53f)
        quadTo(74f, 48f, 80f, 43f)
        quadTo(73f, 38f, 66f, 33f)
        lineTo(70f, 19f)
        lineTo(60f, 3f)
        lineTo(50f, 19f)
        quadTo(40f, 15f, 30f, 13f)
        quadTo(20f, 23f, 14f, 35f)
        quadTo(10f, 39f, 10f, 43f)
        quadTo(10f, 47f, 14f, 51f)
        quadTo(20f, 55f, 26f, 59f)
        lineTo(28f, 72f)
        close()
        addFlaredBase(24f, 76f, 72f, 84f)
    }

    private fun queenPath() = Path().apply {
        moveTo(32f, 42f)
        cubicTo(27f, 48f, 25f, 54f, 25f, 60f)
        cubicTo(25f, 67f, 29f, 72f, 36f, 75f)
        lineTo(64f, 75f)
        cubicTo(71f, 72f, 75f, 67f, 75f, 60f)
        cubicTo(75f, 54f, 73f, 48f, 68f, 42f)
        close()
        moveTo(22f, 34f)
        lineTo(27f, 23f); lineTo(33f, 32f); lineTo(40f, 21f); lineTo(46f, 31f); lineTo(50f, 19f)
        lineTo(54f, 31f); lineTo(60f, 21f); lineTo(67f, 32f); lineTo(73f, 23f); lineTo(78f, 34f)
        lineTo(78f, 42f); lineTo(22f, 42f)
        close()
        addCircle(27f, 23f, 4.2f, Path.Direction.CW)
        addCircle(40f, 21f, 4.2f, Path.Direction.CW)
        addCircle(50f, 19f, 4.6f, Path.Direction.CW)
        addCircle(60f, 21f, 4.2f, Path.Direction.CW)
        addCircle(73f, 23f, 4.2f, Path.Direction.CW)
        addFlaredBase(24f, 76f, 75f, 86f)
    }

    private fun kingPath() = Path().apply {
        moveTo(34f, 46f)
        cubicTo(29f, 52f, 27f, 58f, 27f, 63f)
        cubicTo(27f, 70f, 31f, 75f, 38f, 78f)
        lineTo(62f, 78f)
        cubicTo(69f, 75f, 73f, 70f, 73f, 63f)
        cubicTo(73f, 58f, 71f, 52f, 66f, 46f)
        close()
        moveTo(31f, 40f); quadTo(50f, 32f, 69f, 40f); lineTo(69f, 46f); lineTo(31f, 46f); close()
        addRoundRect(47f, 10f, 53f, 28f, 2f, 2f, Path.Direction.CW)
        addRoundRect(41f, 15f, 59f, 21f, 2f, 2f, Path.Direction.CW)
        addFlaredBase(24f, 76f, 78f, 89f)
    }

    /** Maps a screen (row, col) grid cell to a board square, honoring [flipped]. */
    private fun squareAt(row: Int, col: Int): Square =
        if (flipped) Square(7 - col, row) else Square(col, 7 - row)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = minOf(w, h) / 8f

        captureRingPaint.strokeWidth = cellSize * 0.06f
        xPaint.strokeWidth = cellSize * 0.05f

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
                } else if (unsafeTargets.any { it.to == square }) {
                    drawX(canvas, left + cellSize / 2, top + cellSize / 2, cellSize * 0.16f)
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

    private fun drawX(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, xPaint)
        canvas.drawLine(cx - half, cy + half, cx + half, cy - half, xPaint)
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
        if (game.status() != GameStatus.ONGOING) return

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
            if (listenerRef != null) {
                listenerRef.onPromotionNeeded(from, square) { chosen ->
                    val applied = game.tryMove(from, square, chosen)
                    if (applied != null) afterMove(applied) else deselect()
                }
            } else {
                val applied = game.tryMove(from, square, PieceType.QUEEN)
                if (applied != null) afterMove(applied) else deselect()
            }
        } else {
            val applied = game.tryMove(currentSelected, square)
            if (applied != null) afterMove(applied) else deselect()
        }
    }

    private fun select(square: Square) {
        selected = square
        val legal = MoveGenerator.legalMovesFrom(game.board, square)
        legalTargets = legal
        val legalToSquares = legal.map { it.to }.toSet()
        unsafeTargets = MoveGenerator.pseudoLegalMoves(game.board, square)
            .filter { it.to !in legalToSquares }
            .distinctBy { it.to }
        invalidate()
    }

    private fun deselect() {
        selected = null
        legalTargets = emptyList()
        unsafeTargets = emptyList()
        invalidate()
    }

    private fun afterMove(move: Move) {
        lastMove = move
        selected = null
        legalTargets = emptyList()
        unsafeTargets = emptyList()
        invalidate()
        listener?.onMoveMade(move)
        refreshStatus()
    }

    fun newGame() {
        generation++
        game.board.setup()
        lastMove = null
        inputEnabled = true
        deselect()
        refreshStatus()
    }

    /**
     * Applies a move computed off-thread (e.g. by [chess.ai.ChessAi]). Ignored if [expectedGeneration]
     * no longer matches [currentGeneration], meaning the game was reset while the move was computing.
     */
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
        private const val PIECE_STROKE_WIDTH = 3f
    }
}
