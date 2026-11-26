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

    @Test fun suggestNext() {
        val subjects = Tags.fmgeSubjects.filter { it != Tags.MCQ }.map { st(it, 0) }.toMutableList()
        subjects.replaceAll { if (it.subject == "Anatomy") st("Anatomy", 300) else if (it.subject == "Physiology") st("Physiology", 30) else it }
        val s2 = ExamPlan.suggest(subjects)
        assertEquals(2, s2.size)
        assertTrue("Anatomy" !in s2 && "Physiology" !in s2)  // not-started come first
        val all = subjects.map { st(it.subject, 60) }.toMutableList().apply { this[3] = st(this[3].subject, 5) }
        assertEquals(all[3].subject, ExamPlan.suggest(all, 1).single())  // then the least-studied
        assertTrue(Tags.MCQ !in ExamPlan.suggest(subjects + st(Tags.MCQ, 0), 30))
    }

    @Test fun paceAndWeakSubjects() {
        val subjects = Tags.fmgeSubjects.filter { it != Tags.MCQ }.mapIndexed { i, s -> st(s, if (i < 4) 60 else 0) } // 4 of 19 started
        assertEquals(null, ExamPlan.pace(subjects, null, start, null))
        // 4 started over 14 days -> 15 remaining take ceil(15 * 14 / 4) = 53 days
        val p = ExamPlan.pace(subjects, start.minusDays(13), start, start.plusDays(30))!!
        assertEquals(start.plusDays(53), p.finishOn)
        assertFalse(p.onTrack)
        assertEquals(4, p.neededPerWeek) // 15 subjects / (30/7 weeks) = 3.5 -> 4
        assertTrue(ExamPlan.pace(subjects, start.minusDays(13), start, start.plusDays(60))!!.onTrack)
        // First day counts as a week: 4 subjects on day one doesn't promise 15 in 4 days
        assertEquals(start.plusDays(27), ExamPlan.pace(subjects, start, start, null)!!.finishOn)

        val t1 = MockTest(start, "GT 1", 150, 300, mapOf("Anatomy" to (5 to 17), "ENT" to (12 to 15), "Pathology" to (1 to 2)))
        val t2 = MockTest(start.plusDays(7), "GT 2", 170, 300, mapOf("Anatomy" to (9 to 17)))
        val weak = ExamPlan.weakSubjects(listOf(t1, t2))
        assertEquals(listOf("Anatomy", "ENT"), weak.map { it.subject }) // Pathology: too few questions
        assertEquals(41, weak.first().percent) // 14 of 34
        // Round trip with and without parts; old saved results still read
        val back = ExamPlan.parseTests(ExamPlan.formatTests(listOf(t1, t2.copy(parts = emptyMap()))))
        assertEquals(t1.parts, back.first().parts)
        assertTrue(back.last().parts.isEmpty())
        assertEquals(1, ExamPlan.parseTests("""[{"d":"2026-11-20","n":"old","s":120,"t":300}]""").size)
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

    @Test fun practiceAccuracy() {
        val p = listOf(
            MockTest(start, "Practice", 30, 50, mapOf("Anatomy" to (30 to 50))),
            MockTest(start.minusDays(6), "Practice", 8, 10),
            MockTest(start.minusDays(7), "Practice", 0, 100), // a week ago: outside
        )
        assertEquals(38 to 60, ExamPlan.accuracy(p, start))
        assertEquals(null, ExamPlan.accuracy(emptyList(), start))
        // Practice sets feed the weak subjects like mock tests do
        assertEquals("Anatomy", ExamPlan.weakSubjects(p).single().subject)
        assertEquals(300, ExamPlan.trimPractice(List(310) { p[0] }).size)
    }

    @Test fun pdfRows() {
        val med = MedEntity(id = 2, name = "Vitamin D", form = "Capsule", strength = 1000.0, unit = "IU", startDate = start.toString(), notes = "after food")
        val rows = MedListPdf.rows(listOf(med, med.copy(id = 3, name = "Old", archived = true)), DoseSchedule(), LocalDate.of(2026, 11, 24))
        assertEquals(listOf("Varenicline", "Vitamin D"), rows.map { it.name })
        assertTrue(rows[1].detail.contains("1000 IU"))
        assertEquals("after food", rows[1].notes)
    }
}
