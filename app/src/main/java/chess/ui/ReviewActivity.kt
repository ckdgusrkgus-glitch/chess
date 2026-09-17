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
import chess.ai.AiLevel
import chess.ai.ChessAi
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
    private lateinit var moveCounterText: TextView
    private lateinit var actualMoveText: TextView
    private lateinit var moveQualityText: TextView
    private lateinit var aiSuggestionText: TextView
    private lateinit var prevButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    private lateinit var record: GameRecord
    private var currentIndex = 0

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

    /** Resets the board and replays moves[0 until currentIndex] to show the position at that point. */
    private fun renderPosition() {
        boardView.newGame()
        boardView.inputEnabled = false
        val board = boardView.game.board
        val generation = boardView.currentGeneration
        for (i in 0 until currentIndex) {
            val move = parseAlgebraicMove(board, record.moves[i]) ?: break
            boardView.applyExternalMove(move, generation)
        }

        val total = record.moves.size
        val displayIndex = if (total == 0) 0 else minOf(currentIndex + 1, total)
        moveCounterText.text = getString(R.string.review_move_counter, displayIndex, total)
        prevButton.isEnabled = currentIndex > 0
        nextButton.isEnabled = currentIndex < total

        actualMoveText.text = if (currentIndex < total) {
            getString(R.string.review_actual_move, record.moves[currentIndex])
        } else {
            getString(R.string.review_game_ended, record.result)
        }

        requestAiSuggestion()
    }

    /**
     * Scores every legal move from the current position once, then uses that same pass both to
     * show the engine's suggested move and to grade the move actually played (chess.com "Game
     * Review" style: Brilliant/Great/Best/.../Blunder/Missed Win) — one engine call per position.
     */
    private fun requestAiSuggestion() {
        val requestId = ++suggestionRequestId
        clearMateSuggestion()
        if (currentIndex >= record.moves.size) {
            aiSuggestionText.text = ""
            moveQualityText.visibility = View.GONE
            return
        }
        aiSuggestionText.text = getString(R.string.ai_thinking)
        moveQualityText.visibility = View.GONE
        val snapshot = boardView.game.board.copy()
        val playedMove = parseAlgebraicMove(snapshot, record.moves[currentIndex])
        val isBookMove = OpeningBook.isBookMove(record.moves.subList(0, currentIndex + 1))
        val replayMoves = record.moves.subList(0, currentIndex)
        analysisExecutor.execute {
            val evaluations = runCatching { ChessAi(AiLevel.MASTER).evaluateAllMoves(snapshot) }.getOrDefault(emptyList())
            val best = evaluations.firstOrNull()
            val quality = when {
                isBookMove -> MoveQuality.BOOK
                playedMove != null -> MoveClassifier.classify(snapshot, playedMove, evaluations)
                else -> null
            }
            mainHandler.post {
                if (requestId != suggestionRequestId || isFinishing || isDestroyed) return@post
                if (best != null && ChessAi.isForcedMateScore(best.score)) {
                    showMateSuggestion(best.move.toAlgebraic(), replayMoves)
                } else {
                    aiSuggestionText.text = if (best != null) {
                        getString(R.string.review_ai_suggestion, best.move.toAlgebraic())
                    } else {
                        getString(R.string.review_ai_suggestion_none)
                    }
                }
                showMoveQuality(quality)
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
            startActivity(intent)
        }
    }

    private fun clearMateSuggestion() {
        mateReplayMoves = null
        aiSuggestionText.paintFlags = aiSuggestionText.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        aiSuggestionText.setOnClickListener(null)
        aiSuggestionText.isClickable = false
    }

    private fun showMoveQuality(quality: MoveQuality?) {
        if (quality == null) {
            moveQualityText.visibility = View.GONE
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
    }

    companion object {
        const val EXTRA_GAME_RECORD = "chess.ui.EXTRA_GAME_RECORD"
    }
}
