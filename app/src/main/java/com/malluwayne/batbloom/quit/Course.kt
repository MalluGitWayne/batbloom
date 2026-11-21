package com.malluwayne.batbloom.quit

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A varenicline course as the user's doctor prescribed it. Everything is editable in Settings; the defaults
 * are the user's own plan (12 weeks from Sat 21 Nov 2026). Pure code, unit tested.
 */
data class DoseSchedule(
    val start: LocalDate = LocalDate.of(2026, 11, 21),
    val courseDays: Int = 84,
    /** Last day of phase 1 and of phase 2; phase 3 runs to the end of the course. */
    val phase1End: Int = 3,
    val phase2End: Int = 7,
    /** Morning and evening mg for phases 1, 2 and 3. */
    val mg: List<Pair<Double, Double>> = listOf(0.5 to 0.0, 0.5 to 0.5, 1.0 to 1.0),
    val amTime: LocalTime = LocalTime.of(8, 0),
    val pmTime: LocalTime = LocalTime.of(20, 0),
) {
    val endDate: LocalDate get() = start.plusDays(courseDays - 1L)

    /** Day 1 is [start]. Can be < 1 (before the course) or > [courseDays] (after it). */
    fun dayNumber(date: LocalDate): Int = (ChronoUnit.DAYS.between(start, date) + 1).toInt()

    fun dosesOnDay(day: Int): Pair<Double, Double> = when {
        day < 1 || day > courseDays -> 0.0 to 0.0
        day <= phase1End -> mg[0]
        day <= phase2End -> mg[1]
        else -> mg[2]
    }

    fun slotsOn(date: LocalDate): List<DoseSlot> {
        val (am, pm) = dosesOnDay(dayNumber(date))
        return listOfNotNull(
            if (am > 0) DoseSlot(date, true, am, dayNumber(date)) else null,
            if (pm > 0) DoseSlot(date, false, pm, dayNumber(date)) else null,
        )
    }

    fun timeOf(slot: DoseSlot, zone: ZoneId): ZonedDateTime =
        slot.date.atTime(if (slot.morning) amTime else pmTime).atZone(zone)

    /** Every dose from [from] (inclusive) to the end of the course. */
    fun slotsFrom(from: LocalDate): List<DoseSlot> {
        val first = maxOf(from, start)
        if (first.isAfter(endDate)) return emptyList()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.flatMap { slotsOn(it) }.toList()
    }

    /** The first dose strictly after [now]. */
    fun nextSlot(now: ZonedDateTime): DoseSlot? =
        slotsFrom(now.toLocalDate()).firstOrNull { timeOf(it, now.zone).isAfter(now) }

    /** The most recent dose at or before [now]. */
    fun lastSlot(now: ZonedDateTime): DoseSlot? {
        val days = listOf(now.toLocalDate().minusDays(1), now.toLocalDate())
        return days.flatMap { slotsOn(it) }.lastOrNull { !timeOf(it, now.zone).isAfter(now) }
    }
}

data class DoseSlot(val date: LocalDate, val morning: Boolean, val mg: Double, val day: Int) {
    /** Stable id used for the log row and the notification. */
    val key: String get() = "$date-${if (morning) "AM" else "PM"}"
    val label: String get() = if (morning) "Morning" else "Evening"
}

object Doses {
    fun tablets(mg: Double, strength: Double): Double = if (strength <= 0) 0.0 else mg / strength

    fun formatMg(mg: Double): String = if (mg % 1.0 == 0.0) "${mg.toInt()} mg" else "$mg mg"

    fun formatTablets(t: Double): String = when {
        t % 1.0 == 0.0 -> "${t.toInt()} tablet" + if (t == 1.0) "" else "s"
        else -> "$t tablets"
    }

    enum class Missed { TakeNow, Skip }

    /** The user's rule: take a missed dose when remembered, unless the next one is close. Never double up. */
    fun missedAdvice(now: ZonedDateTime, next: ZonedDateTime?, closeHours: Int): Missed =
        if (next != null && Duration.between(now, next).toMinutes() <= closeHours * 60L) Missed.Skip else Missed.TakeNow

    data class Stock(val coveredUntil: LocalDate?, val runsOutOn: LocalDate?, val daysLeft: Long?)

    /**
     * How long [tabletsLeft] lasts for the doses still to come from [today]. [runsOutOn] is the first day
     * with a dose that can't be fully covered (null if the stock lasts the whole course).
     */
    fun stock(schedule: DoseSchedule, tabletsLeft: Double, strength: Double, today: LocalDate, skipTodayKeys: Set<String> = emptySet()): Stock {
        var left = tabletsLeft
        var covered: LocalDate? = null
        for (s in schedule.slotsFrom(today)) {
            if (s.key in skipTodayKeys) continue
            val need = tablets(s.mg, strength)
            if (left + 1e-9 < need) {
                return Stock(covered, s.date, ChronoUnit.DAYS.between(today, s.date))
            }
            left -= need
            covered = s.date
        }
        return Stock(covered, null, null)
    }

