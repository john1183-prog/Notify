package app.notify.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
        try {
            val r = dao.rhythm(id) ?: return
            val now = System.currentTimeMillis()
            var delivered = false
            if (r.enabled && Notifier.canPost(ctx)) {
                val folderIds = dao.folderIdsFor(id)
                val pool = if (folderIds.isEmpty()) dao.poolIds() else dao.poolIdsIn(folderIds)
                val choice = pick(pool, r.shuffle, r.cursor, parseIds(r.bag), Random.Default)
                val word = choice?.let { dao.message(it.id) }
                if (choice != null && word != null && Notifier.post(ctx, r, word)) {
                    // Only a notification that was really posted counts as a delivery.
                    dao.markShown(word.id, now)
                    dao.saveProgress(id, choice.cursor, choice.bag.joinToString(","), now)
                    delivered = true
                }
            }
            // Blocked, empty or failed: still move the schedule on so it does not retry within the same slice.
            if (!delivered) dao.saveFired(id, now)
        } catch (e: Exception) {
            Log.e("Notify", "Delivery failed for rhythm $id", e)
        } finally {
            // Always schedule the next one, otherwise a single failure would silence the rhythm.
            dao.rhythm(id)?.let { Scheduler.arm(ctx, it) }
        }
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
