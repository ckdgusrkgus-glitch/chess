package chess.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import chess.network.LichessExplorer
import chess.network.MoveStat
import chess.network.PositionStats
import chess.parseAlgebraicMove
import chess.toFen
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Shows how often real players continued from the position reached in [ReviewActivity] — pulled
 * from Lichess's rated-games database, which covers whatever position those games actually
 * reached, not just known opening theory. So this works from any position reviewed, not only the
 * opening moves.
 */
class CommunityStatsActivity : AppCompatActivity() {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView
    private lateinit var movesContainer: LinearLayout

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_stats)

        boardView = findViewById(R.id.communityBoardView)
        statusText = findViewById(R.id.communityStatusText)
        movesContainer = findViewById(R.id.communityMovesContainer)
        findViewById<MaterialButton>(R.id.communityBackButton).setOnClickListener { finish() }

        val replayMoves = intent.getStringArrayListExtra(EXTRA_REPLAY_MOVES) ?: emptyList()
        boardView.flipped = intent.getBooleanExtra(EXTRA_FLIPPED, false)
        boardView.inputEnabled = false

        boardView.newGame()
        val generation = boardView.currentGeneration
        for (notation in replayMoves) {
            val move = parseAlgebraicMove(boardView.game.board, notation) ?: break
            boardView.applyExternalMove(move, generation)
        }
        val fen = boardView.game.board.toFen()

        statusText.text = getString(R.string.community_stats_loading)
        analysisExecutor.execute {
            val stats = LichessExplorer.fetch(fen)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                render(stats)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun render(stats: PositionStats?) {
        if (stats == null) {
            statusText.text = getString(R.string.community_stats_error)
            return
        }
        if (stats.moves.isEmpty()) {
            statusText.text = getString(R.string.community_stats_empty)
            return
        }
        statusText.text = getString(R.string.community_stats_total, formatCount(stats.totalGames))
        movesContainer.removeAllViews()
        stats.moves.forEachIndexed { index, move ->
            movesContainer.addView(buildMoveRow(index + 1, move, stats.totalGames))
        }
    }

    private fun buildMoveRow(rank: Int, move: MoveStat, overallTotal: Int): LinearLayout {
        val percent = if (overallTotal > 0) move.total * 100 / overallTotal else 0

        val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val moveLabel = TextView(this).apply {
            text = getString(R.string.community_stats_move_label, rank, move.san)
            setTextColor(ContextCompat.getColor(this@CommunityStatsActivity, R.color.text_primary))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val detailLabel = TextView(this).apply {
            text = getString(R.string.community_stats_move_detail, percent, formatCount(move.total))
            setTextColor(ContextCompat.getColor(this@CommunityStatsActivity, R.color.text_secondary))
            textSize = 12f
        }
        labelRow.addView(moveLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        labelRow.addView(detailLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(6)).apply {
                topMargin = dpToPx(4)
            }
            addView(outcomeSegment(move.white, R.color.piece_white_fill))
            addView(outcomeSegment(move.draws, R.color.move_quality_good))
            addView(outcomeSegment(move.black, R.color.piece_black_fill))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(12)
            }
            addView(labelRow)
            addView(barRow)
        }
    }

    /** One colored segment of the white/draw/black outcome bar, sized by [count] via LinearLayout's own weight distribution. */
    private fun outcomeSegment(count: Int, colorRes: Int): View = View(this).apply {
        setBackgroundColor(ContextCompat.getColor(this@CommunityStatsActivity, colorRes))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, count.coerceAtLeast(0).toFloat())
    }

    private fun formatCount(count: Int): String = String.format(Locale.US, "%,d", count)

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_REPLAY_MOVES = "chess.ui.EXTRA_REPLAY_MOVES"
        const val EXTRA_FLIPPED = "chess.ui.EXTRA_FLIPPED"
    }
}
