package com.malluwayne.batbloom.stats

import com.malluwayne.batbloom.data.Tags
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A mock or grand test result. */
data class MockTest(val date: LocalDate, val name: String, val score: Int, val total: Int) {
    val percent: Int get() = if (total <= 0) 0 else (score * 100 + total / 2) / total
}

/** Mock tests, daily MCQ counts and the weekly study plan for the Exam tab. Pure, unit tested. */
object ExamPlan {
    /** FMGE pass mark: 150 out of 300. */
    const val PASS_PERCENT = 50

    fun parseTests(s: String): List<MockTest> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            MockTest(LocalDate.parse(o.getString("d")), o.optString("n"), o.getInt("s"), o.getInt("t"))
        }
    }.getOrDefault(emptyList()).sortedBy { it.date }

    fun formatTests(l: List<MockTest>): String = JSONArray(
        l.sortedBy { it.date }.map { JSONObject().put("d", it.date.toString()).put("n", it.name).put("s", it.score).put("t", it.total) },
    ).toString()

    fun parseMcq(s: String): Map<LocalDate, Int> = runCatching {
        val o = JSONObject(s)
        o.keys().asSequence().associate { LocalDate.parse(it) to o.getInt(it) }
    }.getOrDefault(emptyMap())

    /** Keeps the last 120 days so the setting stays small. */
    fun formatMcq(m: Map<LocalDate, Int>, today: LocalDate): String = JSONObject().apply {
        m.filterKeys { !it.isBefore(today.minusDays(120)) }.forEach { (d, n) -> put(d.toString(), n) }
    }.toString()

    fun mcqWeek(m: Map<LocalDate, Int>, today: LocalDate): Int = (0..6).sumOf { m[today.minusDays(it.toLong())] ?: 0 }

    /** Days in a row (ending today, or yesterday if none yet today) with at least one question done. */
    fun mcqStreak(m: Map<LocalDate, Int>, today: LocalDate): Int {
        var d = if ((m[today] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((m[d] ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    // --- Saved plan: made once, kept for 7 days, so missed subjects can carry over ---

    /** A plan made on [start] for 7 days. */
    data class Saved(val start: LocalDate, val days: Map<LocalDate, List<String>>) {
        val end: LocalDate get() = start.plusDays(6)
    }

    fun parseSaved(s: String): Saved? = runCatching {
        val o = JSONObject(s)
        val d = o.getJSONObject("days")
        Saved(LocalDate.parse(o.getString("start")), d.keys().asSequence().associate { k ->
            LocalDate.parse(k) to d.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
        })
    }.getOrNull()

    fun formatSaved(p: Saved): String = JSONObject().put("start", p.start.toString()).put("days", JSONObject().apply {
        p.days.forEach { (d, l) -> put(d.toString(), JSONArray(l)) }
    }).toString()

    /** The saved plan if it still covers [today], otherwise a fresh one starting today. */
    fun current(saved: Saved?, subjects: List<Revision.SubjectStatus>, today: LocalDate, exam: LocalDate?): Saved =
        if (saved != null && !today.isBefore(saved.start) && !today.isAfter(saved.end)) saved
        else Saved(today, week(subjects, today, exam).toMap())

    /** One subject in the plan: [done] once it is studied on its day (or, if carried over, any day since). */
    data class Item(val subject: String, val day: LocalDate, val done: Boolean, val carried: Boolean = false)

    /**
     * Each day of the plan with its items. Subjects from earlier days that weren't studied on their day (or any day
     * since) are added to today as carried over. [studied] has, for each subject, the days it was studied.
     */
    fun view(p: Saved, studied: Map<String, Set<LocalDate>>, today: LocalDate): List<Pair<LocalDate, List<Item>>> {
        fun studiedBetween(s: String, from: LocalDate, to: LocalDate) =
            studied[s].orEmpty().any { !it.isBefore(from) && !it.isAfter(to) }
        val carried = p.days.filterKeys { it.isBefore(today) }.flatMap { (d, l) ->
            l.filter { !studiedBetween(it, d, today) }.map { Item(it, d, done = false, carried = true) }
        }.distinctBy { it.subject }
        return (0L..6L).map { p.start.plusDays(it) }.map { d ->
            val planned = p.days[d].orEmpty().map { s -> Item(s, d, done = studied[s].orEmpty().contains(d)) }
            d to if (d == today) planned + carried.filter { c -> planned.none { it.subject == c.subject } } else planned
        }
    }

    /** What to study now: today's first unfinished item (carried-over ones last). */
    fun next(view: List<Pair<LocalDate, List<Item>>>, today: LocalDate): String? =
        view.firstOrNull { it.first == today }?.second?.firstOrNull { !it.done }?.subject

    /**
     * A plan for the next 7 days: the least-studied subjects first (not started first), without repeating a subject
     * in the week until every subject has had a turn.
     * Two subjects a day, three in the last two weeks before the exam.
     */
    fun week(subjects: List<Revision.SubjectStatus>, today: LocalDate, exam: LocalDate?): List<Pair<LocalDate, List<String>>> {
        val perDay = if (exam != null && ChronoUnit.DAYS.between(today, exam) in 0..14) 3 else 2
        val pool = subjects.filter { it.subject != Tags.MCQ }
            .sortedWith(compareBy<Revision.SubjectStatus> { it.minutes > 0 }.thenBy { it.minutes }.thenBy { Tags.fmgeSubjects.indexOf(it.subject) })
            .map { it.subject }
        val queue = ArrayDeque(pool)
        val used = mutableSetOf<String>()
        return (0 until 7).map { i ->
            val day = today.plusDays(i.toLong())
            if (exam != null && day.isAfter(exam)) return@map day to emptyList()
            val picks = mutableListOf<String>()
            var guard = 0
            while (picks.size < perDay && pool.isNotEmpty() && guard++ < pool.size * 2) {
                if (queue.isEmpty()) { queue.addAll(pool); used.clear(); used += picks }
                val s = queue.removeFirst()
                if (s !in used && s !in picks) { picks += s; used += s }
            }
            day to picks
        }
    }
}
