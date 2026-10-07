package app.notify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notify.AppVM
import app.notify.data.Folder
import app.notify.data.Rhythm
import app.notify.engine.joinTimes
import app.notify.engine.parseTimes
import app.notify.engine.windowMinutes
import kotlinx.coroutines.launch

private val DayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

/** One plain sentence that says everything a rhythm does. */
private fun describe(r: Rhythm, folderNames: List<String>, is24: Boolean): String {
    val source = if (folderNames.isEmpty()) "the whole bank" else joinList(folderNames)
    val order = when {
        !r.shuffle -> "in order"
        r.smart -> "favouring words delivered least"
        else -> "shuffled"
    }
    val timing = if (r.fixedTimes) {
        "at " + joinList(parseTimes(r.times).map { hhmm(it, is24) })
    } else {
        "${r.perDay} ${if (r.perDay == 1) "time" else "times"} a day between ${hhmm(r.windowStart, is24)} and ${hhmm(r.windowEnd, is24)}"
    }
    val days = if ((r.days and 127) == 127) "" else " on ${daysText(r.days)}"
    val quiet = if (r.silent) ", without sound" else ""
    return "Words from $source, $order, $timing$days$quiet."
}

@Composable
fun RhythmScreen(vm: AppVM) {
    val c = Look.c
    val is24 = Look.is24
    val rhythmsN by vm.rhythms.collectAsState()
    val foldersN by vm.folders.collectAsState()
    val linksN by vm.links.collectAsState()
    var editing by remember { mutableStateOf<Rhythm?>(null) }
    var creating by remember { mutableStateOf(false) }

    val rhythms = rhythmsN
    val folders = foldersN
    val links = linksN
    if (rhythms == null || folders == null || links == null) {
        Box(Modifier.fillMaxSize()) // still loading
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
        item {
            Column {
                Text("Rhythm", style = Type.title, color = c.ink)
                Gap(4)
                Text("Choose which words arrive, and when.", style = Type.body, color = c.dim)
                Gap(24)
            }
        }
        if (rhythms.isEmpty()) {
            item {
                Column {
                    Text(
                        "A rhythm picks words from your folders and delivers them at random moments or fixed times.",
                        style = Type.body,
                        color = c.dim,
                    )
                    Gap(10)
                    Text(
                        "For a plain reminder, put one word in its own folder and give that folder a fixed time.",
                        style = Type.body,
                        color = c.dim,
                    )
                    Gap(20)
                }
            }
        }
        items(rhythms, key = { it.id }) { r ->
            val names = folders.filter { it.id in (links[r.id] ?: emptySet()) }.map { it.name }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The text and the switch are separate targets so a screen reader can reach both.
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { editing = r }
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                ) {
                    Text(r.name, style = Type.heading, color = if (r.enabled) c.ink else c.dim)
                    Gap(4)
                    Caption(describe(r, names, is24))
                }
                GapW(12)
                Switch(
                    checked = r.enabled,
                    onCheckedChange = { vm.setEnabled(r, it) },
                    modifier = Modifier.semantics { contentDescription = "${r.name} on or off" },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.ink,
                        checkedThumbColor = c.bg,
                        uncheckedTrackColor = c.raised,
                        uncheckedThumbColor = c.dim,
                        uncheckedBorderColor = c.line,
                    ),
                )
            }
        }
        item {
            Column {
                Gap(20)
                InkButton("New rhythm", { creating = true })
            }
        }
    }

    if (creating) {
        RhythmEditor(
            initial = null,
            folders = folders,
            linked = emptySet(),
            onDismiss = { creating = false },
            onSave = { r, ids -> vm.saveRhythm(r, ids); creating = false },
            onTest = { ids, title, quiet -> vm.testSend(ids, title, quiet) },
            onDelete = null,
        )
    }
    editing?.let { r ->
        RhythmEditor(
            initial = r,
            folders = folders,
            linked = links[r.id] ?: emptySet(),
            onDismiss = { editing = null },
            onSave = { nr, ids -> vm.saveRhythm(nr, ids); editing = null },
            onTest = { ids, title, quiet -> vm.testSend(ids, title, quiet) },
            onDelete = { vm.deleteRhythm(r); editing = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Gap(22)
    Text(text, style = Type.action, color = Look.c.ink)
    Gap(10)
}

/** A single-choice row with a short explanation, for choices that need more than a label. */
@Composable
private fun ChoiceRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    val c = Look.c
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) c.ink else Color.Transparent)
                .border(1.dp, if (selected) c.ink else c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(c.bg)) }
        GapW(14)
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = c.ink)
            Caption(detail)
        }
    }
}

@Composable
private fun TimeChip(minutes: Int, is24: Boolean, onClick: () -> Unit) =
    Pill(hhmm(minutes, is24), selected = false, onClick = onClick, isChoice = false)

