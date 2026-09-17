package chess.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import chess.Board
import chess.Color
import chess.GameStatus
import chess.Move
import chess.MoveGenerator
import chess.PieceType
import chess.Square
import chess.ai.AiLevel
import chess.ai.ChessAi
import chess.history.GameHistoryStore
import chess.history.GameRecord
import chess.toAlgebraic
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

class GameActivity : AppCompatActivity(), ChessBoardView.Listener {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView
    private lateinit var topLabel: TextView
    private lateinit var bottomLabel: TextView

    /** Which color the AI controls this game; the other color is the human's. Randomized per game. */
    private var aiColor: Color = Color.BLACK
    private var currentAiLevel: AiLevel? = null

    private var aiExecutor: ExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Identifies a single [requestAiMove] call, distinct from [ChessBoardView.currentGeneration]
     * (which only changes on [ChessBoardView.newGame] and so is identical across every AI turn in
     * one game). Without a per-request id, a watchdog whose real result already arrived in time
     * has no way to tell it's stale once it *does* fire late — it would find the shared game
     * generation still matching whatever the current request happens to be and apply its own
     * long-outdated fallback move onto a board that has since moved on several turns.
     */
    private var aiRequestSeq = 0
    private var pendingAiRequestId: Int? = null
    private var pendingAiWatchdog: Runnable? = null

    private val moveHistory = mutableListOf<String>()
    private val historyStore by lazy { GameHistoryStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashSafetyNet()
        setContentView(R.layout.activity_game)

        boardView = findViewById(R.id.chessBoardView)
        statusText = findViewById(R.id.statusText)
        topLabel = findViewById(R.id.topLabel)
        bottomLabel = findViewById(R.id.bottomLabel)

        val levelName = intent.getStringExtra(EXTRA_AI_LEVEL)
        val level = levelName?.let { runCatching { AiLevel.valueOf(it) }.getOrNull() }
        currentAiLevel = level
        if (level != null) {
            randomizeAiSide()
            aiExecutor = Executors.newSingleThreadExecutor()
        }
        updateSideLabels()

        boardView.listener = this
        boardView.refreshStatus()

        findViewById<MaterialButton>(R.id.resignButton).setOnClickListener { confirmResign() }
        findViewById<MaterialButton>(R.id.newGameButton).setOnClickListener { startNewGame() }
    }