    /** Warn about 4 days before tablets run out. */
    fun lowStock(stock: Stock, warnDays: Int = 4): Boolean = stock.daysLeft != null && stock.daysLeft <= warnDays
}

data class Milestone(val after: Duration, val title: String, val text: String)

/** Commonly cited public-health milestones after the last cigarette. Approximate; everyone is different. */
object Milestones {
    val all = listOf(
        Milestone(Duration.ofMinutes(20), "20 minutes", "Heart rate and blood pressure begin to come down."),
        Milestone(Duration.ofHours(12), "12 hours", "Carbon monoxide in your blood drops toward normal."),
        Milestone(Duration.ofHours(24), "24 hours", "Most of the nicotine has left your bloodstream."),
        Milestone(Duration.ofHours(48), "48 hours", "Taste and smell often start to improve."),
        Milestone(Duration.ofHours(72), "72 hours", "Breathing may feel easier as airways relax."),
        Milestone(Duration.ofDays(14), "2 weeks", "Circulation starts to improve (over about 2–12 weeks)."),
        Milestone(Duration.ofDays(30), "1 month", "Coughing and shortness of breath begin to ease for many people."),
        Milestone(Duration.ofDays(90), "3 months", "Lung function keeps improving."),
        Milestone(Duration.ofDays(365), "1 year", "Extra risk of heart disease is roughly half that of a smoker."),
    )

    fun progress(m: Milestone, sinceQuit: Duration): Float =
        if (sinceQuit.isNegative) 0f else (sinceQuit.seconds.toDouble() / m.after.seconds).coerceIn(0.0, 1.0).toFloat()
}

data class QuitProgress(val sinceQuit: Duration, val notSmoked: Int, val moneySaved: Double, val lifeRegained: Duration)

object QuitMath {
    /** A rough, commonly quoted estimate of life lost per cigarette. */
    const val MINUTES_PER_CIGARETTE = 20L

    /** Slips are subtracted from cigarettes not smoked but never reset the timer. */
    fun progress(quitAt: ZonedDateTime, now: ZonedDateTime, perDay: Int, packSize: Int, packPrice: Double, slipped: Int): QuitProgress {
        val since = Duration.between(quitAt, now)
        if (since.isNegative) return QuitProgress(since, 0, 0.0, Duration.ZERO)
        val cigs = ((since.seconds / 86_400.0) * perDay).toInt() - slipped
        val notSmoked = cigs.coerceAtLeast(0)
        val money = if (packSize > 0) notSmoked.toDouble() / packSize * packPrice else 0.0
        return QuitProgress(since, notSmoked, money, Duration.ofMinutes(notSmoked * MINUTES_PER_CIGARETTE))
    }

    fun formatDuration(d: Duration): String {
        if (d.isNegative) return "0m"
        val days = d.toDays()
        val h = d.toHours() % 24
        val m = d.toMinutes() % 60
        return when {
            days > 0 -> "${days}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }
}

data class Badge(val id: String, val title: String, val text: String, val coins: Int)

object Badges {
    val all = listOf(
        Badge("quit_day", "Quit day", "You reached your quit day.", 50),
        Badge("week1", "1 week smoke-free", "Seven days since your quit day.", 100),
        Badge("week3", "3 weeks smoke-free", "Twenty-one days since your quit day.", 150),
        Badge("halfway", "Halfway", "Day 42 of your course.", 200),
        Badge("cravings10", "10 cravings beaten", "Ten craving sessions ridden out.", 100),
        Badge("course", "Course complete", "You finished the full course. Unlocks the Dawnbreath Lily.", 500),
    )

    fun byId(id: String) = all.first { it.id == id }

    /** Time-based badges are never taken away by a slip. */
    fun earned(today: LocalDate, schedule: DoseSchedule, quit: LocalDate?, cravingsBeaten: Int): Set<String> = buildSet {
        if (quit != null) {
            if (!today.isBefore(quit)) add("quit_day")
            if (!today.isBefore(quit.plusDays(7))) add("week1")
            if (!today.isBefore(quit.plusDays(21))) add("week3")
        }
        val day = schedule.dayNumber(today)
        if (day >= 42) add("halfway")
        if (day >= schedule.courseDays) add("course")
        if (cravingsBeaten >= 10) add("cravings10")
    }
}
