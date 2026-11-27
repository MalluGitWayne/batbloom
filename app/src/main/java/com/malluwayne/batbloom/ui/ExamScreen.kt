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
import androidx.compose.foundation.layout.width
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
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val exam by prefs.examDate.collectAsState()
    val goal by prefs.dailyGoal.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val summary = remember(sessions, today) { Stats.compute(sessions, today, zone) }

    val subjects = remember(sessions, today) { Revision.subjects(sessions, today, zone) }
    val studiedDays = remember(sessions) {
        sessions.filter { it.tag == Tags.FMGE && it.status == Status.DONE && it.subject != null }
            .groupBy({ it.subject!! }, { Stats.dayOf(it.startedAt, zone) }).mapValues { it.value.toSet() }
    }
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
    val practiceJson by prefs.mcqPractice.collectAsState()
    val practice = remember(practiceJson) { ExamPlan.parseTests(practiceJson) }
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
                Text("Set the date to see a countdown and whether your pace will cover every subject in time.", color = Color(0xFFCBD2F0), fontSize = 13.sp)
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

        // Today: progress to the daily goal, the last 7 days, and what to study next
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Study", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (streak > 0) Text("$streak-day streak", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            val dayFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
            BarChart(
                values = week.map { it.second.toFloat() },
                labels = week.map { (d, _) -> d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) },
                names = week.map { (d, _) -> if (d == today) "today" else dayFmt.format(d) },
                color = Lavender,
                format = { Rules.formatMinutes(it.toInt()) },
                reference = goal.toFloat().takeIf { goal > 0 },
                referenceLabel = "daily goal ${Rules.formatMinutes(goal)}",
                height = 110.dp,
            )
            val current by prefs.subject.collectAsState()
            val next = ExamPlan.suggest(subjects).firstOrNull() ?: current
            Button({ study(next) }, Modifier.fillMaxWidth()) { Text("Study now \u00B7 $next") }
        }

        // Questions (MCQs) done today
        val mcq = remember(mcqJson) { ExamPlan.parseMcq(mcqJson) }
        SectionCard {
            val done = mcq[today] ?: 0
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("$done", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("of $mcqGoal questions today", color = Muted, fontSize = 13.sp, maxLines = 1)
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
            Meter(done.toFloat() / mcqGoal.coerceAtLeast(1), Lavender)
            ExamPlan.accuracy(practice, today)?.let { (right, asked) ->
                val pct = right * 100 / asked
                Text(
                    "Accuracy $pct% this week \u00B7 $right of $asked right",
                    color = if (pct >= ExamPlan.PASS_PERCENT) Teal else Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 25, 50).forEach { k -> OutlinedButton({ addMcq(k) }, Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("+$k", maxLines = 1) } }
                TextButton({ askMcq = true }) { Text("Log a set\u2026") }
            }
        }

        // All subjects
        SectionCard("Subjects") {
            val covered = subjects.count { it.minutes > 0 }
            Text("$covered of ${subjects.size} started", color = Muted, fontSize = 13.sp)
            // Pace: when every subject will have been started, at the rate so far.
            val first = remember(studiedDays) { studiedDays.values.flatten().minOrNull() }
            ExamPlan.pace(subjects, first, today, exam)?.let { p ->
                val df = DateTimeFormatter.ofPattern("d MMM")
                Text(
                    when {
                        p.started == p.total -> "Every subject started. Keep revising."
                        p.onTrack -> "At this pace, all ${p.total} started by ${df.format(p.finishOn)}" + (exam?.let { " · before your exam" } ?: "")
                        else -> "At this pace, all ${p.total} started by ${df.format(p.finishOn)}, after your exam. Start about ${p.neededPerWeek} new a week to finish in time."
                    },
                    color = if (p.onTrack) Teal else Gold, fontSize = 13.sp,
                )
            }
            Meter(covered.toFloat() / subjects.size, Lavender)
            val max = subjects.maxOf { it.minutes }.coerceAtLeast(1)
            val list = subjects.sortedWith(compareByDescending<Revision.SubjectStatus> { it.minutes > 0 }.thenByDescending { it.minutes })
            (if (showAll) list else list.take(8)).forEach { s ->
                Column(Modifier.fillMaxWidth().clickable { study(s.subject) }.padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.subject, Modifier.weight(1f), fontSize = 14.sp)
                        Text(
                            if (s.minutes > 0) Rules.formatMinutes(s.minutes) + " \u00B7 " + ago(s.lastStudied!!, today) else "\u2014",
                            fontSize = 12.sp, color = Muted,
                        )
                    }
                    if (s.minutes > 0) Meter(s.minutes.toFloat() / max, Lavender, height = 4.dp)
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
                val shown = tests.takeLast(10)
                LineChart(shown.map { it.percent }, shown.map { DateTimeFormatter.ofPattern("d MMM").format(it.date) }, ExamPlan.PASS_PERCENT, Lavender)
                val weak = remember(tests, practice) { ExamPlan.weakSubjects(tests + practice) }
                if (weak.isNotEmpty()) {
                    Text("Weakest subjects", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    weak.forEach { w ->
                        Row(Modifier.fillMaxWidth().clickable { study(w.subject) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(w.subject, Modifier.weight(1f), fontSize = 13.sp)
                            Text("${w.percent}% · ${w.correct}/${w.asked}", color = if (w.percent >= ExamPlan.PASS_PERCENT) Muted else Gold, fontSize = 13.sp)
                            Text("  Study ›", color = Lavender, fontSize = 13.sp)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
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
        var right by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf<String?>(null) }
        var pick by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { askMcq = false },
            title = { Text("Log a set of questions") },
            text = {
                Column {
                    NumberBox("How many? (use \u2212 to correct)", v) { v = it }
                    Row { TextButton({ v = "-" + v.trimStart('-') }) { Text("Make it \u2212") } }
                    if (!v.startsWith("-")) {
                        NumberBox("How many right? (optional)", right) { right = it }
                        Box {
                            TextButton({ pick = true }) { Text("Subject: " + (subject ?: "mixed") + "  \u25BE") }
                            androidx.compose.material3.DropdownMenu(pick, { pick = false }) {
                                androidx.compose.material3.DropdownMenuItem({ Text("Mixed") }, { subject = null; pick = false })
                                Tags.fmgeSubjects.filter { it != Tags.MCQ }.forEach { s ->
                                    androidx.compose.material3.DropdownMenuItem({ Text(s) }, { subject = s; pick = false })
                                }
                            }
                        }
                        Text("With the number right, Batbloom tracks your accuracy and finds weak subjects.", color = Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton({
                v.toIntOrNull()?.let { n ->
                    addMcq(n)
                    val r = right.toIntOrNull()
                    if (n > 0 && r != null) prefs.setMcqPractice(ExamPlan.formatTests(ExamPlan.trimPractice(
                        practice + MockTest(today, "Practice", r.coerceIn(0, n), n, subject?.let { mapOf(it to (r.coerceIn(0, n) to n)) } ?: emptyMap()),
                    )))
                }
                askMcq = false
            }) { Text("Add") } },
            dismissButton = { TextButton({ askMcq = false }) { Text("Cancel") } },
        )
    }
    if (addTest) {
        var name by remember { mutableStateOf("") }
        var score by remember { mutableStateOf("") }
        var total by remember { mutableStateOf("300") }
        var breakdown by remember { mutableStateOf(false) }
        // Per subject: correct and asked, both optional; a subject counts only when both are filled in.
        val correct = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
        val asked = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
        AlertDialog(
            onDismissRequest = { addTest = false },
            title = { Text("Add a test result") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name (e.g. GT 3)") })
                    NumberBox("Score", score) { score = it }
                    NumberBox("Out of", total) { total = it }
                    TextButton({ breakdown = !breakdown }) { Text(if (breakdown) "Hide subject scores" else "Add subject scores (optional)") }
                    if (breakdown) {
                        Text("Correct / questions for any subjects you know. This finds your weakest subjects.", color = Muted, fontSize = 12.sp)
                        subjects.forEach { st ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(st.subject, Modifier.weight(1f), fontSize = 13.sp)
                                SmallNumber(correct[st.subject].orEmpty()) { correct[st.subject] = it }
                                Text("/", color = Muted)
                                SmallNumber(asked[st.subject].orEmpty()) { asked[st.subject] = it }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val s = score.toIntOrNull(); val tt = total.toIntOrNull()
                    if (s != null && tt != null && tt > 0) {
                        val parts = subjects.mapNotNull { st ->
                            val c = correct[st.subject]?.toIntOrNull(); val a = asked[st.subject]?.toIntOrNull()
                            if (c != null && a != null && a > 0) st.subject to (c.coerceIn(0, a) to a) else null
                        }.toMap()
                        prefs.setMockTests(ExamPlan.formatTests(ExamPlan.parseTests(prefs.mockTests.value) + MockTest(today, name.trim(), s.coerceIn(0, tt), tt, parts)))
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
    0L -> "today"; 1L -> "yesterday"; else -> "${n}d ago"
}

@Composable
private fun SmallNumber(value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit).take(3)) }, Modifier.width(64.dp), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    )
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
                val slider = androidx.compose.material3.rememberSliderState(goal.toFloat(), 22, 30f..720f)
                Slider(
                    slider,
                    onValueChange = { slider.value = it; prefs.setDailyGoal(((it / 30).roundToInt() * 30).coerceAtLeast(30)) },
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
