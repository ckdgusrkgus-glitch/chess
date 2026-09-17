package chess.ui

import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import chess.Board
import chess.Color
import chess.Move
import chess.ai.AiLevel
import chess.ai.ChessAi
import chess.ai.EXPLAINABLE_MOVE_QUALITIES
import chess.ai.MoveClassifier
import chess.ai.MoveQuality
import chess.ai.OpeningBook
import chess.history.GameRecord
import chess.history.GameRecordCodec
import chess.parseAlgebraicMove
import chess.toAlgebraic
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Steps through a saved game move by move, showing what was actually played next to what the
 * (strongest-level) AI would recommend from that same position.
 */
class ReviewActivity : AppCompatActivity() {

    private lateinit var boardView: ChessBoardView
    private lateinit var evalBarView: EvalBarView
    private lateinit var moveCounterText: TextView
    private lateinit var actualMoveText: TextView
    private lateinit var moveQualityText: TextView
    private lateinit var aiSuggestionText: TextView
    private lateinit var prevButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    private lateinit var record: GameRecord
    private var currentIndex = 0

    /** The position moves[currentIndex - 1] was played from, captured by [renderPosition]; null at the start position. */
    private var boardBeforeLastMove: Board? = null

    /** Set only when the AI suggestion at the current position is a proven forced mate; tapping the suggestion then opens [MateLineActivity] starting from these replayed moves. */
    private var mateReplayMoves: List<String>? = null

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var suggestionRequestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        val decoded = intent.getStringExtra(EXTRA_GAME_RECORD)?.let { GameRecordCodec.decode(it) }
        if (decoded == null) {
            finish()
            return
        }
        record = decoded

