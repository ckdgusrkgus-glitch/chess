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
import chess.augment.OpeningAugments
import com.ckdgusrkgus.augmentedchess.R
import com.google.android.material.button.MaterialButton

/** Local two-player Augmented Chess, with each side's drafted opening augment (if any) applied. */
class GameActivity : AppCompatActivity(), ChessBoardView.Listener {

    private lateinit var boardView: ChessBoardView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        boardView = findViewById(R.id.chessBoardView)
        statusText = findViewById(R.id.statusText)

        val whiteAugment = OpeningAugments.byId(intent.getStringExtra(EXTRA_WHITE_AUGMENT))
        val blackAugment = OpeningAugments.byId(intent.getStringExtra(EXTRA_BLACK_AUGMENT))
        val recycleRuleEnabled = intent.getBooleanExtra(EXTRA_RECYCLE_RULE, false)
        findViewById<TextView>(R.id.topLabel).text = sideLabel(R.string.label_black, blackAugment?.displayName)
        findViewById<TextView>(R.id.bottomLabel).text = sideLabel(R.string.label_white, whiteAugment?.displayName)

        boardView.listener = this
        boardView.configureAugments(whiteAugment, blackAugment, recycleRuleEnabled)

        findViewById<MaterialButton>(R.id.resignButton).setOnClickListener { confirmResign() }
        findViewById<MaterialButton>(R.id.newGameButton).setOnClickListener { boardView.newGame() }
    }

    private fun sideLabel(baseRes: Int, augmentName: String?): String {
        val base = getString(baseRes)
        return if (augmentName != null) getString(R.string.label_with_augment, base, augmentName) else base
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

    override fun onPromotionNeeded(from: Square, to: Square, choices: List<PieceType>, onChosen: (PieceType) -> Unit) {
        val options = choices.map { promotionLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.promotion_title)
            .setCancelable(false)
            .setItems(options) { _, which -> onChosen(choices[which]) }
            .show()
    }

    private fun promotionLabel(type: PieceType): String = when (type) {
        PieceType.QUEEN -> getString(R.string.promotion_queen)
        PieceType.ROOK -> getString(R.string.promotion_rook)
        PieceType.BISHOP -> getString(R.string.promotion_bishop)
        PieceType.KNIGHT -> getString(R.string.promotion_knight)
        PieceType.KING, PieceType.PAWN -> type.name
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
        const val EXTRA_WHITE_AUGMENT = "chess.ui.EXTRA_WHITE_AUGMENT"
        const val EXTRA_BLACK_AUGMENT = "chess.ui.EXTRA_BLACK_AUGMENT"
        const val EXTRA_RECYCLE_RULE = "chess.ui.EXTRA_RECYCLE_RULE"
    }
}
