package app.notify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.notify.engine.Notifier
import app.notify.engine.Scheduler
import app.notify.ui.App
import app.notify.ui.NotifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Bumped on every resume so permission banners re-check after the user returns from Settings.
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        lifecycleScope.launch(Dispatchers.Default) { Scheduler.restore(applicationContext, recompute = false) }
        setContent {
            NotifyTheme {
                App(viewModel<AppVM>(), resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
