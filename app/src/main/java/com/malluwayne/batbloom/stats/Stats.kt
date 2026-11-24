package com.malluwayne.batbloom.stats

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class StatsSummary(
    val todayMinutes: Int,
    val weekMinutes: Int,
    val allMinutes: Int,
    /** Oldest first, 7 entries ending today. */
    val last7: List<Pair<LocalDate, Int>>,
    val fmgeTodayMinutes: Int,
    val completed: Int,
    val failed: Int,
    val successRate: Int,
    val streakDays: Int,
    /** Longest run of days with a bloom, ever. */
    val bestStreak: Int = 0,
)

data class SmokingProfile(val perDay: Int, val packSize: Int, val packPrice: Double)
data class Treat(val name: String, val price: Double)

object Stats {
    fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun compute(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): StatsSummary {
        val done = sessions.filter { it.status == Status.DONE }
        val failed = sessions.count { it.status == Status.FAILED }
        val byDay = done.groupBy { dayOf(it.startedAt, zone) }.mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeek = done.filter { !dayOf(it.startedAt, zone).isBefore(weekStart) && !dayOf(it.startedAt, zone).isAfter(today) }

        // Streak: consecutive days with a bloom, counting back from today (or from yesterday if today has none yet).
        var day = if (byDay.containsKey(today)) today else today.minusDays(1)
        var streak = 0
        while (byDay.containsKey(day)) { streak++; day = day.minusDays(1) }

        val total = done.size + failed
        return StatsSummary(
            todayMinutes = byDay[today] ?: 0,
            weekMinutes = thisWeek.sumOf { it.plannedMinutes },
            allMinutes = done.sumOf { it.plannedMinutes },
            last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (byDay[it] ?: 0) },
            fmgeTodayMinutes = done.filter { it.tag == Tags.FMGE && dayOf(it.startedAt, zone) == today }.sumOf { it.plannedMinutes },
            completed = done.size,
            failed = failed,
            successRate = if (total == 0) 0 else (done.size * 100 + total / 2) / total,
            streakDays = streak,
            bestStreak = bestRun(byDay.keys),
        )
    }

    /** Longest run of consecutive dates in [days]. */
    fun bestRun(days: Set<LocalDate>): Int {
        var best = 0
        for (d in days) {
            if (d.minusDays(1) in days) continue
            var n = 0
            var x = d
            while (x in days) { n++; x = x.plusDays(1) }
            best = maxOf(best, n)
        }
        return best
    }

    /** Positive: days until [date]; negative: days since. */
    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** "Quit day in 7 days", "Quit day is today", "Smoke-free day 3". Day 1 is the quit day itself. */
    fun quitLine(quit: LocalDate, today: LocalDate): String {
        val d = daysUntil(quit, today)
        return when {
            d > 1 -> "Quit day in $d days"
            d == 1L -> "Quit day is tomorrow"
            d == 0L -> "Quit day is today. You've got this"
            else -> "Smoke-free day ${-d + 1}"
        }
    }

    fun examLine(exam: LocalDate, today: LocalDate): String {
        val d = daysUntil(exam, today)
        return when {
            d > 1 -> "FMGE in $d days"
            d == 1L -> "FMGE is tomorrow"
            d == 0L -> "FMGE is today. Good luck"
            else -> "FMGE was ${-d} days ago"
        }
    }
}

/** One period (the last 7 or 30 days, ending today) for the Stats screen. */
data class PeriodSummary(
    val days: List<Pair<LocalDate, Int>>,
    val totalMinutes: Int,
    val avgPerDay: Int,
    val sessions: Int,
    val avgSession: Int,
    val successRate: Int,
    val tagMinutes: List<Pair<String, Int>>,
    /** Minutes by part of day: Morning, Afternoon, Evening, Night. Only parts with time in them. */
    val timeOfDay: List<Pair<String, Int>>,
    val bestDay: Pair<LocalDate, Int>?,
)

object Periods {
    /** Morning 5–12, afternoon 12–17, evening 17–21, night 21–5. */
    fun partOfDay(hour: Int): String = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }

    fun summary(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId, length: Int): PeriodSummary {
        val first = today.minusDays(length - 1L)
        val inPeriod = sessions.filter { val d = Stats.dayOf(it.startedAt, zone); !d.isBefore(first) && !d.isAfter(today) }
        val done = inPeriod.filter { it.status == Status.DONE }
        val byDay = done.groupBy { Stats.dayOf(it.startedAt, zone) }.mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
        val total = done.sumOf { it.plannedMinutes }
        val order = listOf("Morning", "Afternoon", "Evening", "Night")
        val tod = done.groupBy { partOfDay(java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).hour) }
            .mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
        return PeriodSummary(
            days = (length - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (byDay[it] ?: 0) },
            totalMinutes = total,
            avgPerDay = total / length,
            sessions = done.size,
            avgSession = if (done.isEmpty()) 0 else total / done.size,
            successRate = if (inPeriod.isEmpty()) 0 else (done.size * 100 + inPeriod.size / 2) / inPeriod.size,
            tagMinutes = done.groupBy { it.tag }.map { (t, l) -> t to l.sumOf { it.plannedMinutes } }.sortedByDescending { it.second },
            timeOfDay = order.mapNotNull { p -> tod[p]?.let { p to it } },
            bestDay = byDay.maxByOrNull { it.value }?.toPair(),
        )
    }
}
