package app.notify

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.notify.data.Backup
import app.notify.data.Db
import app.notify.data.Folder
import app.notify.data.Message
import app.notify.data.Rhythm
import app.notify.engine.Notifier
import app.notify.engine.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short message shown above the tab bar, optionally with one action such as Undo. */
data class Notice(val text: String, val action: String? = null, val run: (() -> Unit)? = null)

/** One ViewModel is plenty for three screens; split per screen when any of them grows logic. */
class AppVM(app: Application) : AndroidViewModel(app) {
    private val dao = Db.get(app).dao()
    private val ctx: Application get() = getApplication()

    // Null means "still loading", so screens can stay quiet instead of flashing empty-state text.
    private fun <T> Flow<T>.hot(): StateFlow<T?> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders = dao.folders().hot()
    val counts = dao.counts().map { l -> l.associate { it.folderId to it.n } }.hot()
    val total = dao.messageCount().hot()
    val rhythms = dao.rhythms().hot()
    val links = dao.links()
        .map { l -> l.groupBy({ it.rhythmId }, { it.folderId }).mapValues { it.value.toSet() } }
        .hot()

    fun messages(folderId: Long): Flow<List<Message>> = dao.messages(folderId)
    fun search(q: String): Flow<List<Message>> = if (q.isBlank()) flowOf(emptyList()) else dao.search(q.trim())

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice
    private var noticeJob: Job? = null

    fun say(text: String, action: String? = null, run: (() -> Unit)? = null) {
        noticeJob?.cancel()
        val n = Notice(text, action, run)
        _notice.value = n
        noticeJob = viewModelScope.launch {
            delay(6_000)
            if (_notice.value === n) _notice.value = null
        }
    }

    fun dismissNotice() { _notice.value = null }

    // The word shown on Today lives here so it survives switching tabs.
    private val _hero = MutableStateFlow<Message?>(null)
    val hero: StateFlow<Message?> = _hero

    /** Keeps the current word if it still exists (picking up edits), otherwise draws a new one. */
    fun refreshHero() = viewModelScope.launch {
        _hero.value = _hero.value?.let { dao.message(it.id) } ?: dao.randomMessage()
    }

    /** Used when a notification is tapped: show exactly that word on Today. */
    fun showWord(id: Long) = viewModelScope.launch { dao.message(id)?.let { _hero.value = it } }

    fun nextHero() = viewModelScope.launch {
        _hero.value = dao.randomOther(_hero.value?.id ?: 0) ?: dao.randomMessage()
    }

    fun addFolder(name: String, hue: Int, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        onCreated(dao.insertFolder(Folder(name = name.trim(), hue = hue)))
    }

    fun updateFolder(f: Folder) = viewModelScope.launch { dao.updateFolder(f) }

    fun deleteFolder(f: Folder) = viewModelScope.launch {
        // A rhythm that drew only from this folder would silently widen to the whole bank. Pause it instead.
        val orphaned = dao.onlyUsing(f.id)
        orphaned.forEach { dao.setEnabled(it, false) }
        dao.deleteFolder(f)
        orphaned.forEach { id -> dao.rhythm(id)?.let { Scheduler.arm(ctx, it) } }
    }

    fun addMessages(folderId: Long, texts: List<String>, label: String) = viewModelScope.launch {
        dao.insertMessages(texts.map { Message(folderId = folderId, text = it, label = label) })
    }

    fun updateMessage(m: Message) = viewModelScope.launch { dao.updateMessage(m) }
    fun deleteMessage(m: Message) = viewModelScope.launch {
        dao.deleteMessage(m)
        say("Word deleted.", "Undo") {
            viewModelScope.launch {
                // The folder may have been deleted in the meantime; then there is nothing to restore into.
                runCatching { dao.insertMessages(listOf(m)) }
            }
            dismissNotice()
        }
    }

    fun saveRhythm(r: Rhythm, folderIds: List<Long>) = viewModelScope.launch {
        val id = dao.saveRhythm(r, folderIds)
        dao.rhythm(id)?.let { Scheduler.arm(ctx, it) }
    }

    fun setEnabled(r: Rhythm, on: Boolean) = viewModelScope.launch {
        dao.setEnabled(r.id, on)
        dao.rhythm(r.id)?.let { Scheduler.arm(ctx, it) }
    }

    fun deleteRhythm(r: Rhythm) = viewModelScope.launch {
        Scheduler.cancel(ctx, r.id)
        dao.deleteRhythm(r)
    }

    // Trying a notification without waiting for a schedule. These change no counters or schedules.
    suspend fun testWord(word: Message, title: String): String = when {
        !Notifier.canPost(ctx) -> "Notifications are off for Notify."
        Notifier.test(ctx, title, word) -> "Sent. Check your notifications."
        else -> "The notification could not be posted."
    }

    suspend fun testSend(folderIds: List<Long>, title: String): String {
        val pool = if (folderIds.isEmpty()) dao.poolIds() else dao.poolIdsIn(folderIds)
        val word = pool.randomOrNull()?.let { dao.message(it) } ?: return "There are no words to send yet."
        return testWord(word, title)
    }

    // Backup
    fun exportTo(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val json = Backup.export(dao)
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }.isSuccess
        }
        say(if (ok) "Bank exported." else "The export did not work.")
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                Backup.import(dao, text)
            }
        }
        result.onSuccess { r ->
            r.rhythmIds.forEach { id -> dao.rhythm(id)?.let { Scheduler.arm(ctx, it) } }
            say("Added ${r.words} ${if (r.words == 1) "word" else "words"} and ${r.folders} ${if (r.folders == 1) "folder" else "folders"}.")
        }.onFailure { say("That file is not a Notify export.") }
    }
}
