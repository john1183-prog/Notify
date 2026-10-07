package app.notify.engine

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.notify.MainActivity
import app.notify.R
import app.notify.data.Message
import app.notify.data.Rhythm

object Notifier {
    private const val CHANNEL = "words"
    private const val QUIET = "words_quiet"
    private const val TEST_ID = 1_000_000_000

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Words", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The words your rhythms deliver"
                },
            )
        }
        if (nm.getNotificationChannel(QUIET) == null) {
            nm.createNotificationChannel(
                NotificationChannel(QUIET, "Quiet words", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Words from silent rhythms: no sound, no pop-up"
                },
            )
        }
    }

    /** False when notifications, or this channel, are switched off, so deliveries are not counted. */
    fun canPost(ctx: Context, silent: Boolean = false): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        val channel = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(if (silent) QUIET else CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** One notification slot per rhythm, so a new word replaces the last instead of piling up. */
    fun post(ctx: Context, r: Rhythm, m: Message): Boolean {
        val hasLabel = m.label.isNotBlank()
        return show(ctx, r.id.toInt(), if (hasLabel) m.label else r.name, if (hasLabel) r.name else null, m, r.silent)
    }

    /** A one-off notification for trying things out. It changes no counters and no schedules. */
    fun test(ctx: Context, title: String, m: Message, silent: Boolean = false): Boolean =
        show(ctx, TEST_ID, title.ifBlank { "Notify" }, m.label.takeIf { it.isNotBlank() }, m, silent)

    @SuppressLint("MissingPermission")
    private fun show(ctx: Context, id: Int, title: String, subText: String?, m: Message, silent: Boolean): Boolean {
        ensureChannel(ctx)
        // Tapping the notification opens Today on this very word.
        val open = PendingIntent.getActivity(
            ctx,
            id,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_WORD, m.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, if (silent) QUIET else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF3F62E0.toInt())
            .setContentTitle(title)
            .setSubText(subText)
            .setContentText(m.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(id, n)
            true
        } catch (_: SecurityException) {
            false // Permission missing; the Today screen explains how to grant it.
        }
    }
}
