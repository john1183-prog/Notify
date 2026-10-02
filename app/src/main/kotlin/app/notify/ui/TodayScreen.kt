package app.notify.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.notify.AppVM
import app.notify.data.Message
import app.notify.engine.Scheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private fun openNotificationSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun TodayScreen(vm: AppVM, resumeTick: Int, goBank: () -> Unit, goRhythm: () -> Unit) {
    val c = Look.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val total by vm.total.collectAsState()
    val folders by vm.folders.collectAsState()
    val rhythms by vm.rhythms.collectAsState()
    var drawn by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(total > 0) { drawn = if (total > 0) vm.draw() else null }

    var notifOk by remember(resumeTick) {
        mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled())
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        // Android stops showing the prompt after repeated refusals; Settings is the only way back.
        if (!granted && !notifOk) openNotificationSettings(ctx)
    }
    val exactMissing = remember(resumeTick, rhythms) {
        rhythms.any { it.enabled && it.fixedTimes } && !Scheduler.canExact(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            DateTimeFormatter.ofPattern("EEEE, d MMMM").format(LocalDate.now()),
            style = Type.body,
            color = c.dim,
        )
        Gap(20)

        if (!notifOk) {
            Banner(
                "Notifications are off, so no word can reach you.",
                "Allow notifications",
            ) {
                if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else openNotificationSettings(ctx)
            }
        }
        if (exactMissing) {
            Banner(
                "Fixed times can arrive a few minutes late until exact alarms are allowed.",
                "Allow exact alarms",
            ) { openExactAlarmSettings(ctx) }
        }

        val m = drawn
        if (m == null) {
            Text("Your bank is empty", style = Type.title, color = c.ink)
            Gap(10)
            Text(
                "Add a few verses, quotes or lessons and they will show up here.",
                style = Type.body,
                color = c.dim,
            )
            Gap(20)
            InkButton("Open the bank", goBank)
        } else {
            val folder = folders.firstOrNull { it.id == m.folderId }
            val hue = hueOf(folder?.hue ?: 0)
            val tint by animateColorAsState(hue.copy(alpha = 0.10f + 0.24f * soakOf(m.shown)), label = "tint")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(tint)
                    .padding(28.dp),
            ) {
                Text(m.text, style = if (m.text.length < 70) Type.hero else Type.word, color = c.ink)
                if (m.label.isNotBlank()) {
                    Gap(14)
                    Text(m.label, style = Type.action, color = c.ink)
                }
                Gap(18)
                Caption("From ${folder?.name ?: "your bank"}. ${deliveredText(m.shown)}.", color = c.ink)
            }
            Gap(6)
            TextAction("Show another", { scope.launch { drawn = vm.draw() } })
        }

        Gap(36)
        Text("Coming up", style = Type.heading, color = c.ink)
        Gap(10)
        val now = System.currentTimeMillis()
        val next = rhythms.filter { it.enabled && it.nextAt > now }.minByOrNull { it.nextAt }
        if (next != null) {
            Text(next.name, style = Type.body, color = c.ink)
            Caption(whenText(next.nextAt, now, exact = next.fixedTimes))
        } else {
            Text("Nothing is scheduled.", style = Type.body, color = c.dim)
            Gap(8)
            TextAction("Set a rhythm", goRhythm)
        }
    }
}
