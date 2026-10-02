package app.notify.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.random.Random

/*
 * Pure scheduling and picking logic. No Android imports on purpose:
 * it can be unit-tested on any JVM and reused if the app ever grows a second target.
 */

/** What a rhythm asks for. Days is a bitmask: bit 0 = Sunday ... bit 6 = Saturday. */
data class Plan(
    val fixed: Boolean,
    val windowStart: Int,   // minutes since midnight
    val windowEnd: Int,     // minutes since midnight
    val perDay: Int,
    val times: List<Int>,   // minutes since midnight, used when fixed
    val days: Int,
)

fun parseTimes(s: String): List<Int> =
    s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..1439 }.distinct().sorted()

fun joinTimes(l: List<Int>): String = l.distinct().sorted().joinToString(",")

fun parseIds(s: String): List<Long> =
    if (s.isEmpty()) emptyList() else s.split(',').mapNotNull { it.toLongOrNull() }

fun dayAllowed(days: Int, d: LocalDate): Boolean =
    (days and (1 shl (d.dayOfWeek.value % 7))) != 0

/**
 * Next moment this plan should fire, strictly after [after].
 *
 * Fixed plans return the next listed clock time on an allowed day.
 * Random plans split the window into [Plan.perDay] equal slices and fire once at a random
 * moment inside each slice. That keeps arrivals unpredictable but evenly spread through the day.
 * [lastFiredMs] stops a slice from firing twice.
 */
fun nextFire(p: Plan, after: ZonedDateTime, lastFiredMs: Long, rnd: Random): ZonedDateTime? {
    if ((p.days and 127) == 0) return null
    if (p.fixed && p.times.isEmpty()) return null
    val zone = after.zone
    val afterSec = after.toEpochSecond()
    val lastSec = lastFiredMs / 1000
    for (offset in 0..7) {
        val date = after.toLocalDate().plusDays(offset.toLong())
        if (!dayAllowed(p.days, date)) continue
        if (p.fixed) {
            for (t in p.times) {
                val at = date.atTime(t / 60, t % 60).atZone(zone)
                if (at.toEpochSecond() > afterSec) return at
            }
        } else {
            val n = p.perDay.coerceIn(1, 24)
            val base = date.atTime(p.windowStart / 60, p.windowStart % 60).atZone(zone).toEpochSecond()
            val len = (p.windowEnd - p.windowStart).coerceAtLeast(1) * 60L
            for (i in 0 until n) {
                val s = base + len * i / n
                val e = base + len * (i + 1) / n
                if (s <= lastSec || e <= afterSec + 60) continue
                val lo = maxOf(s, afterSec + 60)
                val t = lo + rnd.nextLong(e - lo)
                return Instant.ofEpochSecond(t).atZone(zone)
            }
        }
    }
    return null
}

data class Pick(val id: Long, val cursor: Int, val bag: List<Long>)

/**
 * Chooses the next word from [pool] (ordered list of message ids).
 * In order: walks the pool using [cursor]. Shuffled: draws from a "bag" so every word
 * appears once before any repeats. Words added later join the bag at the next refill.
 */
fun pick(pool: List<Long>, shuffle: Boolean, cursor: Int, bag: List<Long>, rnd: Random): Pick? {
    if (pool.isEmpty()) return null
    if (!shuffle) {
        val i = ((cursor % pool.size) + pool.size) % pool.size
        return Pick(pool[i], (i + 1) % pool.size, bag)
    }
    val live = pool.toHashSet()
    var b = bag.filter { it in live }
    if (b.isEmpty()) b = pool.shuffled(rnd)
    return Pick(b.first(), cursor, b.drop(1))
}
