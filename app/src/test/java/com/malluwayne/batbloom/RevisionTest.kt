package com.malluwayne.batbloom

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.stats.Revision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RevisionTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 25)
    private fun s(day: LocalDate, subject: String, min: Int = 30, tag: String = Tags.FMGE, status: String = Status.DONE) =
        SessionEntity(startedAt = day.atTime(10, 0).toInstant(zone).toEpochMilli(), plannedMinutes = min, status = status, tag = tag, subject = subject, plantId = "x")

    @Test fun subjectStatus() {
        val l = listOf(s(today.minusDays(2), "Anatomy"), s(today.minusDays(1), "Anatomy", 45), s(today, "Physiology", status = Status.FAILED))
        val st = Revision.subjects(l, today, zone).associateBy { it.subject }
        assertEquals(75, st.getValue("Anatomy").minutes)
        assertEquals(today.minusDays(1), st.getValue("Anatomy").lastStudied)
        assertEquals(2, st.getValue("Anatomy").days)
        assertEquals(0, st.getValue("Physiology").minutes)
        assertNull(st.getValue("Physiology").lastStudied)
        assertEquals(Tags.fmgeSubjects.size - 1, st.size)
        assertTrue(Tags.MCQ !in st)
    }

    @Test fun streak() {
        val l = listOf(s(today.minusDays(1), "Anatomy"), s(today.minusDays(2), "ENT"), s(today.minusDays(4), "ENT"), s(today, "x", tag = Tags.STUDY))
        assertEquals(2, Revision.streak(l, today, zone))
        assertEquals(3, Revision.streak(l + s(today, "Anatomy"), today, zone))
    }
}