    /**
     * If anything crashes mid-game (an uncaught exception on any thread, e.g. a rare edge case in
     * move generation deep into the middlegame), the record was otherwise lost entirely — it only
     * ever got saved from [onStatusChanged]/[confirmResign], both of which require the app to
     * still be running. Saving best-effort here first means a crash still leaves the game in the
     * 복기 (review) list, even though the underlying bug that caused it still needs fixing.
     */
    private fun installCrashSafetyNet() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception, attempting to save game before crashing", throwable)
            runCatching {
                if (moveHistory.isNotEmpty()) saveGameRecord(getString(R.string.game_interrupted_result))
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun startNewGame() {
        moveHistory.clear()
        currentAiLevel?.let { randomizeAiSide() }
        updateSideLabels()
        boardView.newGame()
    }

    /** Randomly gives the AI White or Black this game, and flips the board so the human sits at the bottom. */
    private fun randomizeAiSide() {
        val humanPlaysWhite = Random.nextBoolean()
        aiColor = if (humanPlaysWhite) Color.BLACK else Color.WHITE
        boardView.flipped = !humanPlaysWhite
    }

    private fun updateSideLabels() {
        val topColor = if (boardView.flipped) Color.WHITE else Color.BLACK
        val bottomColor = if (boardView.flipped) Color.BLACK else Color.WHITE
        topLabel.text = sideLabel(topColor)
        bottomLabel.text = sideLabel(bottomColor)
    }

    private fun sideLabel(color: Color): String {
        val base = if (color == Color.WHITE) getString(R.string.label_white) else getString(R.string.label_black)
        val level = currentAiLevel
        return if (level != null && color == aiColor) {
            getString(R.string.label_ai_format, base, level.label, level.rating)
        } else {
            base
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        aiExecutor?.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onMoveMade(move: Move) {
        moveHistory.add(move.toAlgebraic())
    }

    override fun onStatusChanged(status: GameStatus, sideToMove: Color, inCheck: Boolean) {
        val resultText = when (status) {
            GameStatus.ONGOING -> {
                val turn = if (sideToMove == Color.WHITE) getString(R.string.turn_white) else getString(R.string.turn_black)
                if (inCheck) "$turn - ${getString(R.string.check_banner)}" else turn
            }
            // sideToMove has no legal moves and is in check: the OTHER side just delivered mate.
            GameStatus.CHECKMATE -> if (sideToMove == Color.WHITE) getString(R.string.checkmate_black) else getString(R.string.checkmate_white)
            GameStatus.STALEMATE -> getString(R.string.stalemate)
            GameStatus.DRAW_FIFTY_MOVE -> getString(R.string.draw_fifty_move)
            GameStatus.DRAW_INSUFFICIENT_MATERIAL -> getString(R.string.draw_insufficient_material)
        }
        statusText.text = resultText

        if (status != GameStatus.ONGOING) {
            saveGameRecord(resultText)
            showGameOverDialog(resultText)
        } else if (sideToMove == aiColor) {
            requestAiMove()
        }
    }

    private fun showGameOverDialog(resultText: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(resultText)
            .setCancelable(false)
            .setPositiveButton(R.string.main_menu) { _, _ -> finish() }
            .setNegativeButton(R.string.new_game) { _, _ -> startNewGame() }
            .show()
    }

    private fun saveGameRecord(resultText: String) {
        if (moveHistory.isEmpty()) return
        val record = GameRecord(
            timestampMillis = System.currentTimeMillis(),
            humanColor = if (currentAiLevel != null) aiColor.opposite() else null,
            opponentLabel = currentAiLevel?.let { getString(R.string.ai_level_option, it.label, it.rating) }
                ?: getString(R.string.two_player),
            result = resultText,
            moves = moveHistory.toList()
        )
        historyStore.addGame(record)
    }

    private fun requestAiMove() {
        val level = currentAiLevel ?: return
        val executor = aiExecutor ?: return

        boardView.inputEnabled = false
        statusText.text = getString(R.string.ai_thinking)

        val snapshot = boardView.game.board.copy()
        val boardGeneration = boardView.currentGeneration
        val requestId = ++aiRequestSeq
        pendingAiRequestId = requestId
        val startedAt = System.currentTimeMillis()
        Log.i(TAG, "AI move requested (requestId=$requestId, generation=$boardGeneration)")

        // Belt-and-suspenders: if the search hangs or misbehaves for any reason on a given device,
        // this guarantees the game recovers on its own instead of getting stuck forever. Replacing
        // the executor (rather than reusing it) matters just as much as the fallback move itself:
        // a search that's still stuck when this fires would otherwise permanently occupy the single
        // background thread, silently timing out every AI turn for the rest of the game.
        val watchdog = Runnable {
            Log.w(TAG, "AI move watchdog fired after ${System.currentTimeMillis() - startedAt}ms (requestId=$requestId)")
            aiExecutor?.shutdownNow()
            aiExecutor = Executors.newSingleThreadExecutor()
            resolveAiMove(requestId, boardGeneration, randomFallbackMove(snapshot))
        }
        pendingAiWatchdog = watchdog
        mainHandler.postDelayed(watchdog, AI_WATCHDOG_TIMEOUT_MS)

        executor.execute {
            val computed = try {
                ChessAi(level).chooseMove(snapshot)
            } catch (t: Throwable) {
                Log.e(TAG, "AI move computation failed", t)
                null
            }
            val move = computed ?: randomFallbackMove(snapshot)
            val elapsed = System.currentTimeMillis() - startedAt
            Log.i(TAG, "AI move computed in ${elapsed}ms (requestId=$requestId): $move")
            mainHandler.post { resolveAiMove(requestId, boardGeneration, move) }
        }
    }

    private fun randomFallbackMove(board: Board): Move? =
        MoveGenerator.legalMoves(board, board.sideToMove).randomOrNull()

    /**
     * Applies the AI's move, but only for whichever caller (the real result or the watchdog) gets
     * here first for THIS specific [requestId] — not just this game (see [pendingAiRequestId]).
     */
    private fun resolveAiMove(requestId: Int, boardGeneration: Int, move: Move?) {
        if (pendingAiRequestId != requestId) {
            Log.i(TAG, "Ignoring superseded AI result for requestId=$requestId")
            return
        }
        pendingAiRequestId = null
        // The real result winning the race still leaves the watchdog timer scheduled; cancel it so
        // it can't fire later with a now long-outdated fallback move.
        pendingAiWatchdog?.let { mainHandler.removeCallbacks(it) }
        pendingAiWatchdog = null
        if (isFinishing || isDestroyed) return
        boardView.inputEnabled = true
        if (move != null) boardView.applyExternalMove(move, boardGeneration)
    }

    override fun onPromotionNeeded(from: Square, to: Square, onChosen: (PieceType) -> Unit) {
        val options = arrayOf(
            getString(R.string.promotion_queen),
            getString(R.string.promotion_rook),
            getString(R.string.promotion_bishop),
            getString(R.string.promotion_knight)
        )
        val pieceTypes = arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        AlertDialog.Builder(this)
            .setTitle(R.string.promotion_title)
            .setCancelable(false)
            .setItems(options) { _, which -> onChosen(pieceTypes[which]) }
            .show()
    }

    private fun confirmResign() {
        AlertDialog.Builder(this)
            .setTitle(R.string.resign_confirm_title)
            .setMessage(R.string.resign_confirm_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                saveGameRecord(getString(R.string.resigned_result))
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    companion object {
        const val EXTRA_AI_LEVEL = "chess.ui.EXTRA_AI_LEVEL"
        private const val TAG = "GameActivity"
        private const val AI_WATCHDOG_TIMEOUT_MS = 5_000L
    }
}
