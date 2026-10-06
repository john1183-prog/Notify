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

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Words", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The words your rhythms deliver"
                },
            )
        }
    }

    /** False when notifications, or this channel, are switched off, so deliveries are not counted. */
    fun canPost(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        val channel = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** One notification slot per rhythm, so a new word replaces the last instead of piling up. */
    @SuppressLint("MissingPermission")
    fun post(ctx: Context, r: Rhythm, m: Message): Boolean {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hasLabel = m.label.isNotBlank()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF3F62E0.toInt())
            .setContentTitle(if (hasLabel) m.label else r.name)
            .setSubText(if (hasLabel) r.name else null)
            .setContentText(m.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(r.id.toInt(), n)
            true
        } catch (_: SecurityException) {
            false // Permission missing; the Today screen explains how to grant it.
        }
    }
}