@Composable
fun RhythmEditor(
    initial: Rhythm?,
    folders: List<Folder>,
    linked: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Rhythm, List<Long>) -> Unit,
    onTest: suspend (List<Long>, String, Boolean) -> String,
    onDelete: (() -> Unit)?,
) {
    val c = Look.c
    val is24 = Look.is24
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // 0 = in order, 1 = shuffled, 2 = fresh first (shuffled, leaning toward words delivered least)
    var order by remember { mutableIntStateOf(if (initial?.shuffle == false) 0 else if (initial?.smart == true) 2 else 1) }
    var silent by remember { mutableStateOf(initial?.silent ?: false) }
    var fixed by remember { mutableStateOf(initial?.fixedTimes ?: false) }
    var windowStart by remember { mutableIntStateOf(initial?.windowStart ?: 480) }
    var windowEnd by remember { mutableIntStateOf(initial?.windowEnd ?: 1260) }
    var perDay by remember { mutableIntStateOf(initial?.perDay ?: 3) }
    var times by remember { mutableStateOf(parseTimes(initial?.times ?: "480,1200")) }
    var days by remember { mutableIntStateOf(initial?.days ?: 127) }
    var picked by remember { mutableStateOf(linked) }
    var confirming by remember { mutableStateOf(false) }

    val folderNames = folders.filter { it.id in picked }.map { it.name }
    val windowLen = windowMinutes(windowStart, windowEnd)
    val timingOk = if (fixed) times.isNotEmpty() else windowLen >= 30
    val valid = timingOk && (days and 127) != 0

    SheetDialog(onDismiss) {
        Text(if (initial == null) "New rhythm" else "Edit rhythm", style = Type.heading, color = c.ink)
        Gap(16)
        LineField(name, { name = it }, hint = "Name, such as Morning verses")

        SectionTitle("Which words")
        if (folders.isEmpty()) {
            Caption("No folders yet, so every word in the bank is in play.")
        } else {
            folders.forEach { f ->
                val on = f.id in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = {
                            picked = if (on) picked - f.id else picked + f.id
                        })
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (on) hueOf(f.hue) else Color.Transparent)
                            .border(1.dp, if (on) hueOf(f.hue) else c.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Text("✓", fontSize = 12.sp, color = Color.White) }
                    GapW(12)
                    Text(f.name, style = Type.body, color = c.ink)
                }
            }
            Caption("Pick none to draw from the whole bank.")
        }

        SectionTitle("Order")
        ChoiceRow(order == 0, "In order", "Follows the order in each folder. You can reorder words in the Bank.") { order = 0 }
        ChoiceRow(order == 1, "Shuffled", "Every word appears once before any repeats.") { order = 1 }
        ChoiceRow(order == 2, "Fresh first", "Leans toward words delivered least, so newer words catch up.") { order = 2 }

        SectionTitle("When")
        Row {
            Pill("Random moments", !fixed, { fixed = false })
            GapW(8)
            Pill("Fixed times", fixed, { fixed = true })
        }
        Gap(8)
        if (fixed) {
            times.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeChip(t, is24) { pickTime(ctx, t, is24) { n -> times = (times - t + n).distinct().sorted() } }
                    TextAction("Remove", { times = times - t }, color = c.dim)
                }
            }
            TextAction("Add a time", { pickTime(ctx, 540, is24) { n -> times = (times + n).distinct().sorted() } })
            if (times.isEmpty()) Caption("Add at least one time.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("From", style = Type.body, color = c.dim, modifier = Modifier.width(60.dp))
                TimeChip(windowStart, is24) { pickTime(ctx, windowStart, is24) { windowStart = it } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Until", style = Type.body, color = c.dim, modifier = Modifier.width(60.dp))
                TimeChip(windowEnd, is24) { pickTime(ctx, windowEnd, is24) { windowEnd = it } }
            }
            if (windowLen < 30) Caption("The window needs to be at least 30 minutes long.")
            else if (windowEnd <= windowStart) Caption("This window runs overnight, into the next day.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Words per day", style = Type.body, color = c.dim)
                Box(Modifier.weight(1f))
                TextAction("−", { perDay = (perDay - 1).coerceAtLeast(1) })
                Text("$perDay", style = Type.heading, color = c.ink)
                TextAction("+", { perDay = (perDay + 1).coerceAtMost(24) })
            }
        }

        SectionTitle("Days")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { bit, letter ->
                val on = (days and (1 shl bit)) != 0
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (on) c.ink else Color.Transparent)
                        .border(1.dp, if (on) c.ink else c.line, CircleShape)
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { days = days xor (1 shl bit) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        style = Type.action,
                        color = if (on) c.bg else c.dim,
                        modifier = Modifier.semantics { contentDescription = DayNames[bit] },
                    )
                }
            }
        }
        if ((days and 127) == 0) {
            Gap(6)
            Caption("Choose at least one day.")
        }

        SectionTitle("Sound")
        Row {
            Pill("With sound", !silent, { silent = false })
            GapW(8)
            Pill("Silent", silent, { silent = true })
        }
        Gap(6)
        Caption(
            if (silent) "Silent words appear quietly in your notifications, with no sound or pop-up."
            else "Words arrive with your phone's normal notification sound.",
        )

        Gap(22)
        TextAction(
            "Send a test word now",
            {
                scope.launch {
                    testResult = onTest(picked.toList(), name.trim().ifEmpty { folderNames.firstOrNull() ?: "Test" }, silent)
                }
            },
        )
        if (testResult.isNotEmpty()) Caption(testResult)

        Gap(22)
        if (confirming && onDelete != null) {
            Text("Delete this rhythm?", style = Type.body, color = c.ink)
            Gap(12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Keep it", { confirming = false }, color = c.dim)
                Box(Modifier.weight(1f))
                InkButton("Delete rhythm", onDelete)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextAction("Delete", { confirming = true }, color = hueOf(2))
                Box(Modifier.weight(1f))
                TextAction("Cancel", onDismiss, color = c.dim)
                GapW(8)
                InkButton("Save", {
                    val base = initial ?: Rhythm(name = "")
                    onSave(
                        base.copy(
                            name = name.trim().ifEmpty { folderNames.firstOrNull() ?: "Rhythm" },
                            shuffle = order != 0,
                            smart = order == 2,
                            silent = silent,
                            fixedTimes = fixed,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            perDay = perDay,
                            times = joinTimes(times),
                            days = days,
                            cursor = 0,
                            bag = "",
                        ),
                        picked.toList(),
                    )
                }, enabled = valid)
            }
        }
    }
}
