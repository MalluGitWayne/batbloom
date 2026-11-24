package com.malluwayne.batbloom

import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.meds.MedTime
import com.malluwayne.batbloom.meds.Phase
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.stats.ExamPlan
import com.malluwayne.batbloom.stats.MockTest
import com.malluwayne.batbloom.stats.Revision
import com.malluwayne.batbloom.ui.MedListPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExtrasTest {
    private val start = LocalDate.of(2026, 11, 23)

    @Test fun taperPhases() {
        val phases = listOf(
            Phase(1, 3, listOf(MedTime(LocalTime.of(8, 0), 1.0))),
            Phase(4, 7, listOf(MedTime(LocalTime.of(8, 0), 1.0), MedTime(LocalTime.of(20, 0), 1.0))),
        )
        val s = MedSchedule.formatPhases(phases)
        assertEquals("1-3=08:00*1|4-7=08:00*1;20:00*1", s)
        assertEquals(phases, MedSchedule.parsePhases(s))
        val m = MedEntity(id = 1, name = "Pred", form = "Tablet", frequency = "Taper", times = s, startDate = start.toString(), stock = 11.0)
        assertEquals(1, MedSchedule.dosesOn(listOf(m), start).size)
        assertEquals(2, MedSchedule.dosesOn(listOf(m), start.plusDays(3)).size)
        assertFalse(MedSchedule.occursOn(m, start.plusDays(7))) // day 8: course over
        assertTrue(MedSchedule.summary(m).contains("2 steps"))
    }

    private fun st(s: String, min: Int) = Revision.SubjectStatus(s, min, if (min > 0) start.minusDays(1) else null, if (min > 0) 1 else 0)

    @Test fun weekPlan() {
        val subjects = Tags.fmgeSubjects.filter { it != Tags.MCQ }.map { st(it, 0) }.toMutableList()
        subjects[subjects.indexOfFirst { it.subject == "Anatomy" }] = st("Anatomy", 300)
        subjects[subjects.indexOfFirst { it.subject == "ENT" }] = st("ENT", 30)
        val plan = ExamPlan.week(subjects, start, null)
        assertEquals(7, plan.size)
        assertTrue(plan.all { it.second.size == 2 })
        assertTrue("Anatomy" !in plan[0].second)              // most-studied comes last
        assertTrue(plan.flatMap { it.second }.none { it == Tags.MCQ })
        val week = plan.flatMap { it.second }
        assertEquals(week.size, week.toSet().size)             // no repeats within the week (14 of 19)
        assertTrue("ENT" !in plan[0].second)                   // not-started subjects first
        // Two weeks before the exam: three a day, nothing after the exam
        val close = ExamPlan.week(subjects, start, start.plusDays(3))
        assertEquals(3, close[0].second.size)
        assertTrue(close[5].second.isEmpty())
    }

    @Test fun courseEndAndMedStreak() {
        val taper = MedEntity(id = 1, name = "Pred", form = "Tablet", frequency = "Taper", times = "1-3=08:00*1|4-5=08:00*0.5", startDate = start.toString())
        assertEquals(start.plusDays(4), MedSchedule.courseEnd(taper))
        assertFalse(MedSchedule.finished(taper, start.plusDays(4)))
        assertTrue(MedSchedule.finished(taper, start.plusDays(5)))
        assertEquals(null, MedSchedule.courseEnd(taper.copy(frequency = "Daily", times = "08:00*1")))
        val daily = MedEntity(id = 2, name = "D", form = "Tablet", times = "08:00*1;20:00*1", startDate = start.toString())
        val taken = (0..4).flatMap { d -> listOf("08:00", "20:00").map { 2L to "${start.plusDays(d.toLong())}T$it" } }.toMutableSet()
        assertEquals(5, com.malluwayne.batbloom.quit.Badges.bestMedStreak(listOf(daily), taken, start.plusDays(4), start))
        taken.remove(2L to "${start.plusDays(2)}T20:00")
        assertEquals(2, com.malluwayne.batbloom.quit.Badges.bestMedStreak(listOf(daily), taken, start.plusDays(4), start))
        // Today unfinished doesn't break the run
        taken.add(2L to "${start.plusDays(2)}T20:00"); taken.remove(2L to "${start.plusDays(4)}T20:00")
        assertEquals(4, com.malluwayne.batbloom.quit.Badges.bestMedStreak(listOf(daily), taken, start.plusDays(4), start))
    }

    @Test fun newBadges() {
        val b = com.malluwayne.batbloom.quit.Badges
        val f = com.malluwayne.batbloom.quit.FocusFacts(mcqTotal = 600, bestMock = 55, breathing = 1, medStreak = 7)
        val e = b.earned(start, DoseSchedule(), null, facts = f)
        assertTrue(listOf("mcq500", "mock_pass", "breathe1", "meds7").all { it in e })
        assertTrue(listOf("mcq2000", "mock60", "breathe10", "meds30").none { it in e })
        assertEquals(b.all.size, b.all.map { it.id }.toSet().size)
    }

    @Test fun mcqAndTests() {
        val m = mapOf(start to 40, start.minusDays(1) to 60, start.minusDays(3) to 10, start.minusDays(200) to 5)
        val back = ExamPlan.parseMcq(ExamPlan.formatMcq(m, start))
        assertEquals(3, back.size) // old day dropped
        assertEquals(110, ExamPlan.mcqWeek(back, start))
        assertEquals(2, ExamPlan.mcqStreak(back, start))
        val tests = listOf(MockTest(start, "GT 2", 160, 300), MockTest(start.minusDays(7), "GT 1", 140, 300))
        val t2 = ExamPlan.parseTests(ExamPlan.formatTests(tests))
        assertEquals("GT 1", t2.first().name)
        assertEquals(53, t2.last().percent)
        assertEquals(emptyList<MockTest>(), ExamPlan.parseTests("not json"))
    }

    @Test fun pdfRows() {
        val med = MedEntity(id = 2, name = "Vitamin D", form = "Capsule", strength = 1000.0, unit = "IU", startDate = start.toString(), notes = "after food")
        val rows = MedListPdf.rows(listOf(med, med.copy(id = 3, name = "Old", archived = true)), DoseSchedule(), LocalDate.of(2026, 11, 24))
        assertEquals(listOf("Varenicline", "Vitamin D"), rows.map { it.name })
        assertTrue(rows[1].detail.contains("1000 IU"))
        assertEquals("after food", rows[1].notes)
    }
}
