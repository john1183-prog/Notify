package app.notify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.notify.data.Folder
import app.notify.data.Message

@Composable
fun FolderDialog(
    initial: Folder?,
    wordCount: Int,
    defaultHue: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, hue: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Look.c
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var hue by remember { mutableStateOf(initial?.hue ?: defaultHue) }
    var confirming by remember { mutableStateOf(false) }

    SheetDialog(onDismiss) {
        Text(if (initial == null) "New folder" else "Edit folder", style = Type.heading, color = c.ink)
        Gap(16)
        LineField(name, { name = it }, hint = "Folder name", style = Type.heading)
        Gap(22)
        Text("Colour", style = Type.small, color = c.dim)
        Gap(10)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            c.hues.forEachIndexed { i, color ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(3.dp, if (i == hue) c.ink else Color.Transparent, CircleShape)
                        .selectable(selected = i == hue, role = Role.RadioButton, onClick = { hue = i })
                        .semantics { contentDescription = HueNames[i] },
                )
            }
        }
        Gap(26)
        if (confirming && onDelete != null) {
            Text(
                if (wordCount == 0) "Delete this folder?" else "Delete this folder and its $wordCount ${if (wordCount == 1) "word" else "words"}? This cannot be undone.",
                style = Type.body,
                color = c.ink,
            )
            Gap(12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Keep it", { confirming = false }, color = c.dim)
                Box(Modifier.weight(1f))
                InkButton("Delete folder", onDelete)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextAction("Delete", { confirming = true }, color = hueOf(2))
                Box(Modifier.weight(1f))
                TextAction("Cancel", onDismiss, color = c.dim)
                GapW(8)
                InkButton("Save", { onSave(name.trim(), hue) }, enabled = name.isNotBlank())
            }
        }
    }
}

@Composable
fun MessageDialog(
    initial: Message?,
    bulk: Boolean,
    onDismiss: () -> Unit,
    onSave: (text: String, label: String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Look.c
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }

    SheetDialog(onDismiss) {
        Text(
            when {
                initial != null -> "Edit word"
                bulk -> "Paste many words"
                else -> "New word"
            },
            style = Type.heading,
            color = c.ink,
        )
        Gap(16)
        LineField(
            text,
            { text = it },
            hint = if (bulk) "One word per line" else "A verse, a quote, a lesson",
            style = Type.word,
            singleLine = false,
            minLines = if (bulk) 6 else 3,
        )
        if (!bulk) {
            Gap(14)
            LineField(label, { label = it }, hint = "Source, such as John 3:16 (optional)")
        }
        Gap(26)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) TextAction("Delete", onDelete, color = hueOf(2))
            Box(Modifier.weight(1f))
            TextAction("Cancel", onDismiss, color = c.dim)
            GapW(8)
            InkButton("Save", { onSave(text, label) }, enabled = text.isNotBlank())
        }
    }
}

/** Plain-language help for the most common reason a notification app feels unreliable. */
@Composable
fun ReliabilityDialog(onDismiss: () -> Unit, onBattery: () -> Unit, onApp: () -> Unit) {
    val c = Look.c
    SheetDialog(onDismiss) {
        Text("If words arrive late or not at all", style = Type.heading, color = c.ink)
        Gap(12)
        Text(
            "Some phones stop apps in the background to save battery. To keep Notify on time:",
            style = Type.body,
            color = c.ink,
        )
        Gap(12)
        Text("1. Open battery settings and set Notify to Unrestricted, or choose Don't optimise.", style = Type.body, color = c.ink)
        Gap(8)
        Text("2. On Tecno, Infinix, Xiaomi, Oppo and similar phones, also allow auto-start for Notify and lock it in the recent apps list.", style = Type.body, color = c.ink)
        Gap(8)
        Text("3. Avoid force-stopping Notify. Android cancels every alarm until you open the app again.", style = Type.body, color = c.ink)
        Gap(12)
        Caption("Notify also checks its alarms every few hours and puts back any that went missing.")
        Gap(22)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("App settings", onApp, color = c.dim)
            Box(Modifier.weight(1f))
            InkButton("Battery settings", onBattery)
        }
    }
}
