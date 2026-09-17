package chess.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import chess.Board
import chess.Move
import chess.MoveGenerator
import chess.PieceType
import chess.ai.AiLevel
import chess.ai.ChessAi
import chess.ai.MoveQuality
import chess.parseAlgebraicMove
import chess.toAlgebraic
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Explains why a specific move earned a Blunder/Brilliant/Missed-Win grade in [ReviewActivity]:
 * what was actually played, what the engine's own top choice was instead, and — for a blunder —
 * what the opponent's best reply exploits.
 *
 * The badge, board, and "played"/"best" scenarios render immediately from data [ReviewActivity]
 * already had. The opponent's punishing reply (for a blunder) and the follow-up line both need a
 * fresh engine search — up to [MISSED_MATE_MAX_PLIES] plies of it for a missed forced mate — so
 * that search runs in the background here and is folded in once ready, instead of [ReviewActivity]
 * paying for it on every single "next" press regardless of whether this screen is ever opened.
 */
class MoveExplanationActivity : AppCompatActivity() {

    private lateinit var boardView: ChessBoardView
    private lateinit var titleText: TextView
    private lateinit var bodyText: TextView
    private lateinit var scenarioContainer: LinearLayout
    private lateinit var stepText: TextView
    private lateinit var stepPrevButton: MaterialButton
    private lateinit var stepNextButton: MaterialButton

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var replayMoves: List<String> = emptyList()
    private var playedMove: Move? = null
    private var bestMove: Move? = null
    private var punishMove: Move? = null
    private var playedScore: Int = 0
    private var bestScore: Int = 0

    /** The move(s) that reach the position [followUpMoves] continues from — see [anchorMovesFor]. Only meaningful once the background search below completes. */
    private var anchorMoves: List<Move> = emptyList()
    /** Best play for both sides past [anchorMoves], found by a background engine search — empty until that search completes. */
    private var followUpMoves: List<Move> = emptyList()
    /** True when [followUpMoves] actually ends in checkmate (a missed forced mate walked out in full), rather than being cut off mid-line. */
    private var followUpIsMate: Boolean = false

