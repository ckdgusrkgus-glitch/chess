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
    private fun pawnPath() = Path().apply {
        addCircle(50f, 32f, 11f, Path.Direction.CW)
        moveTo(38f, 45f); lineTo(62f, 45f); lineTo(70f, 68f); lineTo(30f, 68f); close()
        addRect(26f, 68f, 74f, 78f, Path.Direction.CW)
    }

    private fun rookPath() = Path().apply {
        addRect(28f, 16f, 40f, 28f, Path.Direction.CW)
        addRect(44f, 16f, 56f, 28f, Path.Direction.CW)
        addRect(60f, 16f, 72f, 28f, Path.Direction.CW)
        addRect(28f, 28f, 72f, 34f, Path.Direction.CW)
        moveTo(32f, 34f); lineTo(68f, 34f); lineTo(70f, 68f); lineTo(30f, 68f); close()
        addRect(24f, 68f, 76f, 78f, Path.Direction.CW)
    }

    private fun bishopPath() = Path().apply {
        addCircle(50f, 14f, 4.5f, Path.Direction.CW)
        moveTo(44f, 22f)
        quadTo(32f, 35f, 32f, 50f)
        quadTo(32f, 62f, 38f, 68f)
        lineTo(62f, 68f)
        quadTo(68f, 62f, 68f, 50f)
        quadTo(68f, 35f, 56f, 22f)
        close()
        addRect(28f, 68f, 72f, 78f, Path.Direction.CW)
        moveTo(42f, 38f); lineTo(58f, 30f) // the mitre's diagonal slit; open contour, stroke-only
    }

    private fun knightPath() = Path().apply {
        moveTo(68f, 78f)
        lineTo(68f, 58f); lineTo(58f, 48f); lineTo(64f, 40f); lineTo(56f, 36f); lineTo(60f, 28f)
        lineTo(50f, 20f); lineTo(45f, 14f); lineTo(41f, 22f); lineTo(48f, 24f); lineTo(28f, 26f)
        lineTo(18f, 36f); lineTo(24f, 40f); lineTo(20f, 46f); lineTo(28f, 44f); lineTo(34f, 54f)
        lineTo(32f, 66f); lineTo(32f, 78f)
        close()
    }

    private fun queenPath() = Path().apply {
        moveTo(36f, 38f)
        quadTo(28f, 48f, 28f, 55f)
        quadTo(28f, 62f, 32f, 68f)
        lineTo(68f, 68f)
        quadTo(72f, 62f, 72f, 55f)
        quadTo(72f, 48f, 64f, 38f)
        close()
        moveTo(26f, 34f)
        lineTo(28f, 26f); lineTo(33f, 32f); lineTo(39f, 24f); lineTo(44f, 32f); lineTo(50f, 22f)
        lineTo(56f, 32f); lineTo(61f, 24f); lineTo(67f, 32f); lineTo(72f, 26f); lineTo(74f, 34f)
        lineTo(74f, 38f); lineTo(26f, 38f)
        close()
        addCircle(28f, 26f, 4f, Path.Direction.CW)
        addCircle(39f, 24f, 4f, Path.Direction.CW)
        addCircle(50f, 22f, 4.5f, Path.Direction.CW)
        addCircle(61f, 24f, 4f, Path.Direction.CW)
        addCircle(72f, 26f, 4f, Path.Direction.CW)
        addRect(26f, 68f, 74f, 78f, Path.Direction.CW)
    }

    private fun kingPath() = Path().apply {
        moveTo(38f, 42f)
        quadTo(30f, 50f, 30f, 58f)
        quadTo(30f, 64f, 34f, 68f)
        lineTo(66f, 68f)
        quadTo(70f, 64f, 70f, 58f)
        quadTo(70f, 50f, 62f, 42f)
        close()
        addRect(32f, 34f, 68f, 42f, Path.Direction.CW)
        addRect(47f, 14f, 53f, 32f, Path.Direction.CW)
        addRect(40f, 19f, 60f, 25f, Path.Direction.CW)
        addRect(28f, 68f, 72f, 78f, Path.Direction.CW)
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
