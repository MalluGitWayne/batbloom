package com.malluwayne.batbloom

import com.malluwayne.batbloom.data.AllowedAppEntity
import com.malluwayne.batbloom.data.Backup
import com.malluwayne.batbloom.data.BackupData
import com.malluwayne.batbloom.data.BadgeEntity
import com.malluwayne.batbloom.data.DoseLogEntity
import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.SideEffectEntity
import com.malluwayne.batbloom.data.SlipEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.data.UnlockEntity
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.guard.TimeGuard
import com.malluwayne.batbloom.stats.Revision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FeaturesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 12, 10)

    private fun study(subject: String, daysAgo: Long, ok: Boolean = true) = SessionEntity(
        startedAt = today.minusDays(daysAgo).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
        plannedMinutes = 50, tag = Tags.FMGE, subject = subject, plantId = "moonflower",
        status = if (ok) Status.DONE else Status.FAILED,
    )

    @Test fun revisionSteps() {
        // Studied once yesterday: due today (+1).
        assertEquals(listOf("Anatomy"), Revision.due(listOf(study("Anatomy", 1)), today, zone).map { it.subject })
        // Two study days, last 2 days ago: next interval is 3 days, not due yet.
        assertTrue(Revision.due(listOf(study("Anatomy", 5), study("Anatomy", 2)), today, zone).isEmpty())
        // ...but due once 3 days have passed (and overdue after).
        val due = Revision.due(listOf(study("Anatomy", 8), study("Anatomy", 4)), today, zone).single()
        assertEquals(1L, due.overdueDays(today))
        // Studied today: not due. Failed sessions don't count.
        assertTrue(Revision.due(listOf(study("Anatomy", 1), study("Anatomy", 0)), today, zone).isEmpty())
        assertTrue(Revision.due(listOf(study("Anatomy", 1, ok = false)), today, zone).isEmpty())
        // After the 5th step it's every 30 days.
        val five = (0 until 5).map { study("Pathology", 200L - it * 30) }
        assertEquals(1, Revision.due(five, today, zone).size)
        // MCQ practice is tracked but never "due".
        assertTrue(Revision.due(listOf(study(Tags.MCQ, 1)), today, zone).isEmpty())
        assertEquals("Anatomy · yesterday", Revision.label(Revision.due(listOf(study("Anatomy", 1)), today, zone).first(), today))
    }

    @Test fun timeGuard() {
        val m = TimeGuard.parse("com.instagram.android=30;com.google.android.youtube=45;bad;x=;=5")
        assertEquals(mapOf("com.instagram.android" to 30, "com.google.android.youtube" to 45), m)
        assertEquals(m, TimeGuard.parse(TimeGuard.format(m)))
        assertEquals(0, TimeGuard.level(29, 30))
        assertEquals(1, TimeGuard.level(30, 30))
        assertEquals(2, TimeGuard.level(45, 30))
        assertTrue(TimeGuard.shouldRemind(1, 0))
        assertFalse(TimeGuard.shouldRemind(1, 1))
        assertTrue(TimeGuard.shouldRemind(2, 1))
    }

    @Test fun stopwatch() {
        assertFalse(Rules.stopwatchCounts(9 * 60_000L + 59_000))
        assertTrue(Rules.stopwatchCounts(10 * 60_000L))
        assertEquals(42, Rules.stopwatchMinutes(42 * 60_000L + 30_000))
        assertEquals(Rules.STOPWATCH_MAX_MINUTES, Rules.stopwatchMinutes(10 * 3600_000L))
        assertEquals(0.5f, Rules.stopwatchGrowth(30 * 60_000L), 0.001f)
        assertEquals(1f, Rules.stopwatchGrowth(5 * 3600_000L), 0f)
    }

    @Test fun backupRoundTrip() {
        val b = BackupData(
            sessions = listOf(
                study("Anatomy", 1).copy(id = 7, endedAt = 123L, coins = 55),
                SessionEntity(id = 8, startedAt = 5, plannedMinutes = 10, tag = Tags.CRAVING, plantId = "glowcap", status = Status.FAILED,
                    failReason = "Instagram", cravingIntensity = 4, cravingTrigger = "Stress", round = 2),
            ),
            unlocks = listOf(UnlockEntity("bat_orchid", 150, 99)),
            allowed = listOf(AllowedAppEntity("com.dictionary")),
            doses = listOf(DoseLogEntity("2026-11-21-AM", 1, 0.5, 1.0, "TAKEN", 1000)),
            slips = listOf(SlipEntity(1, 2000, 2, "party")),
            effects = listOf(SideEffectEntity(1, 3000, "Nausea", 2, null)),
            badges = listOf(BadgeEntity("quit_day", 4000, 50)),
            prefs = mapOf("plant" to "glowcap", "minutes" to 45, "tablet_mg" to 0.5f, "reminders" to true, "custom_tags" to setOf("Revision"), "big" to 5_000_000_000L),
        )
        val back = Backup.fromJson(Backup.toJson(b))
        assertEquals(b.sessions, back.sessions)
        assertEquals(b.unlocks, back.unlocks)
        assertEquals(b.allowed, back.allowed)
        assertEquals(b.doses, back.doses)
        assertEquals(b.slips, back.slips)
        assertEquals(b.effects, back.effects)
        assertEquals(b.badges, back.badges)
        assertEquals(b.prefs, back.prefs)
    }

    @Test(expected = Backup.BadBackup::class) fun rejectsOtherFiles() {
        Backup.fromJson("""{"hello": 1}""")
    }
}

