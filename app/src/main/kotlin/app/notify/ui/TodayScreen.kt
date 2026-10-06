package app.notify.ui

import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.notify.AppVM
import app.notify.engine.Scheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private fun openNotificationSettings(ctx: Context) = safeStart(
    ctx,
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
)

private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        safeStart(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
    }
}

private fun openBatterySettings(ctx: Context) =
    safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

private fun openAppSettings(ctx: Context) =
    safeStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

@Composable
fun TodayScreen(vm: AppVM, resumeTick: Int, goBank: () -> Unit, goRhythm: () -> Unit) {
    val c = Look.c
    val is24 = Look.is24
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val totalN by vm.total.collectAsState()
    val foldersN by vm.folders.collectAsState()
    val rhythmsN by vm.rhythms.collectAsState()
    val hero by vm.hero.collectAsState()
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(totalN) { if (totalN != null) vm.refreshHero() }

    var notifOk by remember(resumeTick) {
        mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled())
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        // Android stops showing the prompt after repeated refusals; Settings is the only way back.
        if (!granted && !notifOk) openNotificationSettings(ctx)
    }
    val exactMissing = remember(resumeTick, rhythmsN) {
        rhythmsN?.any { it.enabled && it.fixedTimes } == true && !Scheduler.canExact(ctx)
    }

    val count = totalN
    val rhythms = rhythmsN
    if (count == null || rhythms == null) {
        Box(Modifier.fillMaxSize()) // still loading: stay quiet rather than flash an empty state
        return
    }
    val folders = foldersN ?: emptyList()

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
            Banner("Notifications are off, so no word can reach you.", "Allow notifications") {
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

        val m = hero
        if (count == 0) {
            Text("Your bank is empty", style = Type.title, color = c.ink)
            Gap(10)
            Text(
                "Add a few verses, quotes or lessons and they will show up here.",
                style = Type.body,
                color = c.dim,
            )
            Gap(20)
            InkButton("Open the bank", goBank)
        } else if (m == null) {
            Box(Modifier.fillMaxWidth().height(200.dp)) // the word is being drawn
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Show another", { vm.nextHero() })
                TextAction("Send it to me", { scope.launch { vm.say(vm.testWord(m, "Test")) } }, color = c.dim)
            }
        }

        Gap(30)
        Text("Coming up", style = Type.heading, color = c.ink)
        Gap(10)
        val now = System.currentTimeMillis()
        val next = rhythms.filter { it.enabled && it.nextAt > now }.minByOrNull { it.nextAt }
        if (next != null) {
            Text(next.name, style = Type.body, color = c.ink)
            Caption(whenText(next.nextAt, now, exact = next.fixedTimes, is24 = is24))
        } else {
            Text("Nothing is scheduled.", style = Type.body, color = c.dim)
            TextAction("Set a rhythm", goRhythm)
        }

        Gap(24)
        TextAction("Not arriving on time?", { showHelp = true }, color = c.dim)
    }

    if (showHelp) {
        ReliabilityDialog(
            onDismiss = { showHelp = false },
            onBattery = { openBatterySettings(ctx) },
            onApp = { openAppSettings(ctx) },
        )
    }
}
