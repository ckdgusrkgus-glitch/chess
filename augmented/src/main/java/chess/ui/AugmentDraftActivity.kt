package chess.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import chess.Color
import chess.augment.OpeningAugment
import chess.augment.OpeningAugments
import com.ckdgusrkgus.augmentedchess.R
import com.google.android.material.button.MaterialButton

/**
 * Lets White, then Black, each pick one "오프닝 증강" (opening augment) before the game starts —
 * matching the source game's rule that this category is drafted once, automatically applied at
 * the start of the game, independently per side.
 */
class AugmentDraftActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var cardsContainer: LinearLayout

    private var currentColor = Color.WHITE
    private var whitePick: OpeningAugment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_augment_draft)

        titleText = findViewById(R.id.draftTitleText)
        cardsContainer = findViewById(R.id.draftCardsContainer)
        findViewById<MaterialButton>(R.id.draftBackButton).setOnClickListener { finish() }

        showCandidatesFor(Color.WHITE)
    }

    private fun showCandidatesFor(color: Color) {
        currentColor = color
        titleText.text = getString(if (color == Color.WHITE) R.string.draft_pick_white else R.string.draft_pick_black)
        cardsContainer.removeAllViews()
        OpeningAugments.ALL.forEach { augment ->
            val button = MaterialButton(this).apply {
                text = getString(R.string.draft_card_format, augment.displayName, augment.description)
                isAllCaps = false
                isSingleLine = false
            }
            button.setOnClickListener { onPicked(augment) }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = dpToPx(10)
            cardsContainer.addView(button, params)
        }
    }

    private fun onPicked(augment: OpeningAugment) {
        if (currentColor == Color.WHITE) {
            whitePick = augment
            showCandidatesFor(Color.BLACK)
        } else {
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra(GameActivity.EXTRA_WHITE_AUGMENT, whitePick?.id)
            intent.putExtra(GameActivity.EXTRA_BLACK_AUGMENT, augment.id)
            startActivity(intent)
            finish()
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
