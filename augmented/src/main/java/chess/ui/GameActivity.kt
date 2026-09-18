package chess.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import chess.AugmentedGameStatus
import chess.Color
import chess.Move
import chess.PieceType
import chess.Square
import com.ckdgusrkgus.augmentedchess.R
import com.google.android.material.button.MaterialButton

/** Local two-player Augmented Chess (base ruleset only — no augment drafting yet). */
class GameActivity : AppCompatActivity(), ChessBoardView.Listener {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        boardView = findViewById(R.id.chessBoardView)
        statusText = findViewById(R.id.statusText)
        findViewById<TextView>(R.id.topLabel).text = getString(R.string.label_black)
        findViewById<TextView>(R.id.bottomLabel).text = getString(R.string.label_white)

        boardView.listener = this
        boardView.refreshStatus()

        findViewById<MaterialButton>(R.id.resignButton).setOnClickListener { confirmResign() }
        findViewById<MaterialButton>(R.id.newGameButton).setOnClickListener { boardView.newGame() }
    }

    override fun onMoveMade(move: Move) {}

    override fun onStatusChanged(status: AugmentedGameStatus, sideToMove: Color, inCheck: Boolean) {
        val resultText = when (status) {
            AugmentedGameStatus.ONGOING -> {
                val turn = if (sideToMove == Color.WHITE) getString(R.string.turn_white) else getString(R.string.turn_black)
                if (inCheck) "$turn - ${getString(R.string.check_banner)}" else turn
            }
            AugmentedGameStatus.WHITE_WINS -> getString(R.string.white_wins)
            AugmentedGameStatus.BLACK_WINS -> getString(R.string.black_wins)
        }
        statusText.text = resultText
        if (status != AugmentedGameStatus.ONGOING) showGameOverDialog(resultText)
    }

    private fun showGameOverDialog(resultText: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(resultText)
            .setCancelable(false)
            .setPositiveButton(R.string.main_menu) { _, _ -> finish() }
            .setNegativeButton(R.string.new_game) { _, _ -> boardView.newGame() }
            .show()
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
}
