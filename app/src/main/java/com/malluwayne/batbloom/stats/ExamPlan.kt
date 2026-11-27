package com.malluwayne.batbloom.stats

import com.malluwayne.batbloom.data.Tags
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A mock or grand test result. [parts] is the optional score per subject: subject to (correct, asked). */
data class MockTest(
    val date: LocalDate, val name: String, val score: Int, val total: Int,
    val parts: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    val percent: Int get() = pct(score, total)
}

private fun pct(s: Int, t: Int) = if (t <= 0) 0 else (s * 100 + t / 2) / t

/** Mock tests, daily MCQ counts and the weekly study plan for the Exam tab. Pure, unit tested. */
object ExamPlan {
    /** FMGE pass mark: 150 out of 300. */
    const val PASS_PERCENT = 50

    fun parseTests(s: String): List<MockTest> = runCatching {
        val a = JSONArray(s)
        // Each entry on its own: a bad one is skipped, the rest are kept.
        (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                val p = o.optJSONObject("p")
                val parts = p?.keys()?.asSequence()?.mapNotNull { k ->
                    runCatching { p.getJSONArray(k).let { k to (it.getInt(0) to it.getInt(1)) } }.getOrNull()
                }?.toMap().orEmpty()
                MockTest(LocalDate.parse(o.getString("d")), o.optString("n"), o.getInt("s"), o.getInt("t"), parts)
            }.getOrNull()
        }
    }.getOrDefault(emptyList()).sortedBy { it.date }

    fun formatTests(l: List<MockTest>): String = JSONArray(
        l.sortedBy { it.date }.map { t ->
            JSONObject().put("d", t.date.toString()).put("n", t.name).put("s", t.score).put("t", t.total).apply {
                if (t.parts.isNotEmpty()) put("p", JSONObject().apply { t.parts.forEach { (k, v) -> put(k, JSONArray(listOf(v.first, v.second))) } })
            }
        },
    ).toString()

    /** A subject's score across every test that recorded it. */
    data class SubjectScore(val subject: String, val correct: Int, val asked: Int) { val percent: Int get() = pct(correct, asked) }

    /**
     * Weakest subjects from the tests' subject breakdowns, lowest first. A subject needs at least [minAsked]
     * questions in total so one unlucky question doesn't make it "weak".
     */
    fun weakSubjects(tests: List<MockTest>, minAsked: Int = 5, limit: Int = 3): List<SubjectScore> =
        tests.flatMap { it.parts.entries }.groupBy({ it.key }, { it.value })
            .map { (s, l) -> SubjectScore(s, l.sumOf { it.first }, l.sumOf { it.second }) }
            .filter { it.asked >= minAsked }
            .sortedWith(compareBy<SubjectScore> { it.percent }.thenBy { it.subject })
            .take(limit)

    /** How fast subjects are being started, and whether that finishes the syllabus before the exam. */
    data class Pace(val started: Int, val total: Int, val finishOn: LocalDate?, val onTrack: Boolean, val neededPerWeek: Int)

    /**
     * Pace from the day you first studied: subjects started per day (counted over at least a week so a strong
     * first day doesn't skew it). Null before anything is studied.
     */
    fun pace(subjects: List<Revision.SubjectStatus>, firstStudy: LocalDate?, today: LocalDate, exam: LocalDate?): Pace? {
        val started = subjects.count { it.minutes > 0 }
        if (started == 0 || firstStudy == null) return null
        val total = subjects.size
        val remaining = total - started
        val days = maxOf(7L, ChronoUnit.DAYS.between(firstStudy, today) + 1)
        val finish = if (remaining == 0) today else today.plusDays(kotlin.math.ceil(remaining * days.toDouble() / started).toLong())
        val weeksLeft = exam?.let { ChronoUnit.DAYS.between(today, it) / 7.0 }
        val needed = if (weeksLeft == null || weeksLeft <= 0 || remaining == 0) 0 else kotlin.math.ceil(remaining / weeksLeft).toInt()
        return Pace(started, total, finish, exam == null || !finish.isAfter(exam), needed)
    }

    fun parseMcq(s: String): Map<LocalDate, Int> = runCatching {
        val o = JSONObject(s)
        o.keys().asSequence().mapNotNull { k -> runCatching { LocalDate.parse(k) to o.getInt(k) }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())

    /** Keeps the last 120 days so the setting stays small. */
    fun formatMcq(m: Map<LocalDate, Int>, today: LocalDate): String = JSONObject().apply {
        m.filterKeys { !it.isBefore(today.minusDays(120)) }.forEach { (d, n) -> put(d.toString(), n) }
    }.toString()

    /** Right and asked over the last [days] days of practice sets; null when none had a score. */
    fun accuracy(practice: List<MockTest>, today: LocalDate, days: Int = 7): Pair<Int, Int>? =
        practice.filter { !it.date.isBefore(today.minusDays(days - 1L)) && !it.date.isAfter(today) && it.total > 0 }
            .takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.score } to l.sumOf { it.total } }

    /** Keeps the last 300 practice sets so the setting stays small. */
    fun trimPractice(l: List<MockTest>): List<MockTest> = l.takeLast(300)

    fun mcqWeek(m: Map<LocalDate, Int>, today: LocalDate): Int = (0..6).sumOf { m[today.minusDays(it.toLong())] ?: 0 }

    /** Days in a row (ending today, or yesterday if none yet today) with at least one question done. */
    fun mcqStreak(m: Map<LocalDate, Int>, today: LocalDate): Int {
        var d = if ((m[today] ?: 0) > 0) today else today.minusDays(1)
        var n = 0
        while ((m[d] ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    /** What to study next: not-started subjects first, then the least-studied. MCQ practice isn't a subject. */
    fun suggest(subjects: List<Revision.SubjectStatus>, n: Int = 2): List<String> =
        subjects.filter { it.subject != Tags.MCQ }
            .sortedWith(compareBy<Revision.SubjectStatus> { it.minutes > 0 }.thenBy { it.minutes }.thenBy { Tags.fmgeSubjects.indexOf(it.subject) })
            .take(n).map { it.subject }
}
