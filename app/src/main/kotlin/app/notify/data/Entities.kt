package app.notify.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A named collection of words. [hue] indexes the palette's pigment list. */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hue: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One word: a verse, a quote, a lesson. [shown] counts how many times it has been delivered.
 * A [known] word rests: it is kept but no longer delivered. [position] orders words inside a folder.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(Folder::class, ["id"], ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folderId")],
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val text: String,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val shown: Int = 0,
    val lastShownAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val known: Boolean = false,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

/**
 * A rule for when words arrive. Reminders are just rhythms pointed at a folder of one word.
 * Times are minutes since midnight; [days] is a bitmask (bit 0 = Sunday).
 * [cursor] and [bag] hold delivery progress; [nextAt] is the scheduled alarm (epoch ms, 0 = none).
 * With [shuffle] on, [smart] favours the words delivered least; [silent] posts without sound.
 */
@Entity(tableName = "rhythms")
data class Rhythm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val shuffle: Boolean = true,
    val fixedTimes: Boolean = false,
    val windowStart: Int = 480,
    val windowEnd: Int = 1260,
    val perDay: Int = 3,
    val times: String = "480,1200",
    val days: Int = 127,
    val cursor: Int = 0,
    val bag: String = "",
    val lastFiredAt: Long = 0,
    val nextAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val smart: Boolean = false,
    @ColumnInfo(defaultValue = "0") val silent: Boolean = false,
)

/** Which folders a rhythm draws from. No rows for a rhythm means the whole bank. */
@Entity(
    tableName = "rhythm_folders",
    primaryKeys = ["rhythmId", "folderId"],
    foreignKeys = [
        ForeignKey(Rhythm::class, ["id"], ["rhythmId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Folder::class, ["id"], ["folderId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("folderId")],
)
data class RhythmFolder(val rhythmId: Long, val folderId: Long)

data class FolderCount(val folderId: Long, val n: Int)

/** One real delivery, kept so Today can show what arrived even if the notification was dismissed. */
@Entity(
    tableName = "deliveries",
    foreignKeys = [ForeignKey(Message::class, ["id"], ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("messageId"), Index("deliveredAt")],
)
data class Delivery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val deliveredAt: Long,
)

data class RecentDelivery(@Embedded val message: Message, val deliveredAt: Long)

data class ShownRow(val folderId: Long, val shown: Int, val known: Boolean)

data class PoolRow(val id: Long, val shown: Int)
