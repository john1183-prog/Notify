package app.notify.engine

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val zone = ZoneId.of("Africa/Lagos")

    /** 1 Oct 2026 is a Thursday. */
    private fun at(h: Int, m: Int = 0, d: Int = 1): ZonedDateTime = ZonedDateTime.of(2026, 10, d, h, m, 0, 0, zone)

    private val fixed = Plan(true, 0, 0, 0, listOf(480, 1200), 127)
    private val daytime = Plan(false, 540, 1260, 3, emptyList(), 127)   // 09:00-21:00, three slices
    private val overnight = Plan(false, 1320, 300, 3, emptyList(), 127) // 22:00-05:00, three slices

    @Test fun fixedBeforeFirstTimeFiresFirstTimeToday() = assertEquals(at(8), nextFire(fixed, at(6), 0, Random(1)))
    @Test fun fixedBetweenTimesFiresSecondTime() = assertEquals(at(20), nextFire(fixed, at(9), 0, Random(1)))
    @Test fun fixedAfterLastTimeRollsToTomorrow() = assertEquals(at(8, 0, 2), nextFire(fixed, at(21), 0, Random(1)))
    @Test fun fixedIsStrictlyAfter() = assertEquals(at(20), nextFire(fixed, at(8), 0, Random(1)))

    @Test fun weekdaysSkipTheWeekend() {
        val weekdays = fixed.copy(days = 62)
        assertEquals(at(8, 0, 5), nextFire(weekdays, at(21, 0, 2), 0, Random(1))) // Friday night -> Monday
    }

    @Test fun noDaysOrNoTimesMeansNothingScheduled() {
        assertNull(nextFire(fixed.copy(days = 0), at(6), 0, Random(1)))
        assertNull(nextFire(fixed.copy(times = emptyList()), at(6), 0, Random(1)))
    }

    @Test fun randomFromEarlyMorningLandsInFirstSlice() {
        repeat(300) { seed ->
            val n = nextFire(daytime, at(7), 0, Random(seed))!!
            assertTrue(n.dayOfMonth == 1 && n.hour in 9..12)
        }
    }

    @Test fun randomFiresExactlyPerDayInOrder() {
        var now = at(7)
        var last = 0L
        val fires = mutableListOf<ZonedDateTime>()
        repeat(9) {
            val n = nextFire(daytime, now, last, Random(it + 100))!!
            fires += n
            last = n.toInstant().toEpochMilli()
            now = n
        }
        assertEquals(listOf(3, 3, 3), fires.groupBy { it.dayOfMonth }.values.map { it.size })
        assertTrue(fires.zipWithNext().all { (a, b) -> b.isAfter(a) })
        assertTrue(fires.all { it.hour in 9..20 })
    }

    @Test fun randomLateInTheDayUsesTheRemainingSlice() {
        val n = nextFire(daytime, at(18), 0, Random(1))!!
        assertTrue(n.isAfter(at(18)) && n.isBefore(at(21)))
    }

    @Test fun randomAfterTheWindowRollsToTomorrow() {
        val n = nextFire(daytime, at(21, 30), 0, Random(1))!!
        assertTrue(n.dayOfMonth == 2 && n.hour in 9..12)
    }

    @Test fun windowMinutesHandlesMidnight() {
        assertEquals(720, windowMinutes(540, 1260))
        assertEquals(420, windowMinutes(1320, 300))
    }

    @Test fun overnightWindowStartingTonight() {
        repeat(200) { seed ->
            val n = nextFire(overnight, at(21), 0, Random(seed))!!
            assertTrue(n.isAfter(at(22).minusSeconds(1)) && n.isBefore(at(0, 20, 2)))
        }
    }

    @Test fun overnightWindowStillOpenAfterMidnight() {
        // 01:00 is inside the window that started yesterday at 22:00.
        repeat(200) { seed ->
            val n = nextFire(overnight, at(1), 0, Random(seed))!!
            assertTrue(n.isAfter(at(1, 1)) && n.isBefore(at(2, 40).plusSeconds(1)))
        }
    }

    @Test fun overnightDoesNotRepeatASliceAlreadyFired() {
        val lastFired = at(1, 30).toInstant().toEpochMilli()
        val n = nextFire(overnight, at(1, 30), lastFired, Random(3))!!
        assertTrue(n.isAfter(at(2, 39)) && n.isBefore(at(5, 1)))
    }

    @Test fun overnightBelongsToTheDayItStarts() {
        val weekdays = overnight.copy(days = 62)
        // Saturday 01:00 is still Friday's window, which is allowed.
        val n = nextFire(weekdays, at(1, 0, 3), 0, Random(5))!!
        assertTrue(n.dayOfMonth == 3 && n.hour < 5)
    }

    @Test fun inOrderCyclesThroughThePool() {
        val pool = listOf(10L, 11L, 12L)
        var cursor = 0
        val seen = mutableListOf<Long>()
        repeat(7) {
            val p = pick(pool, false, cursor, emptyList(), Random(1))!!
            seen += p.id
            cursor = p.cursor
        }
        assertEquals(listOf(10L, 11L, 12L, 10L, 11L, 12L, 10L), seen)
    }

    @Test fun shuffleShowsEveryWordBeforeRepeating() {
        val pool = listOf(10L, 11L, 12L)
        var bag = emptyList<Long>()
        val drawn = mutableListOf<Long>()
        repeat(9) {
            val p = pick(pool, true, 0, bag, Random(it))!!
            drawn += p.id
            bag = p.bag
        }
        assertTrue(drawn.chunked(3).all { it.toSet() == pool.toSet() })
    }

    @Test fun shuffleIgnoresDeletedWords() {
        assertEquals(2L, pick(listOf(1L, 2L), true, 0, listOf(99L, 2L), Random(1))!!.id)
    }

    @Test fun emptyPoolPicksNothing() = assertNull(pick(emptyList(), true, 0, emptyList(), Random(1)))

    @Test fun timesParseAndJoinCleanly() {
        assertEquals("480,1200", joinTimes(parseTimes("1200, 480,480,5000,x")))
        assertNotNull(parseIds("1,2,x,3"))
        assertEquals(listOf(1L, 2L, 3L), parseIds("1,2,x,3"))
    }

    @Test fun freshFavoursWordsDeliveredLeast() {
        val pool = listOf(1L to 0, 2L to 10)
        val rnd = Random(11)
        val firstShare = (1..2000).count { pickFresh(pool, 0L, rnd) == 1L } / 2000.0
        assertTrue(firstShare > 0.95)
    }

    @Test fun freshNeverRepeatsTheLastWordWhenThereIsChoice() {
        val pool = listOf(1L to 0, 2L to 0, 3L to 0)
        val rnd = Random(5)
        repeat(300) { assertTrue(pickFresh(pool, 2L, rnd) != 2L) }
    }

    @Test fun freshKeepsTheOnlyWordEvenIfItWasLast() {
        assertEquals(7L, pickFresh(listOf(7L to 3), 7L, Random(1)))
    }

    @Test fun freshWithNothingPicksNothing() = assertNull(pickFresh(emptyList(), 0L, Random(1)))

    @Test fun freshStillReachesEveryWord() {
        val pool = listOf(1L to 0, 2L to 4, 3L to 9)
        val rnd = Random(21)
        val seen = (1..5000).mapNotNull { pickFresh(pool, 0L, rnd) }.toSet()
        assertEquals(setOf(1L, 2L, 3L), seen)
    }
}
