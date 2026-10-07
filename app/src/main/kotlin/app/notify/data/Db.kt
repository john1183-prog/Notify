package app.notify.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BankDao {
    // Folders
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    abstract fun folders(): Flow<List<Folder>>

    @Query("SELECT folderId, COUNT(*) AS n FROM messages GROUP BY folderId")
    abstract fun counts(): Flow<List<FolderCount>>

    @Insert abstract suspend fun insertFolder(f: Folder): Long
    @Update abstract suspend fun updateFolder(f: Folder)
    @Delete abstract suspend fun deleteFolder(f: Folder)

    // Words
    @Query("SELECT * FROM messages WHERE folderId = :folderId ORDER BY position, id")
    abstract fun messages(folderId: Long): Flow<List<Message>>

    @Query("SELECT COUNT(*) FROM messages")
    abstract fun messageCount(): Flow<Int>

    @Query("SELECT * FROM messages ORDER BY known, RANDOM() LIMIT 1")
    abstract suspend fun randomMessage(): Message?

    @Query("SELECT * FROM messages WHERE id != :id ORDER BY known, RANDOM() LIMIT 1")
    abstract suspend fun randomOther(id: Long): Message?

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun message(id: Long): Message?

    /** Case-insensitive match on text or source, without LIKE wildcards getting in the way. */
    @Query("SELECT * FROM messages WHERE instr(lower(text), lower(:q)) > 0 OR instr(lower(label), lower(:q)) > 0 ORDER BY folderId, position, id LIMIT 100")
    abstract fun search(q: String): Flow<List<Message>>

    // One-shot snapshots for backup and import
    @Query("SELECT * FROM folders ORDER BY id")
    abstract suspend fun foldersOnce(): List<Folder>

    @Query("SELECT * FROM messages ORDER BY folderId, position, id")
    abstract suspend fun wordsOnce(): List<Message>

    @Query("SELECT * FROM rhythm_folders")
    abstract suspend fun linksOnce(): List<RhythmFolder>

    /** Ids only: delivery never needs to load every word's text. */
    /** Words eligible for delivery: known words rest. Ids only, so delivery never loads every text. */
    @Query("SELECT id FROM messages WHERE known = 0 ORDER BY folderId, position, id")
    abstract suspend fun poolIds(): List<Long>

    @Query("SELECT id FROM messages WHERE known = 0 AND folderId IN (:folderIds) ORDER BY folderId, position, id")
    abstract suspend fun poolIdsIn(folderIds: List<Long>): List<Long>

    @Query("SELECT id, shown FROM messages WHERE known = 0 ORDER BY folderId, position, id")
    abstract suspend fun poolRows(): List<PoolRow>

    @Query("SELECT id, shown FROM messages WHERE known = 0 AND folderId IN (:folderIds) ORDER BY folderId, position, id")
    abstract suspend fun poolRowsIn(folderIds: List<Long>): List<PoolRow>

    @Query("SELECT folderId, shown, known FROM messages ORDER BY folderId, position, id")
    abstract fun shownRows(): Flow<List<ShownRow>>

    @Query("UPDATE messages SET known = :known WHERE id = :id")
    abstract suspend fun setKnown(id: Long, known: Boolean)

    // Order inside a folder
    @Query("SELECT COALESCE(MAX(position), 0) FROM messages WHERE folderId = :folderId")
    abstract suspend fun maxPosition(folderId: Long): Int

    @Query("SELECT * FROM messages WHERE folderId = :folderId ORDER BY position, id")
    abstract suspend fun wordsInFolder(folderId: Long): List<Message>

    @Query("UPDATE messages SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int)

    /** Moves a word one step up (-1) or down (+1), renumbering the folder so ties can never block a move. */
    @Transaction
    open suspend fun moveWord(folderId: Long, id: Long, delta: Int) {
        val list = wordsInFolder(folderId)
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val reordered = list.toMutableList().also { it.add(to, it.removeAt(from)) }
        reordered.forEachIndexed { index, m -> if (m.position != index + 1) setPosition(m.id, index + 1) }
    }

    // Delivery history
    @Insert abstract suspend fun insertDelivery(d: Delivery)

    @Query("DELETE FROM deliveries WHERE id NOT IN (SELECT id FROM deliveries ORDER BY deliveredAt DESC, id DESC LIMIT 200)")
    abstract suspend fun pruneDeliveries()

    @Query("SELECT messages.*, deliveries.deliveredAt AS deliveredAt FROM deliveries INNER JOIN messages ON messages.id = deliveries.messageId ORDER BY deliveries.deliveredAt DESC, deliveries.id DESC LIMIT 5")
    abstract fun recent(): Flow<List<RecentDelivery>>

    /** One real delivery: counts it and remembers it, together or not at all. */
    @Transaction
    open suspend fun recordDelivery(id: Long, at: Long) {
        markShown(id, at)
        insertDelivery(Delivery(messageId = id, deliveredAt = at))
        pruneDeliveries()
    }

    @Insert abstract suspend fun insertMessages(m: List<Message>)
    @Update abstract suspend fun updateMessage(m: Message)
    @Delete abstract suspend fun deleteMessage(m: Message)

    @Query("UPDATE messages SET shown = shown + 1, lastShownAt = :t WHERE id = :id")
    abstract suspend fun markShown(id: Long, t: Long)

    // Rhythms
    @Query("SELECT * FROM rhythms ORDER BY id")
    abstract fun rhythms(): Flow<List<Rhythm>>

    @Query("SELECT * FROM rhythms ORDER BY id")
    abstract suspend fun rhythmsOnce(): List<Rhythm>

    @Query("SELECT * FROM rhythms WHERE id = :id")
    abstract suspend fun rhythm(id: Long): Rhythm?

    @Query("SELECT * FROM rhythm_folders")
    abstract fun links(): Flow<List<RhythmFolder>>

    @Query("SELECT folderId FROM rhythm_folders WHERE rhythmId = :rhythmId")
    abstract suspend fun folderIdsFor(rhythmId: Long): List<Long>

    /** Rhythms whose only source is this folder; they must be paused if it is deleted. */
    @Query("SELECT rhythmId FROM rhythm_folders GROUP BY rhythmId HAVING COUNT(*) = 1 AND MAX(folderId) = :folderId")
    abstract suspend fun onlyUsing(folderId: Long): List<Long>

    @Insert abstract suspend fun insertRhythm(r: Rhythm): Long
    @Update abstract suspend fun updateRhythm(r: Rhythm)
    @Delete abstract suspend fun deleteRhythm(r: Rhythm)

    @Query("DELETE FROM rhythm_folders WHERE rhythmId = :rhythmId")
    abstract suspend fun clearLinks(rhythmId: Long)

    @Insert abstract suspend fun insertLinks(l: List<RhythmFolder>)

    @Query("UPDATE rhythms SET enabled = :on WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, on: Boolean)

    @Query("UPDATE rhythms SET cursor = :cursor, bag = :bag, lastFiredAt = :t WHERE id = :id")
    abstract suspend fun saveProgress(id: Long, cursor: Int, bag: String, t: Long)

    @Query("UPDATE rhythms SET lastFiredAt = :t WHERE id = :id")
    abstract suspend fun saveFired(id: Long, t: Long)

    @Query("UPDATE rhythms SET nextAt = :t WHERE id = :id")
    abstract suspend fun saveNext(id: Long, t: Long)

    @Transaction
    open suspend fun saveRhythm(r: Rhythm, folderIds: List<Long>): Long {
        val id = if (r.id == 0L) insertRhythm(r) else { updateRhythm(r); r.id }
        clearLinks(id)
        if (folderIds.isNotEmpty()) insertLinks(folderIds.map { RhythmFolder(id, it) })
        return id
    }
}

@Database(
    entities = [Folder::class, Message::class, Rhythm::class, RhythmFolder::class, Delivery::class],
    version = 2,
    exportSchema = true,
    // Version 1 -> 2 only adds columns (with defaults) and one table, so Room can migrate by itself.
    // It checks this against schemas/app.notify.data.Db/1.json at build time.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): BankDao

    companion object {
        @Volatile private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, Db::class.java, "notify.db")
                .build()
                .also { instance = it }
        }
    }
}
