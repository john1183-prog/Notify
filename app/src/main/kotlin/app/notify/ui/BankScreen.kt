package app.notify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.notify.AppVM
import app.notify.data.Message

@Composable
fun BankScreen(vm: AppVM, onOpen: (Long) -> Unit) {
    val c = Look.c
    val folders by vm.folders.collectAsState()
    val counts by vm.counts.collectAsState()
    val total by vm.total.collectAsState()
    var adding by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
        item {
            Column {
                Text("Bank", style = Type.title, color = c.ink)
                Gap(4)
                Text(
                    "${if (total == 1) "1 word" else "$total words"} in ${if (folders.size == 1) "1 folder" else "${folders.size} folders"}",
                    style = Type.body,
                    color = c.dim,
                )
                Gap(24)
            }
        }
        items(folders, key = { it.id }) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(f.id) }
                    .padding(horizontal = 4.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(hueOf(f.hue)))
                GapW(16)
                Text(f.name, style = Type.heading, color = c.ink, modifier = Modifier.weight(1f))
                Text("${counts[f.id] ?: 0}", style = Type.body, color = c.dim)
            }
        }
        item {
            Column {
                Gap(20)
                if (folders.isEmpty()) {
                    Text(
                        "Folders keep your words apart: verses in one, quotes in another. Make your first folder to begin.",
                        style = Type.body,
                        color = c.dim,
                    )
                    Gap(16)
                }
                InkButton("New folder", { adding = true })
            }
        }
    }

    if (adding) {
        FolderDialog(
            initial = null,
            wordCount = 0,
            onDismiss = { adding = false },
            onSave = { name, hue -> vm.addFolder(name, hue); adding = false },
            onDelete = null,
        )
    }
}

@Composable
fun FolderScreen(vm: AppVM, folderId: Long, onBack: () -> Unit) {
    val c = Look.c
    val folders by vm.folders.collectAsState()
    val words by remember(folderId) { vm.messages(folderId) }.collectAsState(initial = emptyList())
    var editingFolder by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<Message?>(null) }
    var addMode by remember { mutableStateOf<Boolean?>(null) } // null = closed, false = one word, true = many

    val folder = folders.firstOrNull { it.id == folderId }
    LaunchedEffect(folder == null && folders.isNotEmpty()) { if (folder == null && folders.isNotEmpty()) onBack() }
    if (folder == null) return

    val hue = hueOf(folder.hue)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("‹ Bank", onBack, color = c.dim)
            Box(Modifier.weight(1f))
            TextAction("Edit folder", { editingFolder = true })
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(hue))
                        GapW(14)
                        Text(folder.name, style = Type.title, color = c.ink)
                    }
                    Gap(8)
                    Text(
                        if (words.isEmpty()) "Nothing here yet. Add a verse, a quote or a lesson you want to keep."
                        else "A word's colour deepens each time it is delivered.",
                        style = Type.body,
                        color = c.dim,
                    )
                    Gap(24)
                }
            }
            items(words, key = { it.id }) { w ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { editingWord = w },
                ) {
                    Box(Modifier.fillMaxHeight().width(4.dp).clip(CircleShape).background(soakColor(hue, w.shown)))
                    GapW(16)
                    Column(Modifier.padding(vertical = 14.dp)) {
                        Text(w.text, style = Type.word, color = c.ink)
                        Gap(6)
                        Caption(
                            if (w.label.isBlank()) deliveredText(w.shown) else "${w.label}. ${deliveredText(w.shown)}.",
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.raised).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton("Add a word", { addMode = false })
            GapW(8)
            TextAction("Paste many", { addMode = true })
        }
    }

    if (editingFolder) {
        FolderDialog(
            initial = folder,
            wordCount = words.size,
            onDismiss = { editingFolder = false },
            onSave = { name, h -> vm.updateFolder(folder.copy(name = name, hue = h)); editingFolder = false },
            onDelete = { editingFolder = false; vm.deleteFolder(folder) },
        )
    }
    editingWord?.let { w ->
        MessageDialog(
            initial = w,
            bulk = false,
            onDismiss = { editingWord = null },
            onSave = { t, l -> vm.updateMessage(w.copy(text = t.trim(), label = l.trim())); editingWord = null },
            onDelete = { vm.deleteMessage(w); editingWord = null },
        )
    }
    addMode?.let { many ->
        MessageDialog(
            initial = null,
            bulk = many,
            onDismiss = { addMode = null },
            onSave = { t, l ->
                val texts = if (many) t.lines().map { it.trim() }.filter { it.isNotEmpty() } else listOf(t.trim())
                vm.addMessages(folderId, texts, if (many) "" else l.trim())
                addMode = null
            },
            onDelete = null,
        )
    }
}
