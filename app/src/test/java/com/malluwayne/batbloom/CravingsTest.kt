package com.malluwayne.batbloom

import com.malluwayne.batbloom.quit.Craving
import com.malluwayne.batbloom.quit.Cravings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CravingsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 26)
    private fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun roundTrip() {
        val c = Craving(123L, "After food")
        assertEquals(c, Cravings.parse(Cravings.format(c)))
        assertEquals(Craving(5L, ""), Cravings.parse("5|"))
        assertEquals(Craving(5L, ""), Cravings.parse("5"))
        assertNull(Cravings.parse("x|Stress"))
        assertEquals("a b", Cravings.parse(Cravings.format(Craving(1, "a|b")))!!.trigger)
    }

    @Test fun perDayAndTrigger() {
        val all = listOf(Craving(at(0, 9), "Coffee"), Craving(at(0, 18), "Stress"), Craving(at(2, 10), "Stress"), Craving(at(20, 10), "Coffee"))
        val days = Cravings.perDay(all, today, zone)
        assertEquals(14, days.size)
        assertEquals(2, days.last())
        assertEquals(1, days[11])
        assertEquals(3, days.sum()) // 20 days ago is outside the window
        assertEquals("Stress", Cravings.topTrigger(all.take(3)))
        // Tie: the one named most recently wins
        assertEquals("Stress", Cravings.topTrigger(listOf(Craving(at(3, 9), "Coffee"), Craving(at(0, 9), "Stress"))))
        assertNull(Cravings.topTrigger(listOf(Craving(1, ""))))
    }
}
