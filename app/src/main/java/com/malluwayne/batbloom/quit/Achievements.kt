package com.malluwayne.batbloom.quit

import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Achievement tiers: the emblem's shape and metal, and how many points it is worth. */
enum class Tier(val label: String, val points: Int) {
    BRONZE("Bronze", 10), SILVER("Silver", 25), GOLD("Gold", 50), PLATINUM("Platinum", 100)
}

/** The picture drawn in the middle of an emblem. */
enum class Glyph { PILL, NO_SMOKE, HEART, CALENDAR, MOON, HALF, TROPHY, STAR, CROWN, FLOWER, FLAME, HOURGLASS, BOLT, BAT, SUN, BOOK, TARGET }

/** An achievement. [accent] (ARGB) colours its picture and glow. */
data class Badge(
    val id: String,
    val title: String,
    val text: String,
    val glyph: Glyph,
    val tier: Tier,
    val group: String,
    val accent: Long,
)

/** Focus facts the count-based achievements are measured against. */
data class FocusFacts(
    val blooms: Int = 0,
    val streak: Int = 0,
    val focusMinutes: Int = 0,
    val longestMinutes: Int = 0,
    val nightOwl: Int = 0,
    val earlyBird: Int = 0,
    val fmgeMinutes: Int = 0,
    val subjects: Int = 0,
)

object Badges {
    const val QUIT = "Quit"
    const val FOCUS = "Focus"
    const val FMGE = "FMGE"
    val groups = listOf(QUIT, FOCUS, FMGE)

    private const val TEAL = 0xFF7FE3D0
    private const val SKY = 0xFF7FB8E3
    private const val ROSE = 0xFFFF8FB1
    private const val VIOLET = 0xFFB9A3FF
    private const val AMBER = 0xFFFFC56B
    private const val EMBER = 0xFFFF7A4D
    private const val LEAF = 0xFF8FE39A
    private const val MOON = 0xFFDCE3FF

