@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.Nav
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.stats.ExamPlan
import com.malluwayne.batbloom.stats.MockTest
import com.malluwayne.batbloom.stats.Revision
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Exam tab: countdown, today's study goal, questions, the weekly plan, and every FMGE subject with
 * its time and next revision. "Study" sets up a Focus session for that subject.
 */
@Composable
fun ExamScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val exam by prefs.examDate.collectAsState()
    val goal by prefs.dailyGoal.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val summary = remember(sessions, today) { Stats.compute(sessions, today, zone) }

    val subjects = remember(sessions, today) { Revision.subjects(sessions, today, zone) }
    val streak = remember(sessions, today) { Revision.streak(sessions, today, zone) }
    val week = remember(sessions, today) {
        (6 downTo 0).map { today.minusDays(it.toLong()) }.map { d ->
            d to sessions.filter { it.tag == Tags.FMGE && it.status == Status.DONE && Stats.dayOf(it.startedAt, zone) == d }.sumOf { it.plannedMinutes }
        }
    }
    var settings by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val mcqJson by prefs.mcq.collectAsState()
    val mcqGoal by prefs.mcqGoal.collectAsState()
    val testsJson by prefs.mockTests.collectAsState()
    var askMcq by remember { mutableStateOf(false) }
    var addTest by remember { mutableStateOf(false) }
    fun addMcq(k: Int) {
        val m = ExamPlan.parseMcq(prefs.mcq.value).toMutableMap()
        m[today] = ((m[today] ?: 0) + k).coerceAtLeast(0)
        prefs.setMcq(ExamPlan.formatMcq(m, today))
        prefs.addMcqTotal(k)
        app.appScope.launch { com.malluwayne.batbloom.quit.DoseAlarms.checkBadges(app) }
    }

    fun study(subject: String) {
        prefs.setTag(Tags.FMGE); prefs.setSubject(subject); Nav.tab.value = 0
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Exam", style = MaterialTheme.typography.headlineMedium)
                Text("FMGE preparation", color = Muted, fontSize = 13.sp)
            }
            TextButton({ settings = true }) { Text("Edit") }
        }

        // Countdown
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF1F3B4D), Color(0xFF16213A), Color(0xFF0E1026))), RoundedCornerShape(24.dp),
            ).clickable { settings = true }.padding(20.dp),
        ) {
            val e = exam
            if (e == null) Column {
                Text("When is your exam?", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Set the date to see a countdown and a study plan for each week.", color = Color(0xFFCBD2F0), fontSize = 13.sp)
                Button({ settings = true }, Modifier.padding(top = 10.dp)) { Text("Set exam date") }
            } else {
                val days = ChronoUnit.DAYS.between(today, e)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when { days > 0 -> "$days"; days == 0L -> "Today"; else -> "Done" },
                            color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black,
                        )
                        Text(
                            when { days > 1 -> "days to FMGE"; days == 1L -> "day to FMGE"; days == 0L -> "Exam day. Good luck!"; else -> "Exam was ${-days} days ago" },
                            color = Color(0xFFCBD2F0),
                        )
                        Text(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy").format(e), color = Color(0xFF8FA3C7), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (days > 0) {
                        val weeks = days / 7
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$weeks", color = Color(0xFF7FE3D0), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("weeks", color = Color(0xFF8FA3C7), fontSize = 12.sp)
                            val untouched = subjects.count { it.minutes == 0 }
                            if (untouched > 0) Text("$untouched subjects\nnot started", color = Color(0xFFFFC56B), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        // Today
        SectionCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(Rules.formatMinutes(summary.fmgeTodayMinutes), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("studied today" + if (goal > 0) " of ${Rules.formatMinutes(goal)}" else "", color = Muted, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$streak", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text(if (streak == 1) "day streak" else "days streak", color = Muted, fontSize = 12.sp)
                }
            }
            if (goal > 0) LinearProgressIndicator(
                { (summary.fmgeTodayMinutes.toFloat() / goal).coerceAtMost(1f) }, Modifier.fillMaxWidth().height(8.dp),
                color = Teal, drawStopIndicator = {}, gapSize = 0.dp,
            )
            WeekBars(week, today)
            val current by prefs.subject.collectAsState()
            val next = ExamPlan.week(subjects, today, exam).firstOrNull()?.second?.firstOrNull() ?: current
            Button({ study(next) }, Modifier.fillMaxWidth()) { Text("Study now \u00B7 $next") }
        }

        // Questions (MCQs) done today
        val mcq = remember(mcqJson) { ExamPlan.parseMcq(mcqJson) }
        SectionCard {
            val done = mcq[today] ?: 0
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("$done", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("questions today of $mcqGoal", color = Muted, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${ExamPlan.mcqWeek(mcq, today)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Lavender)
                    Text("this week \u00B7 ${ExamPlan.mcqStreak(mcq, today)}-day streak", color = Muted, fontSize = 12.sp)
                }
            }
            val timed = remember(sessions, today) {
                sessions.filter { it.tag == Tags.FMGE && it.subject == Tags.MCQ && it.status == Status.DONE && Stats.dayOf(it.startedAt, zone) == today }.sumOf { it.plannedMinutes }
            }
            if (timed > 0) Text("Plus ${Rules.formatMinutes(timed)} of timed MCQ practice today", color = Muted, fontSize = 12.sp)
            LinearProgressIndicator({ (done.toFloat() / mcqGoal.coerceAtLeast(1)).coerceAtMost(1f) }, Modifier.fillMaxWidth().height(8.dp), color = Lavender, drawStopIndicator = {}, gapSize = 0.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 25, 50).forEach { k -> OutlinedButton({ addMcq(k) }) { Text("+$k") } }
                TextButton({ askMcq = true }) { Text("Other\u2026") }
            }
        }

        // This week's plan
        val plan = remember(subjects, today, exam) { ExamPlan.week(subjects, today, exam) }
        if (plan.any { it.second.isNotEmpty() }) SectionCard("This week") {
            plan.forEach { (d, list) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        if (d == today) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " ${d.dayOfMonth}",
                        Modifier.weight(0.32f), fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp,
                        color = if (d == today) MaterialTheme.colorScheme.onSurface else Muted,
                    )
                    Text(list.joinToString(", ").ifBlank { "\u2014" }, Modifier.weight(0.68f), fontSize = 13.sp)
                }
            }
            Text("Least-studied subjects come first, and every subject gets a turn before any repeats.", color = Muted, fontSize = 11.sp)
        }

        // All subjects
        SectionCard("Subjects") {
            val covered = subjects.count { it.minutes > 0 }
            Text("$covered of ${subjects.size} started", color = Muted, fontSize = 13.sp)
            LinearProgressIndicator({ covered.toFloat() / subjects.size }, Modifier.fillMaxWidth().height(6.dp), color = Lavender, drawStopIndicator = {}, gapSize = 0.dp)
            val max = subjects.maxOf { it.minutes }.coerceAtLeast(1)
            val list = subjects.sortedWith(compareByDescending<Revision.SubjectStatus> { it.minutes > 0 }.thenByDescending { it.minutes })
            (if (showAll) list else list.take(8)).forEach { s ->
                Column(Modifier.fillMaxWidth().clickable { study(s.subject) }.padding(vertical = 4.dp)) {
                    Row {
                        Text(s.subject, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (s.minutes > 0) Rules.formatMinutes(s.minutes) else "Not started", fontSize = 13.sp, color = Muted)
                    }
                    if (s.minutes > 0) {
                        LinearProgressIndicator({ s.minutes.toFloat() / max }, Modifier.fillMaxWidth().height(5.dp).padding(top = 2.dp), color = Teal, drawStopIndicator = {}, gapSize = 0.dp)
                        Text("Last studied ${ago(s.lastStudied!!, today)} \u00B7 ${s.days} day${if (s.days == 1) "" else "s"}", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            if (list.size > 8) TextButton({ showAll = !showAll }) { Text(if (showAll) "Show less" else "Show all ${list.size} subjects") }
        }

        // Mock tests
        val tests = remember(testsJson) { ExamPlan.parseTests(testsJson) }
        SectionCard("Mock tests") {
            if (tests.isEmpty()) Text("Add your grand test and mock scores to see your trend against the pass mark (150 of 300).", color = Muted, fontSize = 13.sp)
            else {
                val last = tests.last()
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${last.percent}%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (last.percent >= ExamPlan.PASS_PERCENT) Teal else Gold)
                        Text("latest \u00B7 ${last.score}/${last.total}" + last.name.let { if (it.isBlank()) "" else " \u00B7 $it" }, color = Muted, fontSize = 13.sp)
                    }
                    if (tests.size > 1) {
                        val diff = last.percent - tests[tests.size - 2].percent
                        Text((if (diff >= 0) "+" else "") + "$diff%", color = if (diff >= 0) Teal else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
                TestChart(tests.takeLast(10))
                tests.takeLast(5).reversed().forEach { tst ->
                    Row {
                        Text(DateTimeFormatter.ofPattern("d MMM").format(tst.date) + tst.name.let { if (it.isBlank()) "" else " \u00B7 $it" }, Modifier.weight(1f), fontSize = 13.sp)
                        Text("${tst.score}/${tst.total} \u00B7 ${tst.percent}%", fontSize = 13.sp, color = Muted)
                    }
                }
            }
            TextButton({ addTest = true }) { Text("+ Add a result") }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (settings) ExamSettingsDialog { settings = false }
    if (askMcq) {
        var v by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askMcq = false },
            title = { Text("Questions done") },
            text = {
                Column {
                    NumberBox("How many? (use \u2212 to correct)", v) { v = it }
                    Row { TextButton({ v = "-" + v.trimStart('-') }) { Text("Make it \u2212") } }
                }
            },
            confirmButton = { TextButton({ v.toIntOrNull()?.let { addMcq(it) }; askMcq = false }) { Text("Add") } },
            dismissButton = { TextButton({ askMcq = false }) { Text("Cancel") } },
        )
    }
    if (addTest) {
        var name by remember { mutableStateOf("") }
        var score by remember { mutableStateOf("") }
        var total by remember { mutableStateOf("300") }
        AlertDialog(
            onDismissRequest = { addTest = false },
            title = { Text("Add a test result") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name (e.g. GT 3)") })
                    NumberBox("Score", score) { score = it }
                    NumberBox("Out of", total) { total = it }
                }
            },
            confirmButton = {
                TextButton({
                    val s = score.toIntOrNull(); val tt = total.toIntOrNull()
                    if (s != null && tt != null && tt > 0) {
                        prefs.setMockTests(ExamPlan.formatTests(ExamPlan.parseTests(prefs.mockTests.value) + MockTest(today, name.trim(), s.coerceIn(0, tt), tt)))
                        app.appScope.launch { com.malluwayne.batbloom.quit.DoseAlarms.checkBadges(app) }
                    }
                    addTest = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ addTest = false }) { Text("Cancel") } },
        )
    }
}

private fun ago(d: LocalDate, today: LocalDate): String = when (val n = ChronoUnit.DAYS.between(d, today)) {
    0L -> "today"; 1L -> "yesterday"; else -> "$n days ago"
}

@Composable
private fun WeekBars(week: List<Pair<LocalDate, Int>>, today: LocalDate) {
    val max = week.maxOf { it.second }.coerceAtLeast(30)
    val teal = Teal
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val desc = "Study time, last 7 days: " + week.joinToString(", ") { (d, m) -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + Rules.formatMinutes(m) }
    Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = desc }) {
        val gap = size.width / week.size
        val bw = gap * 0.42f
        week.forEachIndexed { i, (d, m) ->
            val x = i * gap + (gap - bw) / 2
            drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
            val h = size.height * m / max
            if (h > 0) drawRoundRect(if (d == today) teal else teal.copy(alpha = 0.55f), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        week.forEach { (d, _) ->
            Text(
                d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 11.sp, color = if (d == today) MaterialTheme.colorScheme.onSurface else Muted,
            )
        }
    }
}

/** Test scores over time, with the pass mark as a dashed line. */
@Composable
private fun TestChart(tests: List<MockTest>) {
    if (tests.size < 2) return
    val teal = Teal
    val gold = Gold
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val desc = "Mock test scores: " + tests.joinToString(", ") { "${it.percent}%" } + ". Pass mark ${ExamPlan.PASS_PERCENT}%."
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = desc }) {
        // Zoom to the scores (and the pass mark) so changes are visible.
        val lo = (minOf(tests.minOf { it.percent }, ExamPlan.PASS_PERCENT) - 8).coerceAtLeast(0)
        val hi = (maxOf(tests.maxOf { it.percent }, ExamPlan.PASS_PERCENT) + 8).coerceAtMost(100)
        val pad = 10f
        val y = { p: Int -> pad + (size.height - 2 * pad) * (1f - (p - lo).toFloat() / (hi - lo).coerceAtLeast(1)) }
        drawLine(gold.copy(alpha = 0.7f), Offset(0f, y(ExamPlan.PASS_PERCENT)), Offset(size.width, y(ExamPlan.PASS_PERCENT)), 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        val step = size.width / (tests.size - 1)
        val pts = tests.mapIndexed { i, t -> Offset(i * step, y(t.percent)) }
        pts.zipWithNext().forEach { (a, b) -> drawLine(teal, a, b, 5f, androidx.compose.ui.graphics.StrokeCap.Round) }
        pts.forEach { drawCircle(teal, 7f, it); drawCircle(Color.White, 3f, it) }
    }
}

