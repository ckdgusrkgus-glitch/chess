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
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GameActivity : AppCompatActivity(), ChessBoardView.Listener {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView
    private lateinit var blackLabel: TextView

    /** Fixed for simplicity: the human always plays White, the AI (if any) always plays Black. */
    private val aiColor = Color.BLACK
    private var chessAi: ChessAi? = null

    private var aiExecutor: ExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Generation of the AI request still awaiting resolution, or null if none is outstanding. */
    private var pendingAiGeneration: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        boardView = findViewById(R.id.chessBoardView)
        statusText = findViewById(R.id.statusText)
        blackLabel = findViewById(R.id.blackLabel)

        val levelName = intent.getStringExtra(EXTRA_AI_LEVEL)
        val level = levelName?.let { runCatching { AiLevel.valueOf(it) }.getOrNull() }
        if (level != null) {
            chessAi = ChessAi(level)
            aiExecutor = Executors.newSingleThreadExecutor()
            blackLabel.text = getString(R.string.label_black_ai, level.label, level.rating)
        }

        boardView.listener = this
        boardView.refreshStatus()

        findViewById<MaterialButton>(R.id.resignButton).setOnClickListener { confirmResign() }
        findViewById<MaterialButton>(R.id.newGameButton).setOnClickListener { boardView.newGame() }
    }

    override fun onDestroy() {
        super.onDestroy()
        aiExecutor?.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onStatusChanged(status: GameStatus, sideToMove: Color, inCheck: Boolean) {
        statusText.text = when (status) {
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

        if (status == GameStatus.ONGOING && sideToMove == aiColor) {
            requestAiMove()
        }
    }

    private fun requestAiMove() {
        val ai = chessAi ?: return
        val executor = aiExecutor ?: return

        boardView.inputEnabled = false
        statusText.text = getString(R.string.ai_thinking)

        val snapshot = boardView.game.board.copy()
        val generation = boardView.currentGeneration
        pendingAiGeneration = generation

        // Belt-and-suspenders: if the search hangs or misbehaves for any reason on a given device,
        // this guarantees the game recovers on its own instead of getting stuck forever.
        mainHandler.postDelayed({ resolveAiMove(generation, randomFallbackMove(snapshot)) }, AI_WATCHDOG_TIMEOUT_MS)

        executor.execute {
            val computed = try {
                ai.chooseMove(snapshot)
            } catch (t: Throwable) {
                Log.e(TAG, "AI move computation failed", t)
                null
            }
            val move = computed ?: randomFallbackMove(snapshot)
            mainHandler.post { resolveAiMove(generation, move) }
        }
    }

    private fun randomFallbackMove(board: Board): Move? =
        MoveGenerator.legalMoves(board, board.sideToMove).randomOrNull()

    /** Applies the AI's move, but only for whichever caller (the real result or the watchdog) gets here first. */
    private fun resolveAiMove(generation: Int, move: Move?) {
        if (pendingAiGeneration != generation) return
        pendingAiGeneration = null
        if (isFinishing || isDestroyed) return
        boardView.inputEnabled = true
        if (move != null) boardView.applyExternalMove(move, generation)
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
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    companion object {
        const val EXTRA_AI_LEVEL = "chess.ui.EXTRA_AI_LEVEL"
        private const val TAG = "GameActivity"
        private const val AI_WATCHDOG_TIMEOUT_MS = 8_000L
    }
}
