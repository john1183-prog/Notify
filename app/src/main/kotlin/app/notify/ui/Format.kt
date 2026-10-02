package app.notify.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

fun hhmm(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

fun joinList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

fun daysText(days: Int): String = when (days and 127) {
    127 -> "every day"
    62 -> "weekdays"
    65 -> "weekends"
    else -> listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        .filterIndexed { i, _ -> (days and (1 shl i)) != 0 }
        .joinToString(", ")
}

fun deliveredText(n: Int): String = when (n) {
    0 -> "Not delivered yet"
    1 -> "Delivered once"
    else -> "Delivered $n times"
}

/** How deep a word's colour is: 0 (pale) to 1 (fully soaked after eight deliveries). */
fun soakOf(shown: Int): Float = (shown / 8f).coerceIn(0f, 1f)

/**
 * Fixed times are shown exactly. Random moments are shown only as part of the day,
 * because knowing the exact minute would spoil the point of a random arrival.
 */
fun whenText(at: Long, now: Long, exact: Boolean): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val n = Instant.ofEpochMilli(now).atZone(zone)
    val days = ChronoUnit.DAYS.between(n.toLocalDate(), t.toLocalDate())
    val weekday = t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    if (exact) {
        val clock = hhmm(t.hour * 60 + t.minute)
        return when (days) {
            0L -> "Today at $clock"
            1L -> "Tomorrow at $clock"
            else -> "$weekday at $clock"
        }
    }
    val part = when {
        t.hour < 12 -> "morning"
        t.hour < 17 -> "afternoon"
        t.hour < 21 -> "evening"
        else -> "night"
    }
    return when (days) {
        0L -> if (part == "night") "Tonight" else "This $part"
        1L -> "Tomorrow $part"
        else -> "$weekday $part"
    }
}

fun untilText(at: Long, now: Long): String {
    val m = ((at - now) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "any moment now"
        m < 60 -> "in $m min"
        m < 1440 -> "in ${m / 60} h ${m % 60} min"
        else -> "in ${m / 1440} d"
    }
}