    val all = listOf(
        Badge("course_start", "First tablet", "Started the varenicline course.", Glyph.PILL, Tier.BRONZE, QUIT, SKY),
        Badge("quit_day", "Quit day", "Reached your quit day. The hardest step.", Glyph.NO_SMOKE, Tier.SILVER, QUIT, TEAL),
        Badge("day3", "72 hours free", "Three days smoke-free. Nicotine is out of your body.", Glyph.HEART, Tier.BRONZE, QUIT, ROSE),
        Badge("week1", "1 week smoke-free", "Seven days without smoking.", Glyph.CALENDAR, Tier.BRONZE, QUIT, TEAL),
        Badge("week2", "2 weeks smoke-free", "Fourteen days without smoking.", Glyph.CALENDAR, Tier.SILVER, QUIT, SKY),
        Badge("week3", "3 weeks smoke-free", "Twenty-one days. The habit is breaking.", Glyph.CALENDAR, Tier.SILVER, QUIT, VIOLET),
        Badge("halfway", "Halfway", "Day 42 of the course.", Glyph.HALF, Tier.SILVER, QUIT, VIOLET),
        Badge("month1", "1 month smoke-free", "A whole month without smoking.", Glyph.MOON, Tier.GOLD, QUIT, MOON),
        Badge("course", "Course complete", "Finished the full course. Unlocks the Dawnbreath Lily.", Glyph.TROPHY, Tier.PLATINUM, QUIT, AMBER),
        Badge("month3", "3 months smoke-free", "Ninety days without smoking.", Glyph.MOON, Tier.GOLD, QUIT, AMBER),
        Badge("month6", "6 months smoke-free", "Half a year without smoking.", Glyph.STAR, Tier.PLATINUM, QUIT, TEAL),
        Badge("year1", "1 year smoke-free", "A full year. You did it.", Glyph.CROWN, Tier.PLATINUM, QUIT, AMBER),

        Badge("bloom1", "First bloom", "Finished your first focus session.", Glyph.FLOWER, Tier.BRONZE, FOCUS, ROSE),
        Badge("bloom10", "10 blooms", "Ten finished sessions.", Glyph.FLOWER, Tier.SILVER, FOCUS, VIOLET),
        Badge("bloom50", "50 blooms", "Fifty finished sessions.", Glyph.FLOWER, Tier.GOLD, FOCUS, AMBER),
        Badge("bloom100", "100 blooms", "A hundred finished sessions.", Glyph.FLOWER, Tier.PLATINUM, FOCUS, TEAL),
        Badge("streak3", "3-day streak", "Focused three days in a row.", Glyph.FLAME, Tier.BRONZE, FOCUS, EMBER),
        Badge("streak7", "7-day streak", "Focused seven days in a row.", Glyph.FLAME, Tier.SILVER, FOCUS, EMBER),
        Badge("streak14", "14-day streak", "Focused two weeks in a row.", Glyph.FLAME, Tier.GOLD, FOCUS, EMBER),
        Badge("streak30", "30-day streak", "Focused thirty days in a row.", Glyph.FLAME, Tier.PLATINUM, FOCUS, EMBER),
        Badge("hours10", "10 hours", "Ten hours of focus in total.", Glyph.HOURGLASS, Tier.BRONZE, FOCUS, SKY),
        Badge("hours50", "50 hours", "Fifty hours of focus in total.", Glyph.HOURGLASS, Tier.SILVER, FOCUS, SKY),
        Badge("hours100", "100 hours", "A hundred hours of focus.", Glyph.HOURGLASS, Tier.GOLD, FOCUS, AMBER),
        Badge("hours250", "250 hours", "Two hundred and fifty hours of focus.", Glyph.HOURGLASS, Tier.PLATINUM, FOCUS, TEAL),
        Badge("marathon", "Marathon", "One session of 2 hours or more.", Glyph.BOLT, Tier.GOLD, FOCUS, AMBER),
        Badge("night_owl", "Night bat", "5 sessions started between 10 PM and 4 AM.", Glyph.BAT, Tier.SILVER, FOCUS, VIOLET),
        Badge("early_bird", "Early riser", "5 sessions started between 5 and 7 AM.", Glyph.SUN, Tier.SILVER, FOCUS, AMBER),

        Badge("fmge10", "FMGE 10 hours", "Ten hours of FMGE study.", Glyph.BOOK, Tier.BRONZE, FMGE, LEAF),
        Badge("fmge50", "FMGE 50 hours", "Fifty hours of FMGE study.", Glyph.BOOK, Tier.SILVER, FMGE, LEAF),
        Badge("fmge100", "FMGE 100 hours", "A hundred hours of FMGE study.", Glyph.BOOK, Tier.GOLD, FMGE, AMBER),
        Badge("all_subjects", "Full syllabus", "Studied every FMGE subject and MCQ practice at least once.", Glyph.TARGET, Tier.PLATINUM, FMGE, LEAF),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: Badge(id, id, "", Glyph.STAR, Tier.BRONZE, "Other", 0xFF9C9CA8)

    fun points(ids: Collection<String>) = all.filter { it.id in ids }.sumOf { it.tier.points }
    val maxPoints = all.sumOf { it.tier.points }

    /** Days after the quit day for the smoke-free achievements. */
    private val quitDays = mapOf("quit_day" to 0, "day3" to 3, "week1" to 7, "week2" to 14, "week3" to 21, "month1" to 30, "month3" to 90, "month6" to 182, "year1" to 365)

    /** Count-based achievements: what is counted and the target. */
    private val counts: Map<String, Pair<(FocusFacts) -> Int, Int>> = mapOf(
        "bloom1" to ({ f: FocusFacts -> f.blooms } to 1), "bloom10" to ({ f: FocusFacts -> f.blooms } to 10),
        "bloom50" to ({ f: FocusFacts -> f.blooms } to 50), "bloom100" to ({ f: FocusFacts -> f.blooms } to 100),
        "streak3" to ({ f: FocusFacts -> f.streak } to 3), "streak7" to ({ f: FocusFacts -> f.streak } to 7),
        "streak14" to ({ f: FocusFacts -> f.streak } to 14), "streak30" to ({ f: FocusFacts -> f.streak } to 30),
        "hours10" to ({ f: FocusFacts -> f.focusMinutes / 60 } to 10), "hours50" to ({ f: FocusFacts -> f.focusMinutes / 60 } to 50),
        "hours100" to ({ f: FocusFacts -> f.focusMinutes / 60 } to 100), "hours250" to ({ f: FocusFacts -> f.focusMinutes / 60 } to 250),
        "marathon" to ({ f: FocusFacts -> f.longestMinutes } to 120),
        "night_owl" to ({ f: FocusFacts -> f.nightOwl } to 5), "early_bird" to ({ f: FocusFacts -> f.earlyBird } to 5),
        "fmge10" to ({ f: FocusFacts -> f.fmgeMinutes / 60 } to 10), "fmge50" to ({ f: FocusFacts -> f.fmgeMinutes / 60 } to 50),
        "fmge100" to ({ f: FocusFacts -> f.fmgeMinutes / 60 } to 100),
        "all_subjects" to ({ f: FocusFacts -> f.subjects } to Tags.fmgeSubjects.size),
    )

    /** Facts for the count-based achievements, from finished sessions. */
    fun facts(sessions: List<SessionEntity>, streak: Int, zone: ZoneId): FocusFacts {
        val done = sessions.filter { it.status == Status.DONE }
        val hours = done.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
        val fmge = done.filter { it.tag == Tags.FMGE }
        return FocusFacts(
            blooms = done.size,
            streak = streak,
            focusMinutes = done.sumOf { it.plannedMinutes },
            longestMinutes = done.maxOfOrNull { it.plannedMinutes } ?: 0,
            nightOwl = hours.count { it >= 22 || it < 4 },
            earlyBird = hours.count { it in 5..6 },
            fmgeMinutes = fmge.sumOf { it.plannedMinutes },
            subjects = fmge.mapNotNull { it.subject }.toSet().count { it in Tags.fmgeSubjects },
        )
    }

    /** Time-based achievements are never taken away. */
    fun earned(
        today: LocalDate, schedule: DoseSchedule, quit: LocalDate?,
        blooms: Int = 0, streak: Int = 0, facts: FocusFacts = FocusFacts(blooms = blooms, streak = streak),
    ): Set<String> = buildSet {
        if (quit != null) quitDays.forEach { (id, d) -> if (!today.isBefore(quit.plusDays(d.toLong()))) add(id) }
        val day = schedule.dayNumber(today)
        if (day >= 1) add("course_start")
        if (day >= 42) add("halfway")
        if (day >= schedule.courseDays) add("course")
        counts.forEach { (id, c) -> if (c.first(facts) >= c.second) add(id) }
    }

    /** The day a time-based achievement will be (or was) earned; null for the ones that depend on what you do. */
    fun dueDate(id: String, schedule: DoseSchedule, quit: LocalDate?): LocalDate? = when (id) {
        "course_start" -> schedule.start
        "halfway" -> schedule.start.plusDays(41)
        "course" -> schedule.endDate
        in quitDays -> quit?.plusDays(quitDays.getValue(id).toLong())
        else -> null
    }

    /** Target of a count-based achievement, for example 10 for "10 blooms". */
    fun target(id: String): Int? = counts[id]?.second

    /** Progress as (have, target): counted for count-based achievements, in days for time-based ones. */
    fun progress(id: String, today: LocalDate, schedule: DoseSchedule, quit: LocalDate?, facts: FocusFacts): Pair<Int, Int>? {
        counts[id]?.let { (f, t) -> return f(facts).coerceAtMost(t) to t }
        val (from, to) = when (id) {
            "course_start" -> return null
            "halfway" -> schedule.start to schedule.start.plusDays(41)
            "course" -> schedule.start to schedule.endDate
            in quitDays -> {
                val q = quit ?: return null
                if (id == "quit_day") return null
                q to q.plusDays(quitDays.getValue(id).toLong())
            }
            else -> return null
        }
        val total = ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(1)
        val have = ChronoUnit.DAYS.between(from, today).toInt().coerceIn(0, total)
        return have to total
    }
}