    /** The position the currently selected scenario starts from (after replaying [replayMoves]). */
    private lateinit var startBoard: Board
    /** The moves of the currently selected scenario button, steppable one at a time via [stepPrevButton]/[stepNextButton]. */
    private var currentScenarioMoves: List<Move> = emptyList()
    /** True when [currentScenarioMoves] (the currently selected scenario) ends in checkmate. */
    private var currentScenarioIsMate: Boolean = false
    /** How many of [currentScenarioMoves] are currently applied to the board (0 = start position). */
    private var scenarioStepIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_move_explanation)

        boardView = findViewById(R.id.explanationBoardView)
        titleText = findViewById(R.id.explanationTitleText)
        bodyText = findViewById(R.id.explanationBodyText)
        scenarioContainer = findViewById(R.id.scenarioButtonContainer)
        stepText = findViewById(R.id.explanationStepText)
        stepPrevButton = findViewById(R.id.explanationStepPrevButton)
        stepNextButton = findViewById(R.id.explanationStepNextButton)
        findViewById<MaterialButton>(R.id.explanationBackButton).setOnClickListener { finish() }

        stepPrevButton.setOnClickListener {
            if (scenarioStepIndex > 0) {
                scenarioStepIndex--
                renderScenarioStep()
            }
        }
        stepNextButton.setOnClickListener {
            if (scenarioStepIndex < currentScenarioMoves.size) {
                scenarioStepIndex++
                renderScenarioStep()
            }
        }

        val quality = intent.getStringExtra(EXTRA_QUALITY)?.let { runCatching { MoveQuality.valueOf(it) }.getOrNull() }
        if (quality == null) {
            finish()
            return
        }

        replayMoves = intent.getStringArrayListExtra(EXTRA_REPLAY_MOVES) ?: emptyList()
        boardView.flipped = intent.getBooleanExtra(EXTRA_FLIPPED, false)
        boardView.inputEnabled = false

        startBoard = replayToBoardBefore()
        playedMove = intent.getStringExtra(EXTRA_PLAYED_MOVE)?.let { parseAlgebraicMove(startBoard, it) }
        bestMove = intent.getStringExtra(EXTRA_BEST_MOVE)?.let { parseAlgebraicMove(startBoard, it) }
        playedScore = intent.getIntExtra(EXTRA_PLAYED_SCORE, 0)
        bestScore = intent.getIntExtra(EXTRA_BEST_SCORE, 0)

        val (labelRes, colorRes) = qualityBadge(quality)
        titleText.text = getString(labelRes)
        (titleText.background as GradientDrawable).setColor(ContextCompat.getColor(this, colorRes))

        setupScenarios(quality)
        renderExplanationText(quality)
        runDeferredAnalysis(quality)
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Finds the opponent's punishing reply (for a blunder) and the follow-up line off the main
     * thread, then folds the result into the already-visible screen: the body text is rebuilt and,
     * if a follow-up line was found, one more scenario button is added — the existing buttons and
     * whatever the viewer is currently looking at are left alone.
     */
    private fun runDeferredAnalysis(quality: MoveQuality) {
        val played = playedMove
        val best = bestMove
        if (played == null || best == null) return
        analysisExecutor.execute {
            val ai = ChessAi(AiLevel.MASTER)
            val punishingReply = if (quality == MoveQuality.BLUNDER) {
                val afterPlayed = startBoard.copy().apply { applyMove(played) }
                runCatching { ai.evaluateAllMoves(afterPlayed).firstOrNull() }.getOrNull()
            } else null

            // The position the follow-up line should be judged from: right after the sacrifice for
            // Brilliant, after the opponent's punishing reply for Blunder, after the missed best
            // move for Missed Win — i.e. wherever the "so what happens next" question is about.
            val anchor = anchorMovesFor(quality, played, best, punishingReply?.move)
            val anchorBoard = runCatching { startBoard.copy().apply { anchor.forEach { applyMove(it) } } }.getOrNull()

            // A missed forced mate ("놓친 메이트") is walked all the way to checkmate rather than
            // cut off at a fixed depth, so the screen can replay the actual mating sequence.
            val isMissedMate = quality == MoveQuality.MISSED_WIN && ChessAi.isForcedMateScore(bestScore)
            val maxPlies = if (isMissedMate) MISSED_MATE_MAX_PLIES else GENERIC_FOLLOW_UP_MAX_PLIES
            val followUp = anchorBoard?.let { runCatching { ai.findMateLine(it, maxPlies = maxPlies) }.getOrDefault(emptyList()) }.orEmpty()
            val isMate = isMissedMate && followUp.isNotEmpty() && anchorBoard != null && runCatching {
                val after = anchorBoard.copy().apply { followUp.forEach { applyMove(it) } }
                after.isInCheck(after.sideToMove) && MoveGenerator.legalMoves(after, after.sideToMove).isEmpty()
            }.getOrDefault(false)

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                punishMove = punishingReply?.move
                anchorMoves = anchor
                followUpMoves = followUp
                followUpIsMate = isMate
                renderExplanationText(quality)
                if (followUp.isNotEmpty()) addFollowUpScenarioButton()
            }
        }
    }

    /** The move(s) that reach the position a "what happens next" continuation should start from. */
    private fun anchorMovesFor(quality: MoveQuality, played: Move?, best: Move?, punish: Move?): List<Move> = when (quality) {
        MoveQuality.BRILLIANT -> listOfNotNull(played)
        MoveQuality.BLUNDER -> listOfNotNull(played, punish)
        MoveQuality.MISSED_WIN -> listOfNotNull(best)
        else -> emptyList()
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

    /** Resets the board to [replayMoves] and applies the first [scenarioStepIndex] moves of [currentScenarioMoves] on top, so the scenario can be watched move by move via [stepPrevButton]/[stepNextButton]. */
    private fun renderScenarioStep() {
        boardView.newGame()
        val generation = boardView.currentGeneration
        for (notation in replayMoves) {
            val move = parseAlgebraicMove(boardView.game.board, notation) ?: break
            boardView.applyExternalMove(move, generation)
        }
        for (i in 0 until scenarioStepIndex) {
            boardView.applyExternalMove(currentScenarioMoves[i], generation)
        }

        val moveDescription = if (scenarioStepIndex == 0) {
            getString(R.string.review_start_position)
        } else {
            val board = startBoard.copy()
            for (i in 0 until scenarioStepIndex - 1) board.applyMove(currentScenarioMoves[i])
            describeMove(board, currentScenarioMoves[scenarioStepIndex - 1])
        }
        val atCheckmate = currentScenarioIsMate && scenarioStepIndex == currentScenarioMoves.size
        stepText.text = if (atCheckmate) {
            getString(R.string.explanation_step_checkmate, scenarioStepIndex, currentScenarioMoves.size, moveDescription)
        } else {
            getString(R.string.explanation_step_progress, scenarioStepIndex, currentScenarioMoves.size, moveDescription)
        }
        stepPrevButton.isEnabled = scenarioStepIndex > 0
        stepNextButton.isEnabled = scenarioStepIndex < currentScenarioMoves.size
    }

    private data class Scenario(val label: String, val moves: List<Move>, val isMate: Boolean = false)

    /** The scenarios computable immediately from what [ReviewActivity] already passed in — no engine search needed. */
    private fun setupScenarios(quality: MoveQuality) {
        val played = playedMove
        val best = bestMove

        scenarioContainer.removeAllViews()
        if (played != null) {
            addScenarioButton(Scenario(getString(R.string.explanation_scenario_played, played.toAlgebraic()), listOf(played)))
        }
        if (quality != MoveQuality.BRILLIANT && best != null) {
            addScenarioButton(Scenario(getString(R.string.explanation_scenario_best, best.toAlgebraic()), listOf(best)))
        }
        (scenarioContainer.getChildAt(0) as? MaterialButton)?.performClick()
    }

    /** Appends the follow-up scenario once [runDeferredAnalysis] finds one, without disturbing whichever scenario is currently on screen. */
    private fun addFollowUpScenarioButton() {
        val label = if (followUpIsMate) {
            getString(R.string.explanation_scenario_followup_mate)
        } else {
            getString(R.string.explanation_scenario_followup)
        }
        addScenarioButton(Scenario(label, anchorMoves + followUpMoves, followUpIsMate))
    }

    private fun addScenarioButton(scenario: Scenario) {
        val button = MaterialButton(this).apply {
            text = scenario.label
            textSize = 12f
            isAllCaps = false
        }
        button.setOnClickListener {
            currentScenarioMoves = scenario.moves
            currentScenarioIsMate = scenario.isMate
            scenarioStepIndex = 0
            renderScenarioStep()
            for (i in 0 until scenarioContainer.childCount) {
                styleScenarioButton(scenarioContainer.getChildAt(i) as MaterialButton, scenarioContainer.getChildAt(i) === button)
            }
        }
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (scenarioContainer.childCount > 0) params.marginStart = dpToPx(8)
        scenarioContainer.addView(button, params)
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

    /** Rebuilds the whole explanation text from [playedMove]/[bestMove]/[punishMove]/[followUpMoves]; called once immediately and again after [runDeferredAnalysis] fills in the last two. */
    private fun renderExplanationText(quality: MoveQuality) {
        val anchorBoard = if (followUpMoves.isEmpty()) {
            startBoard
        } else {
            runCatching { startBoard.copy().apply { anchorMoves.forEach { applyMove(it) } } }.getOrDefault(startBoard)
        }
        bodyText.text = buildExplanation(startBoard, anchorBoard, quality)
    }

    private fun buildExplanation(boardBefore: Board, anchorBoard: Board, quality: MoveQuality): String {
        val played = playedMove ?: return ""
        val best = bestMove
        val body = when (quality) {
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
        if (followUpMoves.isEmpty()) return body
        return "$body\n\n${getString(R.string.explain_followup, describeSequence(anchorBoard, followUpMoves))}"
    }

    /** Walks [moves] from [start], describing each one against the board state at that point in the sequence. */
    private fun describeSequence(start: Board, moves: List<Move>): String {
        val board = start.copy()
        return moves.joinToString(" → ") { move ->
            val description = describeMove(board, move)
            board.applyMove(move)
            description
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
        private const val GENERIC_FOLLOW_UP_MAX_PLIES = 6
        private const val MISSED_MATE_MAX_PLIES = 20
    }
}
