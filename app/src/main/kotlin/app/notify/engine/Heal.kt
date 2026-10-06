package app.notify.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Every few hours, put back any alarm the system or a battery manager dropped.
 * It cannot help after a force-stop (Android blocks all background work then), but it covers
 * the common case of alarms vanishing while the app was merely swiped away.
 */
class HealWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Scheduler.restore(applicationContext, recompute = false)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<HealWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "heal-alarms",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
