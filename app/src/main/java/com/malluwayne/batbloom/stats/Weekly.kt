package com.malluwayne.batbloom.stats

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.focus.Rules
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** The last seven days against the seven before, for the Sunday summary. */
data class Week(
    val focusMinutes: Int, val lastFocusMinutes: Int,
    val dosesTaken: Int, val dosesDue: Int,
    val cravings: Int, val lastCravings: Int,
)

/** The weekly summary, like a phone's weekly screen-time report: plain lines, each compared with last week. */
object Weekly {
    /** Minutes of finished focus sessions that started between [from] and [to], both included. */
    fun focusMinutes(sessions: List<SessionEntity>, from: LocalDate, to: LocalDate, zone: ZoneId): Int =
        sessions.filter { it.status == Status.DONE }
            .filter { Stats.dayOf(it.startedAt, zone).let { d -> !d.isBefore(from) && !d.isAfter(to) } }
            .sumOf { it.plannedMinutes }

    fun lines(w: Week): List<String> = buildList {
        val diff = w.focusMinutes - w.lastFocusMinutes
        add(
            "Focus: ${Rules.formatMinutes(w.focusMinutes)}" + when {
                w.lastFocusMinutes == 0 && w.focusMinutes == 0 -> ""
                diff == 0 -> ", same as last week"
                diff > 0 -> ", ${Rules.formatMinutes(diff)} more than last week"
                else -> ", ${Rules.formatMinutes(abs(diff))} less than last week"
            },
        )
        if (w.dosesDue > 0) add("Doses: ${w.dosesTaken * 100 / w.dosesDue}% taken (${w.dosesTaken} of ${w.dosesDue})")
        if (w.cravings > 0 || w.lastCravings > 0) add(
            "Cravings: ${w.cravings}" + when {
                w.cravings < w.lastCravings -> ", down from ${w.lastCravings}"
                w.cravings > w.lastCravings -> ", up from ${w.lastCravings}"
                else -> ", same as last week"
            },
        )
    }

    /** A short headline: the best news first. */
    fun title(w: Week): String = when {
        w.focusMinutes > w.lastFocusMinutes && w.lastFocusMinutes > 0 -> "More focus than last week"
        w.dosesDue > 0 && w.dosesTaken == w.dosesDue -> "Every dose taken this week"
        w.cravings < w.lastCravings -> "Fewer cravings this week"
        else -> "Your week in Batbloom"
    }
}
