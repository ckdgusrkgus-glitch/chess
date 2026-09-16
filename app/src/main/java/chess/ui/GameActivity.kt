package chess.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import chess.Color
import chess.GameStatus
import chess.PieceType
import chess.Square
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton

class GameActivity : AppCompatActivity(), ChessBoardView.Listener {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        boardView = findViewById(R.id.chessBoardView)
        statusText = findViewById(R.id.statusText)

        boardView.listener = this
        boardView.refreshStatus()

        findViewById<MaterialButton>(R.id.resignButton).setOnClickListener { confirmResign() }
        findViewById<MaterialButton>(R.id.newGameButton).setOnClickListener { boardView.newGame() }
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