        boardView = findViewById(R.id.reviewBoardView)
        // Show the board from the human's own side, matching how the game was actually played
        // (a two-player game has no single human side, so it's left at the default orientation).
        boardView.flipped = record.humanColor == Color.BLACK
        evalBarView = findViewById(R.id.evalBarView)
        evalBarView.flipped = boardView.flipped
        moveCounterText = findViewById(R.id.moveCounterText)
        actualMoveText = findViewById(R.id.actualMoveText)
        moveQualityText = findViewById(R.id.moveQualityText)
        aiSuggestionText = findViewById(R.id.aiSuggestionText)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }

        prevButton.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                renderPosition()
            }
        }
        nextButton.setOnClickListener {
            if (currentIndex < record.moves.size) {
                currentIndex++
                renderPosition()
            }
        }

        renderPosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Resets the board and replays moves[0 until currentIndex] to show the position at that
     * point. Captures the position one ply earlier too ([boardBeforeLastMove]), since grading
     * "the move that was just played" (moves[currentIndex - 1]) needs the position it was played
     * *from*, not the position it produced.
     */
    private fun renderPosition() {
        boardView.newGame()
        boardView.inputEnabled = false
        val board = boardView.game.board
        val generation = boardView.currentGeneration
        boardBeforeLastMove = null
        for (i in 0 until currentIndex) {
            if (i == currentIndex - 1) boardBeforeLastMove = board.copy()
            val move = parseAlgebraicMove(board, record.moves[i]) ?: break
            boardView.applyExternalMove(move, generation)
        }

        val total = record.moves.size
        moveCounterText.text = getString(R.string.review_move_counter, currentIndex, total)
        prevButton.isEnabled = currentIndex > 0
        nextButton.isEnabled = currentIndex < total

        val moveText = if (currentIndex == 0) {
            getString(R.string.review_start_position)
        } else {
            getString(R.string.review_actual_move, record.moves[currentIndex - 1])
        }
        actualMoveText.text = if (currentIndex == total && total > 0) {
            "$moveText\n${getString(R.string.review_game_ended, record.result)}"
        } else {
            moveText
        }

        requestAiSuggestion()
    }

    /**
     * Scores every legal move from the position the *last-played* move (moves[currentIndex - 1])
     * was made from, then uses that same pass both to show what the engine would have played
     * instead and to grade the move that was actually played there (chess.com "Game Review"
     * style: Brilliant/Great/Best/.../Blunder/Missed Win) — one engine call per position. Nothing
     * is shown at the start position (currentIndex == 0): there's no move yet to grade.
     */
    private fun requestAiSuggestion() {
        val requestId = ++suggestionRequestId
        clearMateSuggestion()
        val lastMoveIndex = currentIndex - 1
        val snapshot = boardBeforeLastMove
        if (lastMoveIndex < 0 || snapshot == null) {
            aiSuggestionText.text = ""
            showMoveQuality(null, null, 0, null, emptyList())
            evalBarView.scoreForWhite = 0
            return
        }
        aiSuggestionText.text = getString(R.string.ai_thinking)
        moveQualityText.visibility = View.GONE
        val playedMove = parseAlgebraicMove(snapshot, record.moves[lastMoveIndex])
        val isBookMove = OpeningBook.isBookMove(record.moves.subList(0, lastMoveIndex + 1))
        val replayMoves = record.moves.subList(0, lastMoveIndex)
        val moverColor = snapshot.sideToMove
        analysisExecutor.execute {
            val evaluations = runCatching { ChessAi(AiLevel.MASTER).evaluateAllMoves(snapshot) }.getOrDefault(emptyList())
            val best = evaluations.firstOrNull()
            val quality = when {
                isBookMove -> MoveQuality.BOOK
                playedMove != null -> MoveClassifier.classify(snapshot, playedMove, evaluations)
                else -> null
            }
            // The eval bar reflects the position AFTER the graded move (what's currently on
            // screen), not the pre-move snapshot: evaluations already score each candidate move by
            // the resulting position, from the mover's own point of view, so it just needs flipping
            // to White's point of view when the mover was Black.
            val playedScore = evaluations.find { it.move == playedMove }?.score
            val scoreForWhite = playedScore?.let { if (moverColor == Color.WHITE) it else -it } ?: 0
            mainHandler.post {
                if (requestId != suggestionRequestId || isFinishing || isDestroyed) return@post
                evalBarView.scoreForWhite = scoreForWhite
                if (best != null && ChessAi.isForcedMateScore(best.score)) {
                    showMateSuggestion(best.move.toAlgebraic(), replayMoves)
                } else {
                    aiSuggestionText.text = if (best != null) {
                        getString(R.string.review_ai_suggestion, best.move.toAlgebraic())
                    } else {
                        getString(R.string.review_ai_suggestion_none)
                    }
                }
                showMoveQuality(quality, playedMove, playedScore ?: 0, best, replayMoves)
            }
        }
    }

    private fun showMateSuggestion(bestMoveNotation: String, replayMoves: List<String>) {
        mateReplayMoves = replayMoves
        aiSuggestionText.text = getString(R.string.review_ai_suggestion_mate, bestMoveNotation)
        aiSuggestionText.paintFlags = aiSuggestionText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        aiSuggestionText.setOnClickListener {
            val intent = Intent(this, MateLineActivity::class.java)
            intent.putStringArrayListExtra(MateLineActivity.EXTRA_REPLAY_MOVES, ArrayList(replayMoves))
            intent.putExtra(MateLineActivity.EXTRA_FLIPPED, boardView.flipped)
            startActivity(intent)
        }
    }

    private fun clearMateSuggestion() {
        mateReplayMoves = null
        aiSuggestionText.paintFlags = aiSuggestionText.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        aiSuggestionText.setOnClickListener(null)
        aiSuggestionText.isClickable = false
    }

    /**
     * Shows the grade badge. Blunder/Brilliant/Missed Win (see [EXPLAINABLE_MOVE_QUALITIES]) are
     * clickable through to [MoveExplanationActivity] — but only the played/best moves and scores
     * are passed along; the opponent's punishing reply and the follow-up line both need another
     * engine search, which that screen runs for itself once opened, instead of every single
     * "next" press in Review paying for a search it might never look at.
     */
    private fun showMoveQuality(
        quality: MoveQuality?,
        playedMove: Move?,
        playedScore: Int,
        best: ChessAi.MoveEvaluation?,
        replayMoves: List<String>
    ) {
        if (quality == null) {
            moveQualityText.visibility = View.GONE
            moveQualityText.setOnClickListener(null)
            moveQualityText.isClickable = false
            return
        }
        val (labelRes, colorRes) = when (quality) {
            MoveQuality.BRILLIANT -> R.string.move_quality_brilliant to R.color.move_quality_brilliant
            MoveQuality.GREAT -> R.string.move_quality_great to R.color.move_quality_great
            MoveQuality.BEST -> R.string.move_quality_best to R.color.move_quality_best
            MoveQuality.EXCELLENT -> R.string.move_quality_excellent to R.color.move_quality_excellent
            MoveQuality.GOOD -> R.string.move_quality_good to R.color.move_quality_good
            MoveQuality.BOOK -> R.string.move_quality_book to R.color.move_quality_book
            MoveQuality.INACCURACY -> R.string.move_quality_inaccuracy to R.color.move_quality_inaccuracy
            MoveQuality.MISTAKE -> R.string.move_quality_mistake to R.color.move_quality_mistake
            MoveQuality.BLUNDER -> R.string.move_quality_blunder to R.color.move_quality_blunder
            MoveQuality.MISSED_WIN -> R.string.move_quality_missed_win to R.color.move_quality_missed_win
        }
        moveQualityText.text = getString(labelRes)
        (moveQualityText.background as GradientDrawable).setColor(ContextCompat.getColor(this, colorRes))
        moveQualityText.visibility = View.VISIBLE
        val explainable = quality in EXPLAINABLE_MOVE_QUALITIES && playedMove != null && best != null
        if (explainable) {
            moveQualityText.paintFlags = moveQualityText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            moveQualityText.setOnClickListener {
                openExplanation(quality, playedMove!!, playedScore, best!!.move, best.score, replayMoves)
            }
        } else {
            moveQualityText.paintFlags = moveQualityText.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            moveQualityText.setOnClickListener(null)
            moveQualityText.isClickable = false
        }
    }

    private fun openExplanation(
        quality: MoveQuality,
        playedMove: Move,
        playedScore: Int,
        bestMove: Move,
        bestScore: Int,
        replayMoves: List<String>
    ) {
        val intent = Intent(this, MoveExplanationActivity::class.java)
        intent.putStringArrayListExtra(MoveExplanationActivity.EXTRA_REPLAY_MOVES, ArrayList(replayMoves))
        intent.putExtra(MoveExplanationActivity.EXTRA_FLIPPED, boardView.flipped)
        intent.putExtra(MoveExplanationActivity.EXTRA_QUALITY, quality.name)
        intent.putExtra(MoveExplanationActivity.EXTRA_PLAYED_MOVE, playedMove.toAlgebraic())
        intent.putExtra(MoveExplanationActivity.EXTRA_PLAYED_SCORE, playedScore)
        intent.putExtra(MoveExplanationActivity.EXTRA_BEST_MOVE, bestMove.toAlgebraic())
        intent.putExtra(MoveExplanationActivity.EXTRA_BEST_SCORE, bestScore)
        startActivity(intent)
    }

    companion object {
        const val EXTRA_GAME_RECORD = "chess.ui.EXTRA_GAME_RECORD"
    }
}
