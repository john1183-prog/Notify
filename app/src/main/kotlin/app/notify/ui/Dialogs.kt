package app.notify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import app.notify.data.Folder
import app.notify.data.Message

@Composable
fun FolderDialog(
    initial: Folder?,
    wordCount: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, hue: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Look.c
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var hue by remember { mutableStateOf(initial?.hue ?: 0) }
    var confirming by remember { mutableStateOf(false) }

    SheetDialog(onDismiss) {
        Text(if (initial == null) "New folder" else "Edit folder", style = Type.heading, color = c.ink)
        Gap(16)
        LineField(name, { name = it }, hint = "Folder name", style = Type.heading)
        Gap(22)
        Text("Colour", style = Type.small, color = c.dim)
        Gap(10)
        Row {
            c.hues.forEachIndexed { i, color ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (i == hue) 3.dp else 0.dp, c.ink, CircleShape)
                        .clickable { hue = i },
                )
                GapW(10)
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
