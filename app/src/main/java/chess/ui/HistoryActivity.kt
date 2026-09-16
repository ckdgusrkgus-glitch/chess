package chess.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import chess.Color
import chess.history.GameHistoryStore
import chess.history.GameRecordCodec
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lists the most recent saved games; tapping one opens [ReviewActivity]. */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.historyContainer)
        val emptyText = findViewById<TextView>(R.id.emptyText)
        val games = GameHistoryStore(this).loadAll()
        emptyText.visibility = if (games.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        for (record in games) {
            val row = inflater.inflate(R.layout.item_game_record, container, false)
            row.findViewById<TextView>(R.id.rowOpponent).text = record.opponentLabel
            val sideText = when (record.humanColor) {
                Color.WHITE -> getString(R.string.history_you_played_white)
                Color.BLACK -> getString(R.string.history_you_played_black)
                null -> getString(R.string.two_player)
            }
            row.findViewById<TextView>(R.id.rowMeta).text = getString(
                R.string.history_row_format,
                dateFormat.format(Date(record.timestampMillis)),
                sideText,
                record.result
            )
            row.setOnClickListener {
                val intent = Intent(this, ReviewActivity::class.java)
                intent.putExtra(ReviewActivity.EXTRA_GAME_RECORD, GameRecordCodec.encode(record))
                startActivity(intent)
            }
            container.addView(row)
        }
    }
}
