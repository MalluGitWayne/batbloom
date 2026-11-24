@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.malluwayne.batbloom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.Nav
import com.malluwayne.batbloom.data.DoseLogEntity
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.data.MedLogEntity
import com.malluwayne.batbloom.meds.Frequency
import com.malluwayne.batbloom.meds.MedDose
import com.malluwayne.batbloom.meds.MedForm
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.meds.Meds
import com.malluwayne.batbloom.quit.DoseAlarms
import com.malluwayne.batbloom.quit.DoseSlot
import com.malluwayne.batbloom.quit.Doses
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** The varenicline course shown alongside the user's own medications. */
private val VareniclineArt = MedEntity(name = "Varenicline", form = "Tablet", shape = "Oval", color1 = 0xFFFFFFFF, color2 = 0xFFFFFFFF, background = 0xFF203A43, startDate = "2000-01-01")

/** One row in today's list: a scheduled dose of a medication, or a varenicline course dose. */
private sealed class TodayItem(val time: LocalTime) {
    class Med(val dose: MedDose, val log: MedLogEntity?) : TodayItem(dose.time)
    class Course(val slot: DoseSlot, val at: LocalTime, val log: DoseLogEntity?) : TodayItem(at)
    val open get() = when (this) { is Med -> log == null; is Course -> log == null }
}

