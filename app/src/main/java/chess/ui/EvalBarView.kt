package chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ckdgusrkgus.chess.R
import kotlin.math.max
import kotlin.math.min

/**
 * A vertical evaluation bar (chess.com style): White's share of the bar height is proportional to
 * the position's centipawn score from White's point of view, clamped so a large material edge or
 * a forced mate both just max the bar out toward whoever is winning, rather than needing an exact
 * number to read.
 */
class EvalBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Centipawn score from White's point of view (positive = White is better). */
    var scoreForWhite: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** Matches [ChessBoardView.flipped]: true when Black is shown at the bottom, so White's share anchors to the top instead. */
    var flipped: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val whitePaint = Paint().apply { color = ContextCompat.getColor(context, R.color.piece_white_fill) }
    private val blackPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.piece_black_fill) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.piece_white_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val whiteHeight = h * whiteFractionOf(scoreForWhite)
        if (!flipped) {
            // White sits at the bottom of the board by default, so its share anchors to the bottom here too.
            canvas.drawRect(0f, 0f, w, h - whiteHeight, blackPaint)
            canvas.drawRect(0f, h - whiteHeight, w, h, whitePaint)
        } else {
            canvas.drawRect(0f, 0f, w, whiteHeight, whitePaint)
            canvas.drawRect(0f, whiteHeight, w, h, blackPaint)
        }
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, borderPaint)
    }

    companion object {
        // Beyond roughly a 10-pawn edge (or any forced-mate score, which is far larger) the bar is
        // already effectively maxed out, so there's no need to special-case mate scores separately.
        private const val CAP_CENTIPAWNS = 1000f

        /** Maps a White-relative score to White's fraction of the bar (0..1). */
        fun whiteFractionOf(scoreForWhite: Int): Float {
            val clamped = max(-CAP_CENTIPAWNS, min(CAP_CENTIPAWNS, scoreForWhite.toFloat()))
            return 0.5f + clamped / (2f * CAP_CENTIPAWNS)
        }
    }
}
