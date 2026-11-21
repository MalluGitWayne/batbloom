package com.malluwayne.batbloom.stats

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Spaced revision for FMGE subjects. Each day you study a subject moves it up a step; the next revision is due
 * 1, 3, 7, 14 and then every 30 days after the last time you studied it.
 */
object Revision {
    val intervals = listOf(1, 3, 7, 14, 30)

    data class Due(val subject: String, val lastStudied: LocalDate, val dueOn: LocalDate, val step: Int) {
        fun overdueDays(today: LocalDate) = ChronoUnit.DAYS.between(dueOn, today)
    }

    /** Subjects due for revision today (overdue first). A subject studied today is not due. */
    fun due(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): List<Due> {
        val days = sessions
            .filter { it.tag == Tags.FMGE && it.status == Status.DONE && it.subject != null && it.subject != Tags.MCQ }
            .groupBy({ it.subject!! }, { Stats.dayOf(it.startedAt, zone) })
            .mapValues { (_, d) -> d.toSortedSet() }
        return days.mapNotNull { (subject, studied) ->
            val last = studied.last()
            if (!last.isBefore(today)) return@mapNotNull null
            val step = studied.size
            val dueOn = last.plusDays(intervals[(step - 1).coerceAtMost(intervals.size - 1)].toLong())
            if (dueOn.isAfter(today)) null else Due(subject, last, dueOn, step)
        }.sortedWith(compareBy<Due> { it.dueOn }.thenBy { Tags.fmgeSubjects.indexOf(it.subject) })
    }

    fun label(d: Due, today: LocalDate): String {
        val ago = ChronoUnit.DAYS.between(d.lastStudied, today)
        return "${d.subject} · ${if (ago == 1L) "yesterday" else "$ago days ago"}"
    }
}
