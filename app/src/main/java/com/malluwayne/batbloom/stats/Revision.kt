package com.malluwayne.batbloom.stats

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import java.time.LocalDate
import java.time.ZoneId

/** Where each FMGE subject stands, and the study streak. (Spaced revision was removed in 1.23.) */
object Revision {
    /** A subject's total minutes, last day studied and how many days it has been studied on. */
    data class SubjectStatus(val subject: String, val minutes: Int, val lastStudied: LocalDate?, val days: Int)

    fun subjects(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): List<SubjectStatus> {
        val done = sessions.filter { it.tag == Tags.FMGE && it.status == Status.DONE && it.subject != null }
        val bySubject = done.groupBy { it.subject!! }
        // MCQ practice is counted separately (questions done), so it is not a subject here.
        return Tags.fmgeSubjects.filter { it != Tags.MCQ }.map { s ->
            val l = bySubject[s].orEmpty()
            val days = l.map { Stats.dayOf(it.startedAt, zone) }.toSortedSet()
            SubjectStatus(s, l.sumOf { it.plannedMinutes }, days.lastOrNull(), days.size)
        }
    }

    /** Days in a row (ending today, or yesterday if nothing yet today) with FMGE study. */
    fun streak(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): Int {
        val days = sessions.filter { it.tag == Tags.FMGE && it.status == Status.DONE }.map { Stats.dayOf(it.startedAt, zone) }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }
}
