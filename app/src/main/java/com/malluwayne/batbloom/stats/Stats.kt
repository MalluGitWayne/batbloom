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
    val tagMinutesWeek: List<Pair<String, Int>>,
    /** Every FMGE subject with its all-time minutes, most studied first. */
    val subjectMinutes: List<Pair<String, Int>>,
    val fmgeTodayMinutes: Int,
    val completed: Int,
    val failed: Int,
    val successRate: Int,
    val streakDays: Int,
    val cravingsBeaten: Int,
)

data class SmokingProfile(val perDay: Int, val packSize: Int, val packPrice: Double)
data class Treat(val name: String, val price: Double)

data class SmokeFree(val days: Double, val notSmoked: Int, val moneySaved: Double)

data class CravingSummary(
    val beaten: Int,
    val lost: Int,
    /** Average intensity per day for the last 7 days (null where no cravings were logged), oldest first. */
    val intensity7: List<Pair<LocalDate, Double?>>,
    val topTriggers: List<Pair<String, Int>>,
)

object Stats {
    /** Time since the start of the quit day; zero before it. */
    fun smokeFree(quit: LocalDate, nowMillis: Long, zone: ZoneId, p: SmokingProfile): SmokeFree {
        val start = quit.atStartOfDay(zone).toInstant().toEpochMilli()
        val days = ((nowMillis - start).coerceAtLeast(0)) / 86_400_000.0
        val cigs = (days * p.perDay).toInt()
        val money = if (p.packSize > 0) cigs.toDouble() / p.packSize * p.packPrice else 0.0
        return SmokeFree(days, cigs, money)
    }

    fun cravings(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): CravingSummary {
        val c = sessions.filter { it.tag == Tags.CRAVING }
        val byDay = c.filter { it.cravingIntensity != null }.groupBy { dayOf(it.startedAt, zone) }
        return CravingSummary(
            beaten = c.count { it.status == Status.DONE },
            lost = c.count { it.status == Status.FAILED },
            intensity7 = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { d ->
                d to byDay[d]?.mapNotNull { it.cravingIntensity }?.average()
            },
            topTriggers = c.mapNotNull { it.cravingTrigger }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5),
        )
    }

    fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun compute(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): StatsSummary {
        val done = sessions.filter { it.status == Status.DONE }
        val failed = sessions.count { it.status == Status.FAILED }
        val byDay = done.groupBy { dayOf(it.startedAt, zone) }.mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val thisWeek = done.filter { !dayOf(it.startedAt, zone).isBefore(weekStart) && !dayOf(it.startedAt, zone).isAfter(today) }

        val subjects = done.filter { it.tag == Tags.FMGE && it.subject != null }
            .groupBy { it.subject!! }.mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
        val subjectList = (Tags.fmgeSubjects.map { it to (subjects[it] ?: 0) } +
            subjects.filterKeys { it !in Tags.fmgeSubjects }.toList())
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { Tags.fmgeSubjects.indexOf(it.first) })

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
            tagMinutesWeek = thisWeek.groupBy { it.tag }.map { (t, l) -> t to l.sumOf { it.plannedMinutes } }.sortedByDescending { it.second },
            subjectMinutes = subjectList,
            fmgeTodayMinutes = done.filter { it.tag == Tags.FMGE && dayOf(it.startedAt, zone) == today }.sumOf { it.plannedMinutes },
            completed = done.size,
            failed = failed,
            successRate = if (total == 0) 0 else (done.size * 100 + total / 2) / total,
            streakDays = streak,
            cravingsBeaten = done.count { it.tag == Tags.CRAVING },
        )
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
