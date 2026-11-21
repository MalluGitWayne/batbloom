package com.malluwayne.batbloom

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.stats.SmokingProfile
import com.malluwayne.batbloom.stats.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 25) // a Wednesday

    private fun at(date: LocalDate, hour: Int = 10) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun s(
        date: LocalDate, minutes: Int, tag: String = Tags.STUDY, ok: Boolean = true, subject: String? = null,
        intensity: Int? = null, trigger: String? = null,
    ) = SessionEntity(
        startedAt = at(date), plannedMinutes = minutes, tag = tag, subject = subject, plantId = "moonflower",
        status = if (ok) Status.DONE else Status.FAILED, cravingIntensity = intensity, cravingTrigger = trigger,
    )

    @Test fun totalsWeekAndStreak() {
        val list = listOf(
            s(today, 25), s(today, 50, Tags.FMGE, subject = "Pathology"),
            s(today.minusDays(1), 30), s(today.minusDays(2), 10),
            s(today.minusDays(4), 60), // breaks the streak (day -3 empty)
            s(today.minusDays(10), 120), // last week
            s(today, 45, ok = false), // failed: no minutes
        )
        val st = Stats.compute(list, today, zone)
        assertEquals(75, st.todayMinutes)
        // Week starts Monday 23 Nov: today, -1 (Tue), -2 (Mon)
        assertEquals(75 + 30 + 10, st.weekMinutes)
        assertEquals(295, st.allMinutes)
        assertEquals(3, st.streakDays)
        assertEquals(6, st.completed)
        assertEquals(1, st.failed)
        assertEquals(86, st.successRate)
        assertEquals(50, st.fmgeTodayMinutes)
        assertEquals("Pathology" to 50, st.subjectMinutes.first())
        assertEquals(Tags.fmgeSubjects.size, st.subjectMinutes.size)
        assertEquals(7, st.last7.size)
        assertEquals(today to 75, st.last7.last())
    }

    @Test fun streakCountsFromYesterdayWhenTodayIsEmpty() {
        val st = Stats.compute(listOf(s(today.minusDays(1), 25), s(today.minusDays(2), 25)), today, zone)
        assertEquals(2, st.streakDays)
    }

    @Test fun quitAndExamLines() {
        val quit = LocalDate.of(2026, 11, 28)
        assertEquals("Quit day in 7 days", Stats.quitLine(quit, LocalDate.of(2026, 11, 21)))
        assertEquals("Quit day is tomorrow", Stats.quitLine(quit, LocalDate.of(2026, 11, 27)))
        assertEquals("Quit day is today. You've got this", Stats.quitLine(quit, quit))
        assertEquals("Smoke-free day 2", Stats.quitLine(quit, LocalDate.of(2026, 11, 29)))
        assertEquals("Smoke-free day 3", Stats.quitLine(quit, LocalDate.of(2026, 11, 30)))
        assertEquals("FMGE in 10 days", Stats.examLine(today.plusDays(10), today))
    }

    @Test fun smokeFreeMoney() {
        val quit = LocalDate.of(2026, 11, 28)
        val p = SmokingProfile(perDay = 10, packSize = 20, packPrice = 340.0)
        val twoDays = quit.atStartOfDay(zone).toInstant().toEpochMilli() + 2 * 86_400_000L
        val sf = Stats.smokeFree(quit, twoDays, zone, p)
        assertEquals(20, sf.notSmoked)
        assertEquals(340.0, sf.moneySaved, 0.001)
        val before = Stats.smokeFree(quit, at(LocalDate.of(2026, 11, 21)), zone, p)
        assertEquals(0, before.notSmoked)
    }

    @Test fun cravingSummary() {
        val list = listOf(
            s(today, 10, Tags.CRAVING, intensity = 4, trigger = "Coffee/tea"),
            s(today, 10, Tags.CRAVING, intensity = 2, trigger = "Coffee/tea"),
            s(today.minusDays(1), 10, Tags.CRAVING, ok = false, trigger = "Stress"),
            s(today, 25),
        )
        val c = Stats.cravings(list, today, zone)
        assertEquals(2, c.beaten)
        assertEquals(1, c.lost)
        assertEquals(3.0, c.intensity7.last().second!!, 0.001)
        assertNull(c.intensity7[5].second)
        assertEquals("Coffee/tea" to 2, c.topTriggers.first())
    }
}
