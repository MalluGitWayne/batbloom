@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.meds.Frequency
import com.malluwayne.batbloom.meds.MedForm
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.meds.MedTime
import com.malluwayne.batbloom.meds.Meds
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val steps = listOf("Name", "Type", "Strength", "Schedule", "Look", "Review")

/** Adding or editing a medication, one question per page like the Health app. */
@Composable
fun MedEditor(start: MedEntity, modifier: Modifier, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = start.id == 0L
    var step by remember { mutableIntStateOf(0) }

    var name by remember { mutableStateOf(start.name) }
    var form by remember { mutableStateOf(MedForm.of(start.form)) }
    var strength by remember { mutableStateOf(if (start.strength > 0) MedSchedule.fmtQty(start.strength) else "") }
    var unit by remember { mutableStateOf(start.unit) }
    var freq by remember { mutableStateOf(Frequency.of(start.frequency)) }
    var weekdays by remember { mutableIntStateOf(start.weekdays) }
    var interval by remember { mutableIntStateOf(start.intervalDays.coerceAtLeast(2)) }
    val startPhases = if (Frequency.of(start.frequency) == Frequency.Taper) MedSchedule.parsePhases(start.times) else emptyList()
    val times = remember {
        mutableStateListOf<MedTime>().apply {
            addAll((startPhases.firstOrNull()?.times ?: MedSchedule.parseTimes(start.times)).ifEmpty { listOf(MedTime(LocalTime.of(8, 0))) })
        }
    }
    val phases = remember { mutableStateListOf<com.malluwayne.batbloom.meds.Phase>().apply { addAll(startPhases) } }
    val others by BatbloomApp.instance.db.meds().all().collectAsState(emptyList())
    val duplicate = others.firstOrNull { it.id != start.id && !it.archived && it.name.trim().equals(name.trim(), ignoreCase = true) }
    var startDate by remember { mutableStateOf(LocalDate.parse(start.startDate)) }
    var endDate by remember { mutableStateOf(start.endDate?.let { LocalDate.parse(it) }) }
    var shape by remember { mutableStateOf(start.shape) }
    var c1 by remember { mutableLongStateOf(start.color1) }
    var c2 by remember { mutableLongStateOf(start.color2) }
    var bg by remember { mutableLongStateOf(start.background) }
    var notes by remember { mutableStateOf(start.notes ?: "") }
    var stock by remember { mutableStateOf(if (start.stock >= 0) MedSchedule.fmtQty(start.stock) else "") }

    fun build() = start.copy(
        name = name.trim(), form = form.name, strength = strength.toDoubleOrNull() ?: 0.0, unit = unit,
        shape = shape, color1 = c1, color2 = c2, background = bg, notes = notes.trim().ifBlank { null },
        frequency = freq.name, weekdays = weekdays, intervalDays = interval, times = if (freq == Frequency.Taper) MedSchedule.formatPhases(phases) else MedSchedule.formatTimes(times),
        startDate = startDate.toString(), endDate = endDate?.toString(), stock = stock.trim().toDoubleOrNull() ?: -1.0,
    )

    val canNext = when (step) {
        0 -> name.isNotBlank()
        3 -> when (freq) {
            Frequency.AsNeeded -> true
            Frequency.Taper -> phases.isNotEmpty() && phases.all { it.times.isNotEmpty() }
            else -> times.isNotEmpty() && (freq != Frequency.Weekdays || weekdays != 0)
        }
        else -> true
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ if (step == 0) onClose() else step-- }) { Text(if (step == 0) "Cancel" else "‹ Back") }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                steps.indices.forEach { i ->
                    Box(Modifier.size(if (i == step) 9.dp else 6.dp).background(if (i <= step) Lavender else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
            }
            Spacer(Modifier.weight(1f))
            if (!isNew) TextButton({ BatbloomApp.instance.appScope.launch { Meds.save(ctx, build()) }; onClose() }, enabled = name.isNotBlank()) { Text("Save") }
            else Spacer(Modifier.size(64.dp, 1.dp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PillArt(form, shape, Color(c1), Color(c2), Color(bg), if (step == 4) 140.dp else 84.dp)
            }
            when (step) {
                0 -> {
                    Title("Medication name", "What's on the box or label.")
                    OutlinedTextField(
                        name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    duplicate?.let { DuplicateNote(it) }
                }
                1 -> {
                    Title("Choose the medication type", null)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedForm.entries.forEach { f -> Choice(f.label, form == f) { form = f } }
                    }
                }
                2 -> {
                    Title("Add the strength", "Optional. For example 500 mg.")
                    OutlinedTextField(
                        strength, { strength = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Strength") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedSchedule.units.forEach { u -> Choice(u, unit == u) { unit = u } }
                    }
                }
                3 -> ScheduleStep(
                    freq, { freq = it }, weekdays, { weekdays = it }, interval, { interval = it }, times, phases,
                    startDate, { startDate = it }, endDate, { endDate = it }, form,
                )
                4 -> {
                    Title("Choose the look", "Helps you tell your medications apart.")
                    if (form == MedForm.Tablet) {
                        Label("Shape")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TabletShapes.forEach { s -> Choice(s, shape == s) { shape = s } } }
                    }
                    Label(if (form == MedForm.Capsule) "Left side" else "Colour")
                    Swatches(PillColors, c1) { c1 = it }
                    if (form != MedForm.Tablet) {
                        Label(if (form == MedForm.Capsule) "Right side" else "Cap and details")
                        Swatches(PillColors, c2) { c2 = it }
                    }
                    Label("Background")
                    Swatches(PillBackgrounds, bg) { bg = it }
                }
                else -> {
                    Title("Review details", null)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Display name") })
                    duplicate?.let { DuplicateNote(it) }
                    SectionCard {
                        LabeledRow("Type", form.label)
                        if ((strength.toDoubleOrNull() ?: 0.0) > 0) LabeledRow("Strength", "$strength $unit")
                        Text(MedSchedule.summary(build()), color = Muted, fontSize = 13.sp)
                    }
                    OutlinedTextField(
                        stock, { stock = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("How many ${form.unitWord}s you have (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2)
                }
            }
        }
        Button(
            {
                if (step < steps.lastIndex) step++
                else { BatbloomApp.instance.appScope.launch { Meds.save(ctx, build()) }; onClose() }
            },
            Modifier.fillMaxWidth().padding(vertical = 8.dp), enabled = canNext,
        ) { Text(if (step < steps.lastIndex) "Next" else if (isNew) "Add medication" else "Save") }
    }
}

@Composable
private fun DuplicateNote(m: MedEntity) {
    Text(
        "You already have ${m.name}" + MedSchedule.strengthLabel(m).let { if (it.isBlank()) "" else " ($it)" } + ". Adding it again can mean taking a double dose; edit the existing one instead if it's the same medicine.",
        color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
    )
}

@Composable
private fun Title(title: String, sub: String?) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        sub?.let { Text(it, color = Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun Label(s: String) = Text(s, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val lav = Lavender
    Text(
        label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(if (selected) lav else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
            .border(1.dp, if (selected) lav else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun Swatches(colors: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            Box(
                Modifier.size(36.dp)
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) ring else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(if (c == selected) 5.dp else 1.dp)
                    .background(Color(c), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun ScheduleStep(
    freq: Frequency, setFreq: (Frequency) -> Unit,
    weekdays: Int, setWeekdays: (Int) -> Unit,
    interval: Int, setInterval: (Int) -> Unit,
    times: MutableList<MedTime>,
    phases: MutableList<com.malluwayne.batbloom.meds.Phase>,
    startDate: LocalDate, setStart: (LocalDate) -> Unit,
    endDate: LocalDate?, setEnd: (LocalDate?) -> Unit,
    form: MedForm,
) {
    val tf = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val df = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }
    var editTime by remember { mutableStateOf<Int?>(null) }
    var editPhaseTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    val unitWord = { q: Double -> MedSchedule.qtyLabel(MedEntity(name = "", form = form.name, startDate = "2000-01-01"), q) }

    Title("Set a schedule", "When will you take this?")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Frequency.entries.forEach { f -> Choice(f.label, freq == f) { setFreq(f) } }
    }
    when (freq) {
        Frequency.Weekdays -> {
            Label("Days")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    val bit = 1 shl (d.value - 1)
                    val on = weekdays and bit != 0
                    val lav = Lavender
                    Box(
                        Modifier.size(40.dp).background(if (on) lav else MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                            .clickable { setWeekdays(weekdays xor bit) },
                        contentAlignment = Alignment.Center,
                    ) { Text(d.name.take(1), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Frequency.Interval -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Every", Modifier.padding(end = 8.dp))
            TextButton({ setInterval((interval - 1).coerceAtLeast(2)) }) { Text("−", fontSize = 20.sp) }
            Text("$interval days", fontWeight = FontWeight.SemiBold)
            TextButton({ setInterval((interval + 1).coerceAtMost(60)) }) { Text("+", fontSize = 20.sp) }
        }
        else -> {}
    }
    if (freq == Frequency.AsNeeded) {
        Label("Usual dose")
        val q = times.firstOrNull()?.qty ?: 1.0
        QtyStepper(q, { v -> if (times.isEmpty()) times.add(MedTime(LocalTime.of(8, 0), v)) else times[0] = times[0].copy(qty = v) }, unitWord)
        Text("No reminders. Log it from the Meds tab whenever you take it.", color = Muted, fontSize = 13.sp)
    } else if (freq == Frequency.Taper) {
        if (phases.isEmpty()) {
            phases.add(com.malluwayne.batbloom.meds.Phase(1, 7, times.toList()))
            phases.add(com.malluwayne.batbloom.meds.Phase(8, 14, times.toList()))
        }
        Text("For doses that step up or down, like a starter pack or a tapering course. Day 1 is the start date.", color = Muted, fontSize = 13.sp)
        phases.forEachIndexed { pi, ph ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Step ${pi + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (phases.size > 1) TextButton({ phases.removeAt(pi) }) { Text("Remove", color = Muted, fontSize = 13.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Days", Modifier.weight(1f))
                    TextButton({ phases[pi] = ph.copy(from = (ph.from - 1).coerceAtLeast(1)) }) { Text("\u2212") }
                    Text("${ph.from}", fontWeight = FontWeight.SemiBold)
                    TextButton({ phases[pi] = ph.copy(from = (ph.from + 1).coerceAtMost(ph.to)) }) { Text("+") }
                    Text("to")
                    TextButton({ phases[pi] = ph.copy(to = (ph.to - 1).coerceAtLeast(ph.from)) }) { Text("\u2212") }
                    Text("${ph.to}", fontWeight = FontWeight.SemiBold)
                    TextButton({ phases[pi] = ph.copy(to = ph.to + 1) }) { Text("+") }
                }
                ph.times.forEachIndexed { ti, mt ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            tf.format(mt.time), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)).clickable { editPhaseTime = pi to ti }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton({ phases[pi] = ph.copy(times = ph.times.toMutableList().also { it[ti] = mt.copy(qty = (mt.qty - 0.5).coerceAtLeast(0.5)) }) }) { Text("\u2212") }
                        Text(unitWord(mt.qty), fontSize = 13.sp)
                        TextButton({ phases[pi] = ph.copy(times = ph.times.toMutableList().also { it[ti] = mt.copy(qty = mt.qty + 0.5) }) }) { Text("+") }
                        if (ph.times.size > 1) TextButton({ phases[pi] = ph.copy(times = ph.times.toMutableList().also { it.removeAt(ti) }) }) { Text("\u2715", color = Muted) }
                    }
                }
                TextButton({
                    val last = ph.times.maxOfOrNull { it.time } ?: LocalTime.of(8, 0)
                    phases[pi] = ph.copy(times = ph.times + MedTime(last.plusHours(12).withMinute(0)))
                }) { Text("+ Add a time") }
            }
        }
        TextButton({
            val last = phases.last()
            phases.add(com.malluwayne.batbloom.meds.Phase(last.to + 1, last.to + 7, last.times))
        }) { Text("+ Add a step") }
    } else {
        Label("Times")
        times.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    tf.format(t.time), fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)).clickable { editTime = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                TextButton({ times[i] = t.copy(qty = (t.qty - 0.5).coerceAtLeast(0.5)) }) { Text("−") }
                Text(unitWord(t.qty), fontSize = 13.sp)
                TextButton({ times[i] = t.copy(qty = t.qty + 0.5) }) { Text("+") }
                if (times.size > 1) TextButton({ times.removeAt(i) }) { Text("✕", color = Muted) }
            }
        }
        TextButton({
            val last = times.maxOfOrNull { it.time } ?: LocalTime.of(8, 0)
            times.add(MedTime(last.plusHours(4).withMinute(0)))
        }) { Text("+ Add a time") }
    }
    Label("Duration")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Starts", Modifier.weight(1f))
        TextButton({ pickDate = "start" }) { Text(df.format(startDate)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("End date", Modifier.weight(1f))
        if (endDate != null) TextButton({ pickDate = "end" }) { Text(df.format(endDate)) }
        Switch(endDate != null, { on -> setEnd(if (on) startDate.plusDays(13) else null) })
    }

    editTime?.let { i ->
        val t = times.getOrNull(i)?.time ?: LocalTime.of(8, 0)
        val st = rememberTimePickerState(t.hour, t.minute, false)
        AlertDialog(
            onDismissRequest = { editTime = null },
            confirmButton = { TextButton({ if (i < times.size) times[i] = times[i].copy(time = LocalTime.of(st.hour, st.minute)); editTime = null }) { Text("OK") } },
            dismissButton = { TextButton({ editTime = null }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
    editPhaseTime?.let { (pi, ti) ->
        val ph = phases.getOrNull(pi)
        val t = ph?.times?.getOrNull(ti)?.time ?: LocalTime.of(8, 0)
        val st = rememberTimePickerState(t.hour, t.minute, false)
        AlertDialog(
            onDismissRequest = { editPhaseTime = null },
            confirmButton = {
                TextButton({
                    if (ph != null && ti < ph.times.size) phases[pi] = ph.copy(times = ph.times.toMutableList().also { it[ti] = it[ti].copy(time = LocalTime.of(st.hour, st.minute)) }.sortedBy { it.time })
                    editPhaseTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ editPhaseTime = null }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
    pickDate?.let { which ->
        val initial = if (which == "start") startDate else endDate ?: startDate
        val st = rememberDatePickerState(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "start") { setStart(d); if (endDate != null && endDate.isBefore(d)) setEnd(d) } else setEnd(if (d.isBefore(startDate)) startDate else d)
                    }
                    pickDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = null }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
}
