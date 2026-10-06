package app.notify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.notify.AppVM
import app.notify.data.Folder
import app.notify.data.Message

/** The first colour no folder uses yet, so new folders start out distinct. */
private fun nextHue(folders: List<Folder>, hueCount: Int): Int =
    (0 until hueCount).firstOrNull { h -> folders.none { it.hue == h } } ?: (folders.size % hueCount)

@Composable
fun BankScreen(vm: AppVM, onOpen: (Long) -> Unit) {
    val c = Look.c
    val foldersN by vm.folders.collectAsState()
    val counts by vm.counts.collectAsState()
    val totalN by vm.total.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editingWord by remember { mutableStateOf<Message?>(null) }
    val results by remember(query) { vm.search(query) }.collectAsState(initial = emptyList())

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    val folders = foldersN
    val total = totalN
    if (folders == null || total == null) {
        Box(Modifier.fillMaxSize()) // still loading
        return
    }
    val searching = query.isNotBlank()

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
                if (total > 0) {
                    Gap(16)
                    LineField(query, { query = it }, hint = "Search every word")
                }
                Gap(18)
            }
        }
        if (searching) {
            if (results.isEmpty()) {
                item { Text("No word matches.", style = Type.body, color = c.dim) }
            }
            items(results, key = { it.id }) { w ->
                val folder = folders.firstOrNull { it.id == w.folderId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { editingWord = w },
                ) {
                    Box(
                        Modifier.fillMaxHeight().width(4.dp).clip(CircleShape)
                            .background(soakColor(hueOf(folder?.hue ?: 0), w.shown)),
                    )
                    GapW(16)
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(w.text, style = Type.word, color = c.ink)
                        Gap(4)
                        Caption(folder?.name ?: "")
                    }
                }
            }
        } else {
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
                    Text("${counts?.get(f.id) ?: 0}", style = Type.body, color = c.dim)
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
                    Gap(40)
                    Text("Back up", style = Type.heading, color = c.ink)
                    Gap(6)
                    Caption("Export saves every folder, word and rhythm to a file you keep. Import adds them back and skips words you already have.")
                    Row {
                        TextAction("Export", { exportLauncher.launch("notify-bank.json") })
                        TextAction("Import", { importLauncher.launch(arrayOf("*/*")) })
                    }
                }
            }
        }
    }

    if (adding) {
        FolderDialog(
            initial = null,
            wordCount = 0,
            defaultHue = nextHue(folders, c.hues.size),
            onDismiss = { adding = false },
            // The new folder opens straight away, ready for its first word.
            onSave = { name, hue -> vm.addFolder(name, hue) { id -> onOpen(id) }; adding = false },
            onDelete = null,
        )
    }
    editingWord?.let { w ->
        MessageDialog(
            initial = w,
            bulk = false,
            folders = folders,
            folderId = w.folderId,
            onDismiss = { editingWord = null },
            onSave = { t, l, fid -> vm.updateMessage(w.copy(text = t.trim(), label = l.trim(), folderId = fid)); editingWord = null },
            onSaveAndNext = null,
            onDelete = { vm.deleteMessage(w); editingWord = null },
        )
    }
}

@Composable
fun FolderScreen(vm: AppVM, folderId: Long, onBack: () -> Unit) {
    val c = Look.c
    val foldersN by vm.folders.collectAsState()
    val wordsN by remember(folderId) { vm.messages(folderId) }
        .collectAsState<List<Message>, List<Message>?>(initial = null)
    var editingFolder by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<Message?>(null) }
    var addMode by remember { mutableStateOf<Boolean?>(null) } // null = closed, false = one word, true = many
    var dialogKey by remember { mutableIntStateOf(0) }

    val folder = foldersN?.firstOrNull { it.id == folderId }
    // Leave this screen once the folder is gone (deleted), but not while folders are still loading.
    LaunchedEffect(foldersN != null, folder == null) { if (foldersN != null && folder == null) onBack() }
    if (folder == null) return

    val words = wordsN
    val allFolders = foldersN ?: emptyList()
    val hue = hueOf(folder.hue)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        when {
                            words == null -> ""
                            words.isEmpty() -> "Nothing here yet. Add a verse, a quote or a lesson you want to keep."
                            else -> "A word's colour deepens each time it is delivered."
                        },
                        style = Type.body,
                        color = c.dim,
                    )
                    Gap(24)
                }
            }
            items(words ?: emptyList(), key = { it.id }) { w ->
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
            Modifier.fillMaxWidth().background(c.raised).padding(horizontal = 24.dp, vertical = 10.dp),
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
            wordCount = words?.size ?: 0,
            defaultHue = folder.hue,
            onDismiss = { editingFolder = false },
            onSave = { name, h -> vm.updateFolder(folder.copy(name = name, hue = h)); editingFolder = false },
            onDelete = { editingFolder = false; vm.deleteFolder(folder) },
        )
    }
    editingWord?.let { w ->
        MessageDialog(
            initial = w,
            bulk = false,
            folders = allFolders,
            folderId = w.folderId,
            onDismiss = { editingWord = null },
            onSave = { t, l, fid -> vm.updateMessage(w.copy(text = t.trim(), label = l.trim(), folderId = fid)); editingWord = null },
            onSaveAndNext = null,
            onDelete = { vm.deleteMessage(w); editingWord = null },
        )
    }
    addMode?.let { many ->
        // The key resets the dialog's fields after "Save and add another".
        key(dialogKey) {
            MessageDialog(
                initial = null,
                bulk = many,
                folders = allFolders,
                folderId = folderId,
                onDismiss = { addMode = null },
                onSave = { t, l, _ ->
                    val texts = if (many) splitWords(t) else listOf(t.trim())
                    vm.addMessages(folderId, texts, if (many) "" else l.trim())
                    addMode = null
                },
                onSaveAndNext = if (many) null else { t, l ->
                    vm.addMessages(folderId, listOf(t.trim()), l.trim())
                    dialogKey++
                },
                onDelete = null,
            )
        }
    }
}
