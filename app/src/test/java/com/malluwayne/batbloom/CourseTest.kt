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

    @Test fun remindAgainRule() {
        val due = d(12, 1).atTime(8, 0).atZone(zone)
        // A follow-up 30 minutes on, well inside the 3-hour window
        assertEquals(due.plusMinutes(40), Doses.remindAgainAt(due, due.plusMinutes(10), 30))
        // Exactly at the window's end still reminds; past it, stop
        assertEquals(due.plusHours(3), Doses.remindAgainAt(due, due.plusMinutes(150), 30))
        assertNull(Doses.remindAgainAt(due, due.plusMinutes(151), 30))
        // Swiped long after: nothing more
        assertNull(Doses.remindAgainAt(due, due.plusHours(5), 10))
        // Varenicline: next dose at 10:00, skip window 4 hours -> no reminder once inside it
        val next = due.plusHours(10)
        assertEquals(due.plusMinutes(30), Doses.remindAgainAt(due, due, 30, next, 4))
        val lateDue = d(12, 1).atTime(20, 0).atZone(zone)
        val nextAm = d(12, 2).atTime(8, 0).atZone(zone)
        assertNull(Doses.remindAgainAt(lateDue, lateDue.plusHours(9), 10, nextAm, 4, windowHours = 12))
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
        assertEquals(setOf("course_start"), Badges.earned(d(11, 27), s, quit))
        assertEquals(emptySet<String>(), Badges.earned(d(11, 20), s, quit))
        assertEquals(setOf("course_start", "quit_day"), Badges.earned(quit, s, quit))
        assertEquals(setOf("course_start", "quit_day", "day3", "week1"),Badges.earned(d(12, 5), s, quit))
        assertTrue("month1" in Badges.earned(d(12, 28), s, quit))
        assertEquals(setOf("bloom1", "bloom10", "streak3", "streak7"), Badges.earned(d(11, 20), s, quit, blooms = 12, streak = 8))
        assertTrue("day3" in Badges.earned(quit.plusDays(3), s, quit))
        assertTrue("year1" !in Badges.earned(quit.plusDays(364), s, quit))
        assertTrue("year1" in Badges.earned(quit.plusDays(365), s, quit))
        val f = com.malluwayne.batbloom.quit.FocusFacts(focusMinutes = 600, longestMinutes = 125, nightOwl = 5, fmgeMinutes = 59)
        val e = Badges.earned(d(11, 20), s, quit, facts = f)
        assertTrue("hours10" in e && "marathon" in e && "night_owl" in e)
        assertTrue("fmge10" !in e && "early_bird" !in e)
        assertEquals(10 to 10, Badges.progress("hours10", d(11, 20), s, quit, f))
        assertEquals(7 to 14, Badges.progress("week2", quit.plusDays(7), s, quit, f))
        assertEquals(Badges.all.size, Badges.all.map { it.id }.toSet().size)

        // Dose streaks: every slot of the day must be taken. Days 1-3 have one dose, day 4 on have two.
        val day = { n: Int -> s.start.plusDays(n - 1L) }
        val takenAll = (1..5).flatMap { s.slotsOn(day(it)) }.map { it.key }.toSet()
        assertEquals(5, Badges.bestDoseStreak(s, takenAll, day(5)))
        val missedPm4 = takenAll - "${day(4)}-PM"
        assertEquals(3, Badges.bestDoseStreak(s, missedPm4, day(5)))
        // Today not finished yet doesn't break the run.
        assertEquals(4, Badges.bestDoseStreak(s, takenAll - "${day(5)}-PM", day(5)))
        val g = Badges.earned(d(11, 20), s, quit, facts = com.malluwayne.batbloom.quit.FocusFacts(doseStreak = 7, maxBloomsInDay = 3))
        assertTrue("dose3" in g && "dose7" in g && "hat_trick" in g && "dose21" !in g && "dose_all" !in g)
        assertTrue("dose_all" in Badges.earned(d(11, 20), s, quit, facts = com.malluwayne.batbloom.quit.FocusFacts(doseStreak = 84)))
        assertTrue(Badges.byId("night_owl").secret && !Badges.byId("week1").secret)
        assertEquals(d(12, 5), Badges.dueDate("week1", s, quit))
        assertEquals(d(2, 12), Badges.dueDate("course", s, quit))
        assertEquals(10, Badges.target("bloom10"))
        assertTrue("halfway" in Badges.earned(s.start.plusDays(41), s, quit))
        assertTrue("course" in Badges.earned(d(2, 12), s, quit))
    }

    @Test fun takenAtPickedTime() {
        val z = ZoneId.of("Asia/Kolkata")
        val pm = d(11, 24).atTime(20, 0).atZone(z)
        val now = d(11, 25).atTime(9, 0).atZone(z)
        // Evening dose, picked 9:30 PM -> same day
        assertEquals(d(11, 24).atTime(21, 30).atZone(z), Doses.takenAt(pm, java.time.LocalTime.of(21, 30), now))
        // Picked 12:30 AM -> it was after midnight, so the next day
        assertEquals(d(11, 25).atTime(0, 30).atZone(z), Doses.takenAt(pm, java.time.LocalTime.of(0, 30), now))
        // Taken early: 5 PM the same day
        assertEquals(d(11, 24).atTime(17, 0).atZone(z), Doses.takenAt(pm, java.time.LocalTime.of(17, 0), now))
        // Never in the future: picked 10 PM while it's only 8:15 PM -> yesterday's 10 PM is too far, so today 10 PM is excluded
        val early = d(11, 24).atTime(20, 15).atZone(z)
        assertTrue(!Doses.takenAt(pm, java.time.LocalTime.of(22, 0), early).isAfter(early))
    }

    @Test fun bestStreak() {
        val days = setOf(d(11, 1), d(11, 2), d(11, 3), d(11, 10), d(11, 11))
        assertEquals(3, com.malluwayne.batbloom.stats.Stats.bestRun(days))
        assertEquals(0, com.malluwayne.batbloom.stats.Stats.bestRun(emptySet()))
    }
}