@Composable
fun MedsScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val dao = app.db.meds()
    val zone = ZoneId.systemDefault()
    val nowMs = rememberNow(30_000)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val today = now.toLocalDate()
    val meds by dao.all().collectAsState(emptyList())
    val since = remember(today) { today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val logs by remember(today) { dao.logsSince(since, today.minusDays(1).toString()) }.collectAsState(emptyList())
    val schedule by app.prefs.schedule.collectAsState()
    val courseLogs by app.repo.doses.collectAsState(emptyList())

    var editing by remember { mutableStateOf<MedEntity?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }

    editing?.let { m ->
        BackHandler { editing = null }
        MedEditor(m, modifier, onClose = { editing = null })
        return
    }
    detailId?.let { id ->
        val m = meds.firstOrNull { it.id == id }
        if (m != null) {
            BackHandler { detailId = null }
            MedDetail(m, modifier, onBack = { detailId = null }, onEdit = { editing = m })
            return
        } else detailId = null
    }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var timeEdit by remember { mutableStateOf<Pair<Long, ZonedDateTime>?>(null) }
    var askLog by remember { mutableStateOf<MedEntity?>(null) }

    val logBySlot = logs.filter { it.slot != null }.associateBy { it.medId to it.slot }
    val courseByKey = courseLogs.associateBy { it.key }
    val items: List<TodayItem> = buildList {
        MedSchedule.dosesOn(meds, today).forEach { add(TodayItem.Med(it, logBySlot[it.med.id to it.slot])) }
        schedule.slotsOn(today).forEach { s -> add(TodayItem.Course(s, schedule.timeOf(s, zone).toLocalTime(), courseByKey[s.key])) }
    }.sortedBy { it.time }
    val asNeeded = meds.filter { Frequency.of(it.frequency) == Frequency.AsNeeded && MedSchedule.isActive(it, today) }
    val active = meds.filter { !it.archived }
    val archived = meds.filter { it.archived }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Medications", style = MaterialTheme.typography.headlineMedium)
                Text(dayFmt.format(today), color = Muted, fontSize = 13.sp)
            }
            TextButton({ editing = newMed(today) }) { Text("+ Add", fontWeight = FontWeight.SemiBold) }
        }

        if (items.isEmpty() && asNeeded.isEmpty() && active.isEmpty()) {
            SectionCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PillArt(MedForm.Capsule, "Round", Color(0xFFFF8FB1), Color(0xFFFFFFFF), Color(0xFF2E3360), 96.dp)
                    Text("Track your medications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "Add the medicines, vitamins and supplements you take. Batbloom reminds you on time and keeps a log.",
                        color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    Button({ editing = newMed(today) }, Modifier.padding(top = 12.dp)) { Text("Add a medication") }
                }
            }
        }

        // Today, grouped by time like the Health app's log
        if (items.isNotEmpty()) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            items.groupBy { it.time }.forEach { (t, group) ->
                val dueAt = today.atTime(t).atZone(zone)
                val late = group.any { it.open } && now.isAfter(dueAt.plusMinutes(60))
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeFmt.format(t), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (late) Gold else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (group.count { it.open } > 1) TextButton({
                            scope.launch {
                                group.filter { it.open }.forEach { i ->
                                    when (i) {
                                        is TodayItem.Med -> Meds.mark(ctx, i.dose, DoseStatus.TAKEN)
                                        is TodayItem.Course -> DoseAlarms.mark(ctx, i.slot, DoseStatus.TAKEN)
                                    }
                                }
                            }
                        }) { Text("Log all as taken", fontSize = 13.sp) }
                    }
                    group.forEach { i ->
                        when (i) {
                            is TodayItem.Med -> DoseLine(
                                art = { PillArt(i.dose.med, 44.dp) },
                                title = i.dose.med.name,
                                subtitle = listOf(MedSchedule.strengthLabel(i.dose.med), MedSchedule.qtyLabel(i.dose.med, i.dose.qty)).filter { it.isNotBlank() }.joinToString(" · "),
                                status = i.log?.status, takenAt = i.log?.actedAt,
                                onTake = { scope.launch { Meds.mark(ctx, i.dose, DoseStatus.TAKEN) } },
                                onSkip = { scope.launch { Meds.mark(ctx, i.dose, DoseStatus.SKIPPED) } },
                                onLogged = { i.log?.let { timeEdit = it.id to i.dose.at.atZone(zone) } },
                            )
                            is TodayItem.Course -> DoseLine(
                                art = { PillArt(VareniclineArt, 44.dp) },
                                title = "Varenicline",
                                subtitle = "${Doses.formatMg(i.slot.mg)} · day ${i.slot.day}",
                                status = i.log?.status, takenAt = i.log?.actedAt,
                                onTake = { scope.launch { DoseAlarms.mark(ctx, i.slot, DoseStatus.TAKEN) } },
                                onSkip = { scope.launch { DoseAlarms.mark(ctx, i.slot, DoseStatus.SKIPPED) } },
                                onLogged = { Nav.tab.value = 2 },
                            )
                        }
                    }
                }
            }
        }

        // As needed: log whenever you take one
        if (asNeeded.isNotEmpty()) {
            SectionCard("As needed") {
                asNeeded.forEach { m ->
                    val todays = logs.filter { it.medId == m.id && it.slot == null && Instant.ofEpochMilli(it.actedAt).atZone(zone).toLocalDate() == today }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillArt(m, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (todays.isEmpty()) (MedSchedule.strengthLabel(m).ifBlank { "Not taken today" })
                                else "${todays.size}× today · last ${timeFmt.format(Instant.ofEpochMilli(todays.maxOf { it.actedAt }).atZone(zone))}",
                                color = Muted, fontSize = 13.sp,
                            )
                        }
                        Button({ askLog = m }) { Text("Log") }
                    }
                }
            }
        }

        // Your medications
        SectionCard("Your medications") {
            if (schedule.dayNumber(today) <= schedule.courseDays) MedListRow(
                art = { PillArt(VareniclineArt, 48.dp) },
                title = "Varenicline",
                subtitle = "Quit-smoking course · ${schedule.courseDays / 7} weeks · day ${schedule.dayNumber(today).coerceAtLeast(0)}",
                onClick = { Nav.tab.value = 2 },
            )
            active.forEach { m ->
                val days = MedSchedule.daysOfStock(m, today)
                MedListRow(
                    art = { PillArt(m, 48.dp) },
                    title = m.name + MedSchedule.strengthLabel(m).let { if (it.isBlank()) "" else " · $it" },
                    subtitle = MedSchedule.summary(m) + when {
                        m.stock < 0 -> ""
                        days != null && days <= 7 -> " · only ${MedSchedule.fmtQty(m.stock)} left"
                        else -> " · ${MedSchedule.fmtQty(m.stock)} left"
                    },
                    warn = days != null && days <= 7,
                    onClick = { detailId = m.id },
                )
            }
            if (active.isEmpty()) Text("No medications yet. Tap + Add to add one.", color = Muted, fontSize = 13.sp)
        }
        if (archived.isNotEmpty()) SectionCard("Archived") {
            archived.forEach { m -> MedListRow({ PillArt(m, 40.dp) }, m.name, "Archived", onClick = { detailId = m.id }) }
        }
        Text(
            "Reminders follow your Dose reminders switch in Settings. Batbloom doesn't check for interactions: ask your doctor or pharmacist.",
            color = Muted, fontSize = 11.sp, lineHeight = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
    }

    timeEdit?.let { (logId, scheduled) -> TakenTimeDialog(
        logId, scheduled,
        onUndo = { scope.launch { Meds.undo(ctx, logId) }; timeEdit = null },
        onSave = { at -> scope.launch { Meds.setTime(ctx, logId, at) }; timeEdit = null },
        onDismiss = { timeEdit = null },
    ) }
    askLog?.let { m -> LogAsNeededDialog(m, onDismiss = { askLog = null }) { qty -> scope.launch { Meds.logAsNeeded(ctx, m, qty) }; askLog = null } }
}

