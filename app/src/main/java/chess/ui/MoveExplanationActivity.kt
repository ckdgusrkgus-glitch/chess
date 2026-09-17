package chess.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import chess.Board
import chess.Move
import chess.PieceType
import chess.ai.ChessAi
import chess.ai.MoveQuality
import chess.parseAlgebraicMove
import chess.toAlgebraic
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton

/**
 * Explains why a specific move earned a Blunder/Brilliant/Missed-Win grade in [ReviewActivity]:
 * what was actually played, what the engine's own top choice was instead, and — for a blunder —
 * what the opponent's best reply exploits. Lets the viewer flip the board between those concrete
 * lines rather than just reading a verdict.
 */
class MoveExplanationActivity : AppCompatActivity() {

    private lateinit var boardView: ChessBoardView
    private lateinit var titleText: TextView
    private lateinit var bodyText: TextView
    private lateinit var scenarioContainer: LinearLayout

    private var replayMoves: List<String> = emptyList()
    private var playedMove: Move? = null
    private var bestMove: Move? = null
    private var punishMove: Move? = null
    private var quality: MoveQuality? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_move_explanation)

        boardView = findViewById(R.id.explanationBoardView)
        titleText = findViewById(R.id.explanationTitleText)
        bodyText = findViewById(R.id.explanationBodyText)
        scenarioContainer = findViewById(R.id.scenarioButtonContainer)
        findViewById<MaterialButton>(R.id.explanationBackButton).setOnClickListener { finish() }

        val quality = intent.getStringExtra(EXTRA_QUALITY)?.let { runCatching { MoveQuality.valueOf(it) }.getOrNull() }
        if (quality == null) {
            finish()
            return
        }
        this.quality = quality

        replayMoves = intent.getStringArrayListExtra(EXTRA_REPLAY_MOVES) ?: emptyList()
        boardView.flipped = intent.getBooleanExtra(EXTRA_FLIPPED, false)
        boardView.inputEnabled = false

        val boardBefore = replayToBoardBefore()
        playedMove = intent.getStringExtra(EXTRA_PLAYED_MOVE)?.let { parseAlgebraicMove(boardBefore, it) }
        bestMove = intent.getStringExtra(EXTRA_BEST_MOVE)?.let { parseAlgebraicMove(boardBefore, it) }

        val played = playedMove
        val punishNotation = intent.getStringExtra(EXTRA_PUNISH_MOVE)
        punishMove = if (punishNotation != null && played != null) {
            val afterPlayed = boardBefore.copy().apply { applyMove(played) }
            parseAlgebraicMove(afterPlayed, punishNotation)
        } else null

        val playedScore = intent.getIntExtra(EXTRA_PLAYED_SCORE, 0)
        val bestScore = intent.getIntExtra(EXTRA_BEST_SCORE, 0)

        val (labelRes, colorRes) = qualityBadge(quality)
        titleText.text = getString(labelRes)
        (titleText.background as GradientDrawable).setColor(ContextCompat.getColor(this, colorRes))

        setupScenarios(quality)
        bodyText.text = buildExplanation(boardBefore, quality, playedScore, bestScore)
    }

    /** Replays [replayMoves] from the start and returns the resulting board (the position the move was played from). */
    private fun replayToBoardBefore(): Board {
        boardView.newGame()
        val generation = boardView.currentGeneration
        for (notation in replayMoves) {
            val move = parseAlgebraicMove(boardView.game.board, notation) ?: break
            boardView.applyExternalMove(move, generation)
        }
        return boardView.game.board.copy()
    }

    /** Resets the board to [replayMoves] and applies [extra] on top, to show one concrete scenario. */
    private fun showScenario(extra: List<Move>) {
        boardView.newGame()
        val generation = boardView.currentGeneration
        for (notation in replayMoves) {
            val move = parseAlgebraicMove(boardView.game.board, notation) ?: break
            boardView.applyExternalMove(move, generation)
        }
        for (move in extra) {
            boardView.applyExternalMove(move, generation)
        }
    }

    private fun setupScenarios(quality: MoveQuality) {
        val scenarios = mutableListOf<Pair<String, List<Move>>>()
        val played = playedMove
        val best = bestMove
        val punish = punishMove

        if (played != null) {
            scenarios += if (quality == MoveQuality.BLUNDER && punish != null) {
                getString(R.string.explanation_scenario_played_and_punish, played.toAlgebraic()) to listOf(played, punish)
            } else {
                getString(R.string.explanation_scenario_played, played.toAlgebraic()) to listOf(played)
            }
        }
        if (quality != MoveQuality.BRILLIANT && best != null) {
            scenarios += getString(R.string.explanation_scenario_best, best.toAlgebraic()) to listOf(best)
        }

        scenarioContainer.removeAllViews()
        val buttons = mutableListOf<MaterialButton>()
        scenarios.forEachIndexed { index, (label, moves) ->
            val button = MaterialButton(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
            }
            button.setOnClickListener {
                showScenario(moves)
                buttons.forEachIndexed { i, b -> styleScenarioButton(b, i == index) }
            }
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) params.marginStart = dpToPx(8)
            scenarioContainer.addView(button, params)
            buttons += button
        }
        buttons.firstOrNull()?.performClick()
    }

    private fun styleScenarioButton(button: MaterialButton, selected: Boolean) {
        button.setBackgroundColor(ContextCompat.getColor(this, if (selected) R.color.accent_green else R.color.panel_bg))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun qualityBadge(quality: MoveQuality): Pair<Int, Int> = when (quality) {
        MoveQuality.BRILLIANT -> R.string.move_quality_brilliant to R.color.move_quality_brilliant
        MoveQuality.BLUNDER -> R.string.move_quality_blunder to R.color.move_quality_blunder
        MoveQuality.MISSED_WIN -> R.string.move_quality_missed_win to R.color.move_quality_missed_win
        else -> R.string.move_quality_best to R.color.move_quality_best
    }

    private fun pieceName(type: PieceType): String = when (type) {
        PieceType.PAWN -> getString(R.string.piece_name_pawn)
        PieceType.KNIGHT -> getString(R.string.piece_name_knight)
        PieceType.BISHOP -> getString(R.string.piece_name_bishop)
        PieceType.ROOK -> getString(R.string.piece_name_rook)
        PieceType.QUEEN -> getString(R.string.piece_name_queen)
        PieceType.KING -> getString(R.string.piece_name_king)
    }

    private fun describeMove(board: Board, move: Move): String {
        val name = board.pieceAt(move.from)?.let { pieceName(it.type) }.orEmpty()
        return "$name ${move.from}→${move.to}"
    }

    private fun formatScore(score: Int): String {
        if (ChessAi.isForcedMateScore(score)) return getString(R.string.explain_leads_to_mate)
        val pawns = score / 100.0
        val sign = if (pawns >= 0) "+" else ""
        return "$sign${"%.1f".format(pawns)}"
    }

    private fun buildExplanation(boardBefore: Board, quality: MoveQuality, playedScore: Int, bestScore: Int): String {
        val played = playedMove ?: return ""
        val best = bestMove
        return when (quality) {
            MoveQuality.BLUNDER -> buildString {
                append(getString(R.string.explain_blunder_played, describeMove(boardBefore, played), formatScore(playedScore)))
                val punish = punishMove
                if (punish != null) {
                    val afterPlayed = boardBefore.copy().apply { applyMove(played) }
                    append("\n\n")
                    append(getString(R.string.explain_blunder_punish, describeMove(afterPlayed, punish)))
                }
                if (best != null) {
                    append("\n\n")
                    append(getString(R.string.explain_best_alt, describeMove(boardBefore, best), formatScore(bestScore)))
                }
            }
            MoveQuality.BRILLIANT ->
                getString(R.string.explain_brilliant, describeMove(boardBefore, played), formatScore(playedScore))
            MoveQuality.MISSED_WIN -> buildString {
                append(getString(R.string.explain_missed_win_best, describeMove(boardBefore, best ?: played), formatScore(bestScore)))
                append("\n\n")
                append(getString(R.string.explain_missed_win_played, describeMove(boardBefore, played), formatScore(playedScore)))
            }
            else -> ""
        }
    }

    companion object {
        const val EXTRA_REPLAY_MOVES = "chess.ui.EXTRA_REPLAY_MOVES"
        const val EXTRA_FLIPPED = "chess.ui.EXTRA_FLIPPED"
        const val EXTRA_QUALITY = "chess.ui.EXTRA_QUALITY"
        const val EXTRA_PLAYED_MOVE = "chess.ui.EXTRA_PLAYED_MOVE"
        const val EXTRA_PLAYED_SCORE = "chess.ui.EXTRA_PLAYED_SCORE"
        const val EXTRA_BEST_MOVE = "chess.ui.EXTRA_BEST_MOVE"
        const val EXTRA_BEST_SCORE = "chess.ui.EXTRA_BEST_SCORE"
        const val EXTRA_PUNISH_MOVE = "chess.ui.EXTRA_PUNISH_MOVE"
    }
}