class CalendarWidgetTest {
    @org.junit.Test fun monthTotals() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val today = java.time.LocalDate.of(2026, 11, 21)
        fun s(d: Int, min: Int, ok: Boolean = true, month: Int = 11) = com.malluwayne.batbloom.data.SessionEntity(
            startedAt = java.time.LocalDate.of(2026, month, d).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            plannedMinutes = min, tag = "Study", plantId = "moonflower",
            status = if (ok) com.malluwayne.batbloom.data.Status.DONE else com.malluwayne.batbloom.data.Status.FAILED,
        )
        val m = com.malluwayne.batbloom.widget.CalendarWidget.month(
            listOf(s(21, 25), s(21, 50), s(20, 30), s(3, 10), s(4, 60, ok = false), s(30, 99, month = 10)), today, zone,
        )
        org.junit.Assert.assertEquals(mapOf(21 to 75, 20 to 30, 3 to 10), m.minutesByDay)
        org.junit.Assert.assertEquals(3, m.bloomDays)
        org.junit.Assert.assertEquals(115, m.totalMinutes)
        org.junit.Assert.assertEquals(2, m.streak)
    }
}


class PeriodsTest {
    private val zone = java.time.ZoneId.of("Asia/Kolkata")
    private val today = java.time.LocalDate.of(2026, 11, 25)
    private fun s(daysAgo: Long, hour: Int, min: Int, ok: Boolean = true, tag: String = "Study") = com.malluwayne.batbloom.data.SessionEntity(
        startedAt = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        plannedMinutes = min, tag = tag, plantId = "moonflower",
        status = if (ok) com.malluwayne.batbloom.data.Status.DONE else com.malluwayne.batbloom.data.Status.FAILED,
    )

    @org.junit.Test fun weekSummary() {
        val list = listOf(
            s(0, 9, 30), s(0, 19, 60, tag = "FMGE"), s(1, 22, 25), s(3, 14, 25, ok = false),
            s(6, 8, 45), s(7, 9, 100), // 7 days ago is outside a 7-day week
        )
        val p = com.malluwayne.batbloom.stats.Periods.summary(list, today, zone, 7)
        org.junit.Assert.assertEquals(7, p.days.size)
        org.junit.Assert.assertEquals(today to 90, p.days.last())
        org.junit.Assert.assertEquals(160, p.totalMinutes)
        org.junit.Assert.assertEquals(22, p.avgPerDay)
        org.junit.Assert.assertEquals(4, p.sessions)
        org.junit.Assert.assertEquals(40, p.avgSession)
        org.junit.Assert.assertEquals(80, p.successRate)
        org.junit.Assert.assertEquals(listOf("Morning" to 75, "Evening" to 60, "Night" to 25), p.timeOfDay)
        org.junit.Assert.assertEquals(today to 90, p.bestDay)
        org.junit.Assert.assertEquals("Study" to 100, p.tagMinutes.first())
        val month = com.malluwayne.batbloom.stats.Periods.summary(list, today, zone, 30)
        org.junit.Assert.assertEquals(260, month.totalMinutes)
    }

    @org.junit.Test fun emptyPeriod() {
        val p = com.malluwayne.batbloom.stats.Periods.summary(emptyList(), today, zone, 7)
        org.junit.Assert.assertEquals(0, p.totalMinutes)
        org.junit.Assert.assertEquals(0, p.successRate)
        org.junit.Assert.assertNull(p.bestDay)
        org.junit.Assert.assertTrue(p.timeOfDay.isEmpty())
    }
}
