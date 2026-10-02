package app.notify.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.notify.data.Db
import app.notify.data.Rhythm
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * One alarm per rhythm. Fixed times use an exact alarm when the user has allowed it;
 * random moments never need to be exact, so they use the battery-friendlier inexact alarm.
 * The chosen time is stored in [Rhythm.nextAt] so the UI and reboot recovery agree with the alarm.
 */
object Scheduler {
    fun plan(r: Rhythm) = Plan(r.fixedTimes, r.windowStart, r.windowEnd, r.perDay, parseTimes(r.times), r.days)

    private fun alarms(ctx: Context): AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    private fun pending(ctx: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        id.toInt(),
        Intent(ctx, FireReceiver::class.java).setAction(FireReceiver.ACTION).putExtra(FireReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarms(ctx).canScheduleExactAlarms()

    fun cancel(ctx: Context, id: Long) = alarms(ctx).cancel(pending(ctx, id))

    private fun set(ctx: Context, r: Rhythm, at: Long) {
        val am = alarms(ctx)
        val pi = pending(ctx, r.id)
        if (r.fixedTimes && canExact(ctx)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                return
            } catch (_: SecurityException) {
                // Permission was revoked between the check and the call: fall through to inexact.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Computes a fresh next time for [r] and sets its alarm. */
    suspend fun arm(ctx: Context, r: Rhythm, now: ZonedDateTime = ZonedDateTime.now()) {
        val dao = Db.get(ctx).dao()
        val next = if (r.enabled) nextFire(plan(r), now, r.lastFiredAt, Random.Default) else null
        if (next == null) {
            cancel(ctx, r.id)
            dao.saveNext(r.id, 0)
            return
        }
        val at = next.toInstant().toEpochMilli()
        dao.saveNext(r.id, at)
        set(ctx, r, at)
    }

    /**
     * Re-creates alarms after a reboot or app update. Keeps the stored time when it is still
     * in the future, so random moments do not reshuffle every time the app is opened.
     */
    suspend fun restore(ctx: Context, recompute: Boolean) {
        val now = System.currentTimeMillis()
        for (r in Db.get(ctx).dao().rhythmsOnce()) {
            if (!r.enabled) continue
            if (!recompute && r.nextAt > now) set(ctx, r, r.nextAt) else arm(ctx, r)
        }
    }
}
