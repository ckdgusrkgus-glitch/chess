package chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val knightEyePath = Path().apply {
        addOval(RectF(22.6f, 45.6f, 29.4f, 50.4f), Path.Direction.CW)
        transform(Matrix().apply { setRotate(-20f, 26f, 48f) })
    }

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
    // Bodies are deliberately stout (short and wide) to match the reference set, which favors
    // chunky, rounded silhouettes over tall/thin ones.
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
        moveTo(46f, 6f)
        lineTo(54f, 20f)
        cubicTo(64f, 23f, 76f, 32f, 76f, 48f)
        cubicTo(76f, 58f, 72f, 66f, 66f, 72f)
        lineTo(66f, 84f)
        lineTo(34f, 84f)
        lineTo(34f, 72f)
        lineTo(24f, 70f)
        lineTo(24f, 65f)
        lineTo(16f, 70f)
        lineTo(18f, 54f)
        cubicTo(19f, 46f, 21f, 41f, 24f, 37f)
        cubicTo(22f, 29f, 26f, 19f, 36f, 14f)
        lineTo(42f, 10f)
        close()
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
        private const val PIECE_STROKE_WIDTH = 4f
    }
}
