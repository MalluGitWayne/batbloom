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

    /**
     * A plan for the next 7 days: revisions that fall due go on their day; the other slots go to the least-studied
     * subjects (not started first), without repeating a subject in the week until every subject has had a turn.
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
            val due = subjects.filter { s -> s.nextDue != null && (s.nextDue == day || (i == 0 && s.nextDue.isBefore(day))) }
                .map { it.subject }.filter { it !in used }.take(perDay)
            val picks = due.toMutableList()
            used += due
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