fun newMed(today: LocalDate) = MedEntity(name = "", form = MedForm.Tablet.name, startDate = today.toString(), times = "08:00*1")

@Composable
private fun DoseLine(
    art: @Composable () -> Unit, title: String, subtitle: String, status: String?, takenAt: Long?,
    onTake: () -> Unit, onSkip: () -> Unit, onLogged: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        art()
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (status != null) Muted else MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        when (status) {
            DoseStatus.TAKEN -> Text(
                "✓ Taken " + (takenAt?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: ""),
                color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onLogged).padding(6.dp),
            )
            DoseStatus.SKIPPED -> Text("Skipped", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onLogged).padding(6.dp))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Skip", false, onSkip)
                Chip("Take", true, onTake)
            }
        }
    }
}

@Composable
private fun Chip(label: String, primary: Boolean, onClick: () -> Unit) {
    val teal = Teal
    Text(
        label,
        color = if (primary) MaterialTheme.colorScheme.onSecondary else Muted,
        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        modifier = Modifier
            .background(if (primary) teal else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (primary) teal else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun MedListRow(art: @Composable () -> Unit, title: String, subtitle: String, warn: Boolean = false, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        art()
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = if (warn) MaterialTheme.colorScheme.error else Muted, fontSize = 13.sp)
        }
        Text("›", color = Muted, fontSize = 20.sp)
    }
}

@Composable
private fun TakenTimeDialog(logId: Long, scheduled: ZonedDateTime, onUndo: () -> Unit, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    val dao = BatbloomApp.instance.db.meds()
    var current by remember { mutableStateOf<MedLogEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(logId) { current = dao.logById(logId) }
    val l = current ?: return
    val zone = ZoneId.systemDefault()
    val taken = Instant.ofEpochMilli(l.actedAt).atZone(zone)
    val st = rememberTimePickerState(taken.hour, taken.minute, false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (l.status == DoseStatus.TAKEN) "When did you take it?" else "Skipped") },
        text = { if (l.status == DoseStatus.TAKEN) TimePicker(st) else Text("Undo to log it again.") },
        confirmButton = {
            if (l.status == DoseStatus.TAKEN) TextButton({
                onSave(Doses.takenAt(scheduled, LocalTime.of(st.hour, st.minute), ZonedDateTime.now(zone)).toInstant().toEpochMilli())
            }) { Text("Save") } else TextButton(onDismiss) { Text("OK") }
        },
        dismissButton = { TextButton(onUndo) { Text("Undo", color = MaterialTheme.colorScheme.error) } },
    )
}

