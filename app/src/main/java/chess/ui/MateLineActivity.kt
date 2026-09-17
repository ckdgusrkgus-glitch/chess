package chess.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import chess.Move
import chess.ai.AiLevel
import chess.ai.ChessAi
import chess.parseAlgebraicMove
import chess.toAlgebraic
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Shows the forced mate the engine found from a position reached in [ReviewActivity]: replays the
 * moves that lead to that position, then searches out and auto-plays the engine's own line
 * (its top choice for both sides) all the way to checkmate.
 */
class MateLineActivity : AppCompatActivity() {

    private lateinit var boardView: ChessBoardView
    private lateinit var counterText: TextView
    private lateinit var moveText: TextView
    private lateinit var prevButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var playPauseButton: MaterialButton

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var replayMoves: List<String> = emptyList()
    private var mateLine: List<Move> = emptyList()
    private var stepIndex = 0
    private var isPlaying = false

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            if (stepIndex < mateLine.size) {
                stepIndex++
                renderStep()
                mainHandler.postDelayed(this, PLAYBACK_DELAY_MS)
            } else {
                isPlaying = false
                updatePlayPauseButton()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mate_line)

        replayMoves = intent.getStringArrayListExtra(EXTRA_REPLAY_MOVES) ?: emptyList()

        boardView = findViewById(R.id.mateBoardView)
        counterText = findViewById(R.id.mateCounterText)
        moveText = findViewById(R.id.mateMoveText)
        prevButton = findViewById(R.id.matePrevButton)
        nextButton = findViewById(R.id.mateNextButton)
        playPauseButton = findViewById(R.id.matePlayPauseButton)
        findViewById<MaterialButton>(R.id.mateBackButton).setOnClickListener { finish() }

        boardView.inputEnabled = false
        prevButton.isEnabled = false
        nextButton.isEnabled = false
        playPauseButton.isEnabled = false
        counterText.text = getString(R.string.mate_line_finding)

        prevButton.setOnClickListener {
            pausePlayback()
            if (stepIndex > 0) {
                stepIndex--
                renderStep()
            }
        }
        nextButton.setOnClickListener {
            pausePlayback()
            if (stepIndex < mateLine.size) {
                stepIndex++
                renderStep()
            }
        }
        playPauseButton.setOnClickListener {
            isPlaying = !isPlaying
            updatePlayPauseButton()
            if (isPlaying) {
                if (stepIndex >= mateLine.size) {
                    stepIndex = 0
                    renderStep()
                }
                mainHandler.postDelayed(playbackRunnable, PLAYBACK_DELAY_MS)
            } else {
                mainHandler.removeCallbacks(playbackRunnable)
            }
        }

        findMateLine()
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun findMateLine() {
        boardView.newGame()
        val generation = boardView.currentGeneration
        for (notation in replayMoves) {
            val move = parseAlgebraicMove(boardView.game.board, notation) ?: break
            boardView.applyExternalMove(move, generation)
        }
        val snapshot = boardView.game.board.copy()

        analysisExecutor.execute {
            val line = runCatching { ChessAi(AiLevel.MASTER).findMateLine(snapshot) }.getOrDefault(emptyList())
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                mateLine = line
                if (line.isEmpty()) {
                    counterText.text = getString(R.string.mate_line_not_found)
                    return@post
                }
                stepIndex = 0
                prevButton.isEnabled = true
                nextButton.isEnabled = true
                playPauseButton.isEnabled = true
                renderStep()
                isPlaying = true
                updatePlayPauseButton()
                mainHandler.postDelayed(playbackRunnable, PLAYBACK_DELAY_MS)
            }
        }
    }

    /** Resets the board and replays replayMoves + mateLine[0 until stepIndex] to show that point in the line. */
    private fun renderStep() {
        boardView.newGame()
        val generation = boardView.currentGeneration
        for (notation in replayMoves) {
            val move = parseAlgebraicMove(boardView.game.board, notation) ?: break
            boardView.applyExternalMove(move, generation)
        }
        for (i in 0 until stepIndex) {
            boardView.applyExternalMove(mateLine[i], generation)
        }

        counterText.text = getString(R.string.mate_line_counter, mateLine.size, stepIndex)
        moveText.text = when {
            stepIndex == 0 -> getString(R.string.mate_line_start_position)
            stepIndex == mateLine.size ->
                getString(R.string.mate_line_move, mateLine[stepIndex - 1].toAlgebraic()) +
                    " – " + getString(R.string.mate_line_checkmate)
            else -> getString(R.string.mate_line_move, mateLine[stepIndex - 1].toAlgebraic())
        }
        prevButton.isEnabled = stepIndex > 0
        nextButton.isEnabled = stepIndex < mateLine.size
    }

    private fun pausePlayback() {
        isPlaying = false
        mainHandler.removeCallbacks(playbackRunnable)
        updatePlayPauseButton()
    }

    private fun updatePlayPauseButton() {
        playPauseButton.text = getString(if (isPlaying) R.string.mate_line_pause else R.string.mate_line_play)
    }

    companion object {
        const val EXTRA_REPLAY_MOVES = "chess.ui.EXTRA_REPLAY_MOVES"
        private const val PLAYBACK_DELAY_MS = 900L
    }
}
