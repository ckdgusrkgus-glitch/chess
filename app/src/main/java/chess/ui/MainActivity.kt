package chess.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import chess.ai.AiLevel
import com.ckdgusrkgus.chess.R
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.twoPlayerButton).setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.vsAiButton).setOnClickListener {
            showAiLevelPicker()
        }
        findViewById<MaterialButton>(R.id.exitButton).setOnClickListener {
            finishAffinity()
        }
    }

    private fun showAiLevelPicker() {
        val levels = AiLevel.values()
        val labels = levels.map { getString(R.string.ai_level_option, it.label, it.rating) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_ai_level)
            .setItems(labels) { _, which ->
                val intent = Intent(this, GameActivity::class.java)
                intent.putExtra(GameActivity.EXTRA_AI_LEVEL, levels[which].name)
                startActivity(intent)
            }
            .show()
    }
}
