package chess.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import chess.ai.AiLevel
import chess.ai.ChessAi
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
    private lateinit var aiSuggestionText: TextView
    private lateinit var prevButton: MaterialButton
    private lateinit var nextButton: MaterialButton

    private lateinit var record: GameRecord
    private var currentIndex = 0

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

    private fun requestAiSuggestion() {
        val requestId = ++suggestionRequestId
        if (currentIndex >= record.moves.size) {
            aiSuggestionText.text = ""
            return
        }
        aiSuggestionText.text = getString(R.string.ai_thinking)
        val snapshot = boardView.game.board.copy()
        analysisExecutor.execute {
            val suggestion = runCatching { ChessAi(AiLevel.MASTER).chooseMove(snapshot) }.getOrNull()
            mainHandler.post {
                if (requestId != suggestionRequestId || isFinishing || isDestroyed) return@post
                aiSuggestionText.text = if (suggestion != null) {
                    getString(R.string.review_ai_suggestion, suggestion.toAlgebraic())
                } else {
                    getString(R.string.review_ai_suggestion_none)
                }
            }
        }
    }

    companion object {
        const val EXTRA_GAME_RECORD = "chess.ui.EXTRA_GAME_RECORD"
    }
}