/** Exam date, daily goal and the study reminder (moved here from Settings). */
@Composable
private fun ExamSettingsDialog(onClose: () -> Unit) {
    val prefs = BatbloomApp.instance.prefs
    val exam by prefs.examDate.collectAsState()
    val goal by prefs.dailyGoal.collectAsState()
    var pick by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Exam settings") },
        confirmButton = { TextButton(onClose) { Text("Done") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Exam date", Modifier.weight(1f))
                    TextButton({ pick = true }) { Text(exam?.let { fmt.format(it) } ?: "Set") }
                    if (exam != null) TextButton({ prefs.setExamDate(null) }) { Text("✕", color = Muted) }
                }
                Text("Daily goal: ${Rules.formatMinutes(goal)}")
                Slider(
                    goal.toFloat(), { prefs.setDailyGoal(((it / 30).roundToInt() * 30).coerceAtLeast(30)) }, valueRange = 30f..720f, steps = 22,
                    colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
                val mcqGoal by prefs.mcqGoal.collectAsState()
                StepRow("Daily questions goal", mcqGoal, 10, 1000, 10) { prefs.setMcqGoal(it) }
                StudyReminderSection()
            }
        },
    )
    if (pick) {
        val st = rememberDatePickerState((exam ?: LocalDate.now().plusMonths(3)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pick = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { prefs.setExamDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pick = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pick = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
}
