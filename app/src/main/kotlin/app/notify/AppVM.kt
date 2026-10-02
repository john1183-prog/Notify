package app.notify

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.notify.data.Db
import app.notify.data.Folder
import app.notify.data.Message
import app.notify.data.Rhythm
import app.notify.engine.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One ViewModel is plenty for three screens; split per screen when any of them grows logic. */
class AppVM(app: Application) : AndroidViewModel(app) {
    private val dao = Db.get(app).dao()
    private val ctx: Application get() = getApplication()

    private fun <T> Flow<T>.hot(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val folders = dao.folders().hot(emptyList())
    val counts = dao.counts().map { l -> l.associate { it.folderId to it.n } }.hot(emptyMap())
    val total = dao.messageCount().hot(0)
    val rhythms = dao.rhythms().hot(emptyList())
    val links = dao.links()
        .map { l -> l.groupBy({ it.rhythmId }, { it.folderId }).mapValues { it.value.toSet() } }
        .hot(emptyMap())

    fun messages(folderId: Long): Flow<List<Message>> = dao.messages(folderId)
    suspend fun draw(): Message? = dao.randomMessage()

    fun addFolder(name: String, hue: Int) = viewModelScope.launch {
        dao.insertFolder(Folder(name = name.trim(), hue = hue))
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
    fun deleteMessage(m: Message) = viewModelScope.launch { dao.deleteMessage(m) }

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
}
