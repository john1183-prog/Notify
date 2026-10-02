package app.notify.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.notify.data.Db
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a rhythm's alarm goes off: deliver one word, then schedule the next. */
class FireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                deliver(context.applicationContext, id)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun deliver(ctx: Context, id: Long) {
        val dao = Db.get(ctx).dao()
        val r = dao.rhythm(id) ?: return
        if (r.enabled) {
            val folderIds = dao.folderIdsFor(id)
            val pool = if (folderIds.isEmpty()) dao.allMessages() else dao.messagesIn(folderIds)
            val now = System.currentTimeMillis()
            val choice = pick(pool.map { it.id }, r.shuffle, r.cursor, parseIds(r.bag), Random.Default)
            if (choice != null) {
                val word = pool.first { it.id == choice.id }
                Notifier.post(ctx, r, word)
                dao.markShown(word.id, now)
                dao.saveProgress(id, choice.cursor, choice.bag.joinToString(","), now)
            } else {
                dao.saveProgress(id, r.cursor, r.bag, now)
            }
        }
        dao.rhythm(id)?.let { Scheduler.arm(ctx, it) }
    }

    companion object {
        const val ACTION = "app.notify.FIRE"
        const val EXTRA_ID = "rhythm_id"
    }
}

/** Alarms do not survive a reboot; this puts them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recompute = intent.action == Intent.ACTION_TIMEZONE_CHANGED
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Scheduler.restore(context.applicationContext, recompute)
            } finally {
                pending.finish()
            }
        }
    }
}
