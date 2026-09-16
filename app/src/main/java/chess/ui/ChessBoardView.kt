package chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
 * Draws an 8x8 chess board (white always at the bottom) and handles tap-to-move input.
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
    }

    var listener: Listener? = null

    /** While false, taps are ignored — used to lock the board out while an AI move is computing. */
    var inputEnabled: Boolean = true

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

    private val whiteFillPaint = textPaint(R.color.piece_white_fill, Paint.Style.FILL)
    private val whiteStrokePaint = textPaint(R.color.piece_white_stroke, Paint.Style.STROKE)
    private val blackFillPaint = textPaint(R.color.piece_black_fill, Paint.Style.FILL)
    private val blackStrokePaint = textPaint(R.color.piece_black_stroke, Paint.Style.STROKE)

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

    private fun textPaint(colorRes: Int, paintStyle: Paint.Style) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        style = paintStyle
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = minOf(w, h) / 8f

        val textSize = cellSize * 0.72f
        whiteFillPaint.textSize = textSize
        whiteStrokePaint.textSize = textSize
        blackFillPaint.textSize = textSize
        blackStrokePaint.textSize = textSize
        whiteStrokePaint.strokeWidth = cellSize * 0.045f
        blackStrokePaint.strokeWidth = cellSize * 0.045f

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
                val file = col
                val rank = 7 - row
                val square = Square(file, rank)
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
        val cx = left + cellSize / 2
        val cy = top + cellSize / 2 - (whiteStrokePaint.ascent() + whiteStrokePaint.descent()) / 2
        val glyph = glyphFor(piece.type)
        val strokePaint = if (piece.color == Color.WHITE) whiteStrokePaint else blackStrokePaint
        val fillPaint = if (piece.color == Color.WHITE) whiteFillPaint else blackFillPaint
        canvas.drawText(glyph, cx, cy, strokePaint)
        canvas.drawText(glyph, cx, cy, fillPaint)
    }

    private fun drawX(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, xPaint)
        canvas.drawLine(cx - half, cy + half, cx + half, cy - half, xPaint)
    }

    private fun glyphFor(type: PieceType): String = when (type) {
        PieceType.KING -> "♚"
        PieceType.QUEEN -> "♛"
        PieceType.ROOK -> "♜"
        PieceType.BISHOP -> "♝"
        PieceType.KNIGHT -> "♞"
        PieceType.PAWN -> "♟"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && cellSize > 0f) {
            val col = (event.x / cellSize).toInt().coerceIn(0, 7)
            val row = (event.y / cellSize).toInt().coerceIn(0, 7)
            handleTap(Square(col, 7 - row))
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
}
