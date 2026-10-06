package app.notify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.notify.engine.HealWorker
import app.notify.engine.Notifier
import app.notify.engine.Scheduler
import app.notify.ui.App
import app.notify.ui.NotifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    // Bumped on every resume so permission banners re-check after the user returns from Settings.
    private var resumeTick by mutableIntStateOf(0)

    // Bumped when a notification is tapped, so the app jumps to Today and shows that word.
    private var wordTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        HealWorker.schedule(applicationContext)
        lifecycleScope.launch(Dispatchers.Default) { Scheduler.restore(applicationContext, recompute = false) }
        if (savedInstanceState == null) openWordFrom(intent)
        setContent {
            NotifyTheme {
                App(vm, resumeTick, wordTick)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openWordFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun openWordFrom(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_WORD, -1L) ?: -1L
        if (id > 0) {
            vm.showWord(id)
            wordTick++
        }
    }

    companion object {
        const val EXTRA_WORD = "word_id"
    }
}
