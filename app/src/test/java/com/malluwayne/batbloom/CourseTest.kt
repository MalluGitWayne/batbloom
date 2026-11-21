package com.malluwayne.batbloom

import com.malluwayne.batbloom.quit.Badges
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.quit.Doses
import com.malluwayne.batbloom.quit.Milestones
import com.malluwayne.batbloom.quit.QuitMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class CourseTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val s = DoseSchedule() // the user's plan
    private fun d(m: Int, day: Int) = LocalDate.of(if (m < 6) 2027 else 2026, m, day)

    @Test fun defaultPlanMatchesPrescription() {
        assertEquals(1, s.dayNumber(d(11, 21)))
        assertEquals(8, s.dayNumber(d(11, 28)))
        assertEquals(d(2, 12), s.endDate)
        assertEquals(84, s.dayNumber(d(2, 12)))
        assertEquals(0.5 to 0.0, s.dosesOnDay(1))
        assertEquals(0.5 to 0.0, s.dosesOnDay(3))
        assertEquals(0.5 to 0.5, s.dosesOnDay(4))
        assertEquals(0.5 to 0.5, s.dosesOnDay(7))
        assertEquals(1.0 to 1.0, s.dosesOnDay(8))
        assertEquals(1.0 to 1.0, s.dosesOnDay(84))
        assertEquals(0.0 to 0.0, s.dosesOnDay(85))
        assertEquals(0.0 to 0.0, s.dosesOnDay(0))
    }

    @Test fun slotsAndNextDose() {
        assertEquals(1, s.slotsOn(d(11, 21)).size)
        assertEquals(2, s.slotsOn(d(11, 24)).size)
        assertEquals("2026-11-21-AM", s.slotsOn(d(11, 21)).first().key)
        // Day 1 after the morning dose: next is Day 2 morning (no evening dose on days 1-3)
        val afterAm = d(11, 21).atTime(9, 0).atZone(zone)
        val next = s.nextSlot(afterAm)!!
        assertEquals(d(11, 22), next.date)
        assertTrue(next.morning)
        // Day 4 midday: next is the evening dose
        val n2 = s.nextSlot(d(11, 24).atTime(12, 0).atZone(zone))!!
        assertFalse(n2.morning)
        assertEquals(0.5, n2.mg, 0.0)
        assertNull(s.nextSlot(d(2, 12).atTime(21, 0).atZone(zone)))
        // 3 + 4*2 + 77*2 doses
        assertEquals(3 + 8 + 154, s.slotsFrom(d(11, 1)).size)
    }

    @Test fun tabletsAndStock() {
        assertEquals(2.0, Doses.tablets(1.0, 0.5), 0.0)
        assertEquals(1.0, Doses.tablets(1.0, 1.0), 0.0)
        assertEquals("2 tablets", Doses.formatTablets(2.0))
        assertEquals("0.5 mg", Doses.formatMg(0.5))
        // Day 8 onwards with 1 mg tablets: 2 a day. 9 tablets cover 4 days and half of day 5.
        val today = d(11, 28)
        val st = Doses.stock(s, 9.0, 1.0, today)
        assertEquals(d(12, 2), st.runsOutOn)
        assertEquals(d(12, 2), st.coveredUntil) // the morning of day 5 is covered
        assertEquals(4L, st.daysLeft)
        assertTrue(Doses.lowStock(st))
        val plenty = Doses.stock(s, 56.0, 1.0, today)
        assertFalse(Doses.lowStock(plenty))
        // Enough for the whole course from Day 8: 77 days × 2
        assertNull(Doses.stock(s, 154.0, 1.0, today).runsOutOn)
        // Doses already logged today are not counted again
        val skipToday = Doses.stock(s, 2.0, 1.0, today, setOf("2026-11-28-AM", "2026-11-28-PM"))
        assertEquals(d(11, 29), skipToday.coveredUntil)
    }

    @Test fun missedDoseRule() {
        val now = d(12, 1).atTime(15, 0).atZone(zone)
        val nextPm = d(12, 1).atTime(20, 0).atZone(zone)
        assertEquals(Doses.Missed.TakeNow, Doses.missedAdvice(now, nextPm, 4))
        assertEquals(Doses.Missed.Skip, Doses.missedAdvice(now.plusHours(2), nextPm, 4))
        assertEquals(Doses.Missed.TakeNow, Doses.missedAdvice(now, null, 4))
    }

    @Test fun quitProgress() {
        val quit = d(11, 28).atStartOfDay(zone)
        val p = QuitMath.progress(quit, quit.plusDays(3), perDay = 10, packSize = 20, packPrice = 340.0)
        assertEquals(Duration.ofDays(3), p.sinceQuit)
        assertEquals(30, p.notSmoked)
        assertEquals(30 / 20.0 * 340.0, p.moneySaved, 0.001)
        assertEquals(Duration.ofMinutes(30 * QuitMath.MINUTES_PER_CIGARETTE), p.lifeRegained)
        val before = QuitMath.progress(quit, quit.minusDays(1), 10, 20, 340.0)
        assertEquals(0, before.notSmoked)
        assertEquals("3d 0h", QuitMath.formatDuration(p.sinceQuit))
    }

    @Test fun milestones() {
        val first = Milestones.all.first()
        assertEquals(0.5f, Milestones.progress(first, Duration.ofMinutes(10)), 0.001f)
        assertEquals(1f, Milestones.progress(first, Duration.ofDays(2)), 0f)
        assertEquals(0f, Milestones.progress(first, Duration.ofMinutes(-5)), 0f)
        assertTrue(Milestones.all.zipWithNext().all { (a, b) -> a.after < b.after })
    }

    @Test fun badges() {
        val quit = d(11, 28)
        assertEquals(emptySet<String>(), Badges.earned(d(11, 27), s, quit))
        assertEquals(setOf("quit_day"), Badges.earned(quit, s, quit))
        assertEquals(setOf("quit_day", "week1"), Badges.earned(d(12, 5), s, quit))
        assertTrue("halfway" in Badges.earned(s.start.plusDays(41), s, quit))
        assertTrue("course" in Badges.earned(d(2, 12), s, quit))
    }
}
