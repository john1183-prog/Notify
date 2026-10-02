package app.notify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.notify.AppVM
import app.notify.data.Folder
import app.notify.data.Rhythm
import app.notify.engine.joinTimes
import app.notify.engine.parseTimes

/** One plain sentence that says everything a rhythm does. */
private fun describe(r: Rhythm, folderNames: List<String>): String {
    val source = if (folderNames.isEmpty()) "the whole bank" else joinList(folderNames)
    val order = if (r.shuffle) "shuffled" else "in order"
    val timing = if (r.fixedTimes) {
        "at " + joinList(parseTimes(r.times).map { hhmm(it) })
    } else {
        "${r.perDay} ${if (r.perDay == 1) "time" else "times"} a day between ${hhmm(r.windowStart)} and ${hhmm(r.windowEnd)}"
    }
    val days = if ((r.days and 127) == 127) "" else " on ${daysText(r.days)}"
    return "Words from $source, $order, $timing$days."
}

@Composable
fun RhythmScreen(vm: AppVM) {
    val c = Look.c
    val rhythms by vm.rhythms.collectAsState()
    val folders by vm.folders.collectAsState()
    val links by vm.links.collectAsState()
    var editing by remember { mutableStateOf<Rhythm?>(null) }
    var creating by remember { mutableStateOf(false) }

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
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { editing = r }
                    .padding(horizontal = 4.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = Type.heading, color = if (r.enabled) c.ink else c.dim)
                    Gap(4)
                    Caption(describe(r, names))
                }
                GapW(12)
                Switch(
                    checked = r.enabled,
                    onCheckedChange = { vm.setEnabled(r, it) },
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

@Composable
private fun TimeChip(minutes: Int, onClick: () -> Unit) = Pill(hhmm(minutes), selected = false, onClick = onClick)

@Composable
fun RhythmEditor(
    initial: Rhythm?,
    folders: List<Folder>,
    linked: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Rhythm, List<Long>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Look.c
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var shuffle by remember { mutableStateOf(initial?.shuffle ?: true) }
    var fixed by remember { mutableStateOf(initial?.fixedTimes ?: false) }
    var windowStart by remember { mutableIntStateOf(initial?.windowStart ?: 480) }
    var windowEnd by remember { mutableIntStateOf(initial?.windowEnd ?: 1260) }
    var perDay by remember { mutableIntStateOf(initial?.perDay ?: 3) }
    var times by remember { mutableStateOf(parseTimes(initial?.times ?: "480,1200")) }
    var days by remember { mutableIntStateOf(initial?.days ?: 127) }
    var picked by remember { mutableStateOf(linked) }
    var confirming by remember { mutableStateOf(false) }

    val timingOk = if (fixed) times.isNotEmpty() else windowEnd - windowStart >= 30
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
                        .clickable { picked = if (on) picked - f.id else picked + f.id }
                        .padding(vertical = 8.dp),
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
        Row {
            Pill("Shuffled", shuffle, { shuffle = true })
            GapW(8)
            Pill("In order", !shuffle, { shuffle = false })
        }

        SectionTitle("When")
        Row {
            Pill("Random moments", !fixed, { fixed = false })
            GapW(8)
            Pill("Fixed times", fixed, { fixed = true })
        }
        Gap(14)
        if (fixed) {
            times.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeChip(t) { pickTime(ctx, t) { n -> times = (times - t + n).distinct().sorted() } }
                    TextAction("Remove", { times = times - t }, color = c.dim)
                }
                Gap(6)
            }
            TextAction("Add a time", { pickTime(ctx, 540) { n -> times = (times + n).distinct().sorted() } })
            if (times.isEmpty()) Caption("Add at least one time.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Between", style = Type.body, color = c.dim)
                GapW(10)
                TimeChip(windowStart) { pickTime(ctx, windowStart) { windowStart = it } }
                GapW(10)
                Text("and", style = Type.body, color = c.dim)
                GapW(10)
                TimeChip(windowEnd) { pickTime(ctx, windowEnd) { windowEnd = it } }
            }
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Words per day", style = Type.body, color = c.dim)
                Box(Modifier.weight(1f))
                TextAction("−", { perDay = (perDay - 1).coerceAtLeast(1) })
                Text("$perDay", style = Type.heading, color = c.ink)
                TextAction("+", { perDay = (perDay + 1).coerceAtMost(24) })
            }
            if (windowEnd - windowStart < 30) Caption("The window needs to be at least 30 minutes long.")
        }

        SectionTitle("Days")
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { bit, letter ->
                val on = (days and (1 shl bit)) != 0
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (on) c.ink else Color.Transparent)
                        .border(1.dp, if (on) c.ink else c.line, CircleShape)
                        .clickable { days = days xor (1 shl bit) },
                    contentAlignment = Alignment.Center,
                ) { Text(letter, style = Type.action, color = if (on) c.bg else c.dim) }
                GapW(6)
            }
        }
        if ((days and 127) == 0) {
            Gap(6)
            Caption("Choose at least one day.")
        }

        Gap(28)
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
                            name = name.trim().ifEmpty { "Rhythm" },
                            shuffle = shuffle,
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
