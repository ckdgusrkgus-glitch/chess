package chess.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ckdgusrkgus.augmentedchess.R
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.startGameButton).setOnClickListener {
            startActivity(Intent(this, AugmentDraftActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.exitButton).setOnClickListener {
            finishAffinity()
        }
    }
}