@Composable
private fun LogAsNeededDialog(m: MedEntity, onDismiss: () -> Unit, onLog: (Double) -> Unit) {
    var qty by remember { mutableStateOf(MedSchedule.parseTimes(m.times).firstOrNull()?.qty ?: 1.0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ${m.name}") },
        text = {
            Column {
                Text("Taken now", color = Muted)
                QtyStepper(qty, { qty = it }) { MedSchedule.qtyLabel(m, it) }
            }
        },
        confirmButton = { TextButton({ onLog(qty) }) { Text("Log") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun QtyStepper(qty: Double, onChange: (Double) -> Unit, label: (Double) -> String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        TextButton({ onChange((qty - 0.5).coerceAtLeast(0.5)) }) { Text("−", fontSize = 20.sp) }
        Text(label(qty), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton({ onChange(qty + 0.5) }) { Text("+", fontSize = 20.sp) }
    }
}

/** One medication: picture, schedule, stock, the last 7 days and its history. */
@Composable
private fun MedDetail(m: MedEntity, modifier: Modifier, onBack: () -> Unit, onEdit: () -> Unit) {
    val app = BatbloomApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val history by remember(m.id) { app.db.meds().history(m.id, 120) }.collectAsState(emptyList())
    var stockDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Medications") }
            Spacer(Modifier.weight(1f))
            TextButton(onEdit) { Text("Edit") }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PillArt(m, 120.dp)
            Text(m.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp))
            Text(listOf(MedForm.of(m.form).label, MedSchedule.strengthLabel(m)).filter { it.isNotBlank() }.joinToString(" · "), color = Muted)
        }
        SectionCard("Schedule") {
            Text(MedSchedule.summary(m))
            parseQtyLine(m)?.let { Text(it, color = Muted, fontSize = 13.sp) }
            Text("From " + DateTimeFormatter.ofPattern("d MMM yyyy").format(LocalDate.parse(m.startDate)) + (m.endDate?.let { " until " + DateTimeFormatter.ofPattern("d MMM yyyy").format(LocalDate.parse(it)) } ?: ""), color = Muted, fontSize = 13.sp)
            m.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontSize = 13.sp) }
        }
        SectionCard("Supply") {
            val days = MedSchedule.daysOfStock(m, today)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { stockDialog = true }) {
                Text(
                    when {
                        m.stock < 0 -> "Not tracked · tap to add how many you have"
                        days != null -> "${MedSchedule.fmtQty(m.stock)} left · about $days days"
                        else -> "${MedSchedule.fmtQty(m.stock)} left"
                    },
                    color = if (days != null && days <= 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text("›", color = Muted, fontSize = 20.sp)
            }
        }
        if (Frequency.of(m.frequency) != Frequency.AsNeeded) SectionCard("Last 7 days") {
            Row(Modifier.fillMaxWidth()) {
                (6 downTo 0).map { today.minusDays(it.toLong()) }.forEach { d ->
                    val doses = MedSchedule.dosesOn(listOf(m), d)
                    val taken = doses.count { dose -> history.any { it.slot == dose.slot && it.status == DoseStatus.TAKEN } }
                    val color = when {
                        doses.isEmpty() -> MaterialTheme.colorScheme.outlineVariant
                        taken == doses.size -> Teal
                        taken > 0 -> Gold
                        d == today -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.error
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(4.dp).width(26.dp).height(26.dp).background(color.copy(alpha = if (doses.isEmpty()) 0.3f else 0.85f), RoundedCornerShape(50)))
                        Text(d.dayOfWeek.name.take(1), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        SectionCard("History") {
            if (history.isEmpty()) Text("Nothing logged yet", color = Muted)
            history.take(30).forEach { l ->
                Row {
                    Text(fmt.format(Instant.ofEpochMilli(l.actedAt).atZone(zone)), modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(
                        if (l.status == DoseStatus.TAKEN) "Taken · ${MedSchedule.qtyLabel(m, l.quantity)}" else "Skipped",
                        color = if (l.status == DoseStatus.TAKEN) Teal else Muted, fontSize = 13.sp,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ scope.launch { Meds.setArchived(ctx, m, !m.archived) }; onBack() }) { Text(if (m.archived) "Unarchive" else "Archive") }
            TextButton({ confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (stockDialog) {
        var text by remember { mutableStateOf(if (m.stock >= 0) MedSchedule.fmtQty(m.stock) else "") }
        AlertDialog(
            onDismissRequest = { stockDialog = false },
            title = { Text("How many do you have?") },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text("${MedForm.of(m.form).unitWord}s left (empty = don't track)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            },
            confirmButton = {
                TextButton({
                    val v = text.trim().toDoubleOrNull()
                    scope.launch { Meds.save(ctx, m.copy(stock = v ?: -1.0)) }
                    stockDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ stockDialog = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${m.name}?") },
        text = { Text("This removes the medication and its whole log. Archive it instead to keep the history.") },
        confirmButton = { TextButton({ scope.launch { Meds.delete(ctx, m.id) }; confirmDelete = false; onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}

private fun parseQtyLine(m: MedEntity): String? {
    if (Frequency.of(m.frequency) == Frequency.AsNeeded) return null
    return MedSchedule.parseTimes(m.times).joinToString(" · ") { "${timeFmt.format(it.time)}: ${MedSchedule.qtyLabel(m, it.qty)}" }
}
