@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.quit.DoseAlarms
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.quit.Doses
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The varenicline course: progress, tablets, dose log and the course plan (moved here from Settings and Quit). */
@Composable
fun CourseDetail(modifier: Modifier, onBack: () -> Unit) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val schedule by prefs.schedule.collectAsState()
    val stockLeft by prefs.tabletsLeft.collectAsState()
    val strength by prefs.tabletStrength.collectAsState()
    val doses by app.repo.doses.collectAsState(emptyList())
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val day = schedule.dayNumber(today)
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }
    val tf = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var stockDialog by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    LaunchedEffect(schedule) { DoseAlarms.scheduleNext(ctx) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onBack) { Text("‹ Medications") } }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PillArt(VareniclineArt, 120.dp)
            Text("Varenicline", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp))
            Text("Quit-smoking course", color = Muted)
        }

        SectionCard("Course") {
            Text(
                when {
                    day < 1 -> "Starts ${fmt.format(schedule.start)}"
                    day > schedule.courseDays -> "Finished ${fmt.format(schedule.endDate)}"
                    else -> "Day $day of ${schedule.courseDays}"
                },
                fontWeight = FontWeight.SemiBold,
            )
            if (day in 1..schedule.courseDays) Meter(day.toFloat() / schedule.courseDays, Teal)
            val (am, pm) = schedule.dosesOnDay(day.coerceIn(1, schedule.courseDays))
            Text(
                "Today: " + listOfNotNull(
                    if (am > 0) "${Doses.formatMg(am)} at ${tf.format(schedule.amTime)}" else null,
                    if (pm > 0) "${Doses.formatMg(pm)} at ${tf.format(schedule.pmTime)}" else null,
                ).joinToString(" · ").ifBlank { "no doses" },
                color = Muted, fontSize = 13.sp,
            )
            val taken = doses.count { it.status == DoseStatus.TAKEN }
            val due = schedule.slotsFrom(schedule.start).count { !it.date.isAfter(today) }
            if (due > 0) Text("$taken of $due doses so far taken", color = Muted, fontSize = 13.sp)
        }

        SectionCard("Tablets") {
            val logged = schedule.slotsOn(today).filter { s -> doses.any { it.key == s.key } }.map { it.key }.toSet()
            val st = if (stockLeft >= 0) Doses.stock(schedule, stockLeft, strength, today, logged) else null
            Row(Modifier.fillMaxWidth().clickable { stockDialog = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        st == null -> "Add how many tablets you have"
                        st.runsOutOn == null -> "${Doses.formatTablets(stockLeft)} of ${Doses.formatMg(strength)} · enough for the course"
                        else -> "${Doses.formatTablets(stockLeft)} of ${Doses.formatMg(strength)} · until ${st.coveredUntil?.let { DateTimeFormatter.ofPattern("EEE d MMM").format(it) } ?: "today"}"
                    },
                    Modifier.weight(1f),
                    color = if (st != null && Doses.lowStock(st)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text("›", color = Muted, fontSize = 20.sp)
            }
        }

        SectionCard("Plan") {
            LabeledRow("Day 1", fmt.format(schedule.start))
            LabeledRow("Last day", "Day ${schedule.courseDays}, ${fmt.format(schedule.endDate)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickTime = "am" }) { Text("Morning ${tf.format(schedule.amTime)}") }
                OutlinedButton({ pickTime = "pm" }) { Text("Evening ${tf.format(schedule.pmTime)}") }
            }
            TextButton({ showPlan = !showPlan }) { Text(if (showPlan) "Hide course details" else "Change course details ›") }
            if (showPlan) {
                OutlinedButton({ pickStart = true }) { Text("Change start date") }
                StepRow("Course length (days)", schedule.courseDays, 7, 200, 7) { prefs.setSchedule(schedule.copy(courseDays = it)) }
                StepRow("Phase 1 ends on day", schedule.phase1End, 1, schedule.phase2End, 1) { prefs.setSchedule(schedule.copy(phase1End = it)) }
                StepRow("Phase 2 ends on day", schedule.phase2End, schedule.phase1End, schedule.courseDays, 1) { prefs.setSchedule(schedule.copy(phase2End = it)) }
                val ranges = listOf("Days 1–${schedule.phase1End}", "Days ${schedule.phase1End + 1}–${schedule.phase2End}", "Days ${schedule.phase2End + 1}–${schedule.courseDays}")
                schedule.mg.forEachIndexed { i, (am, pm) ->
                    Text(ranges[i], fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    MgChoice("Morning", am) { v -> prefs.setSchedule(schedule.copy(mg = schedule.mg.toMutableList().also { it[i] = v to pm })) }
                    MgChoice("Evening", pm) { v -> prefs.setSchedule(schedule.copy(mg = schedule.mg.toMutableList().also { it[i] = am to v })) }
                }
                TextButton({ prefs.setSchedule(DoseSchedule()) }) { Text("Reset to my original plan") }
            }
            Text("Always follow your doctor's prescription.", color = Muted, fontSize = 12.sp)
        }

        if (doses.isNotEmpty()) SectionCard("Dose log") {
            doses.take(21).forEach { d ->
                LabeledRow(
                    "Day ${d.day} · ${if (d.key.endsWith("AM")) "morning" else "evening"} · ${Doses.formatMg(d.mg)}",
                    if (d.status == DoseStatus.TAKEN) "Taken ${tf.format(Instant.ofEpochMilli(d.actedAt).atZone(zone))}" else "Skipped",
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (stockDialog) {
        var count by remember { mutableStateOf(if (stockLeft >= 0) stockLeft.toString().removeSuffix(".0") else "") }
        var mg by remember { mutableDoubleStateOf(strength) }
        AlertDialog(
            onDismissRequest = { stockDialog = false },
            title = { Text("Tablet stock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberBox("Tablets left", count) { count = it }
                    Text("Strength per tablet", color = Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5, 1.0).forEach { v -> FilterChip(mg == v, { mg = v }, { Text(Doses.formatMg(v)) }) }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    prefs.setTabletStrength(mg)
                    prefs.setTabletsLeft(count.toDoubleOrNull() ?: -1.0)
                    stockDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ stockDialog = false }) { Text("Cancel") } },
        )
    }
    if (pickStart) {
        val st = rememberDatePickerState(schedule.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickStart = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { prefs.setSchedule(schedule.copy(start = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) }
                    pickStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickStart = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
    pickTime?.let { which ->
        val t = if (which == "am") schedule.amTime else schedule.pmTime
        val st = rememberTimePickerState(t.hour, t.minute, false)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton({
                    val nt = LocalTime.of(st.hour, st.minute)
                    prefs.setSchedule(if (which == "am") schedule.copy(amTime = nt) else schedule.copy(pmTime = nt))
                    pickTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickTime = null }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
}

@Composable
private fun MgChoice(label: String, mg: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = Muted)
        listOf(0.0, 0.5, 1.0).forEach { v -> FilterChip(mg == v, { onChange(v) }, { Text(if (v == 0.0) "None" else Doses.formatMg(v), fontSize = 12.sp) }) }
    }
}
