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
import androidx.compose.foundation.layout.size
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** The varenicline course shown alongside the user's own medications. */
internal val VareniclineArt = MedEntity(name = "Varenicline", form = "Tablet", shape = "Oval", color1 = 0xFFFFFFFF, color2 = 0xFFFFFFFF, background = 0xFF203A43, startDate = "2000-01-01")

/** One scheduled dose in the log: of a medication, or of the varenicline course. */
private sealed class DoseItem(val at: LocalDateTime) {
    class Med(val dose: MedDose, val log: MedLogEntity?) : DoseItem(dose.at)
    class Course(val slot: DoseSlot, at: LocalDateTime, val log: DoseLogEntity?) : DoseItem(at)
    val status: String? get() = when (this) { is Med -> log?.status; is Course -> log?.status }
    val takenAt: Long? get() = when (this) { is Med -> log?.actedAt; is Course -> log?.actedAt }
    val open get() = status == null
    val key: String get() = when (this) { is Med -> "m${dose.med.id}-${dose.slot}"; is Course -> "c${slot.key}" }
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
    var courseDetail by remember { mutableStateOf(false) }

    editing?.let { m ->
        BackHandler { editing = null }
        MedEditor(m, modifier, onClose = { editing = null })
        return
    }
    if (courseDetail) {
        BackHandler { courseDetail = false }
        CourseDetail(modifier) { courseDetail = false }
        return
    }
    detailId?.let { id ->
        val m = meds.firstOrNull { it.id == id }
        if (m != null) {
            BackHandler { detailId = null }
            MedDetail(m, modifier, onBack = { detailId = null }, onEdit = { editing = m })
            return
        }
    }

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var askLog by remember { mutableStateOf<MedEntity?>(null) }
    var sheet by remember { mutableStateOf<List<String>?>(null) }
    var reminderSettings by remember { mutableStateOf(false) }

    val logBySlot = logs.filter { it.slot != null }.associateBy { it.medId to it.slot }
    val courseByKey = courseLogs.associateBy { it.key }
    fun itemsOn(d: LocalDate): List<DoseItem> = buildList {
        MedSchedule.dosesOn(meds, d).forEach { add(DoseItem.Med(it, logBySlot[it.med.id to it.slot])) }
        schedule.slotsOn(d).forEach { s -> add(DoseItem.Course(s, schedule.timeOf(s, zone).toLocalDateTime(), courseByKey[s.key])) }
    }.sortedBy { it.at }
    // Recomputed only when the data or the day changes, not on every clock tick.
    val items = remember(meds, logs, courseLogs, schedule, today) { itemsOn(today) }
    val yesterdayOpen = remember(meds, logs, courseLogs, schedule, today) { itemsOn(today.minusDays(1)).filter { it.open } }
    val allItems = (yesterdayOpen + items).associateBy { it.key }
    val asNeeded = meds.filter { Frequency.of(it.frequency) == Frequency.AsNeeded && MedSchedule.isActive(it, today) }
    val active = meds.filter { !it.archived }
    val archived = meds.filter { it.archived }
    val courseOn = schedule.dayNumber(today) in 1..schedule.courseDays

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Medications", style = MaterialTheme.typography.headlineMedium)
                Text(dayFmt.format(today), color = Muted, fontSize = 13.sp)
            }
            TextButton({ reminderSettings = true }) { Text("Reminders") }
            TextButton({ MedListPdf.share(ctx, meds.filter { !it.archived }, schedule, today) }) { Text("Share") }
            TextButton({ editing = newMed(today) }) { Text("+ Add", fontWeight = FontWeight.SemiBold) }
        }

        // Today's progress, like the Health app's summary ring
        if (items.isNotEmpty()) {
            val done = items.count { it.status == DoseStatus.TAKEN }
            val logged = items.count { !it.open }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$done of ${items.size} taken", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val next = items.firstOrNull { it.open && it.at.isAfter(now.toLocalDateTime()) }
                        Text(
                            when {
                                logged == items.size -> "All logged for today"
                                next != null -> "Next at ${timeFmt.format(next.at)}"
                                else -> "${items.size - logged} still to log"
                            },
                            color = Muted, fontSize = 13.sp,
                        )
                    }
                    androidx.compose.material3.CircularProgressIndicator(
                        { if (items.isEmpty()) 0f else done.toFloat() / items.size },
                        Modifier.size(46.dp), color = Teal, strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp,
                    )
                }
            }
        }

        if (items.isEmpty() && asNeeded.isEmpty() && active.isEmpty() && !courseOn) {
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

        if (yesterdayOpen.isNotEmpty()) {
            Text("Yesterday · not logged", style = MaterialTheme.typography.titleMedium, color = Gold)
            yesterdayOpen.groupBy { it.at }.forEach { (at, group) -> TimeGroup(at, group, now.toLocalDateTime(), true) { sheet = group.map { it.key } } }
        }

        if (items.isNotEmpty()) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            items.groupBy { it.at }.forEach { (at, group) -> TimeGroup(at, group, now.toLocalDateTime(), false) { sheet = group.map { it.key } } }
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
                                else "${todays.size}× today · last ${timeFmt.format(Instant.ofEpochMilli(todays.maxOf { it.actedAt }).atZone(zone))} (${sinceLabel(nowMs - todays.maxOf { it.actedAt })})",
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
            if (courseOn || courseLogs.isNotEmpty()) MedListRow(
                art = { PillArt(VareniclineArt, 48.dp) },
                title = "Varenicline",
                subtitle = if (courseOn) "Quit-smoking course · day ${schedule.dayNumber(today)} of ${schedule.courseDays}" else "Quit-smoking course",
                onClick = { courseDetail = true },
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
            if (active.isEmpty() && !courseOn) Text("No medications yet. Tap + Add to add one.", color = Muted, fontSize = 13.sp)
            TextButton({ editing = newMed(today) }) { Text("+ Add a medication") }
        }
        if (archived.isNotEmpty()) SectionCard("Archived") {
            archived.forEach { m -> MedListRow({ PillArt(m, 40.dp) }, m.name, "Archived", onClick = { detailId = m.id }) }
        }
        Text(
            "Batbloom doesn't check for interactions between medicines: ask your doctor or pharmacist.",
            color = Muted, fontSize = 11.sp, lineHeight = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
    }

    sheet?.let { keys ->
        val group = keys.mapNotNull { allItems[it] }
        if (group.isEmpty()) sheet = null else LogSheet(group, now) { sheet = null }
    }
    askLog?.let { m -> LogAsNeededDialog(m, onDismiss = { askLog = null }) { qty -> BatbloomApp.instance.appScope.launch { Meds.logAsNeeded(ctx, m, qty) }; askLog = null } }
    if (reminderSettings) ReminderSettingsDialog { reminderSettings = false }
}

/** "3h 10m ago", "25m ago". */
fun sinceLabel(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 48 * 60 -> "${m / 60}h ${m % 60}m ago"
        else -> "${m / 1440} days ago"
    }
}

fun newMed(today: LocalDate) = MedEntity(name = "", form = MedForm.Tablet.name, startDate = today.toString(), times = "08:00*1")

/** A scheduled time with its doses; "Log" (or tapping a dose) opens the confirmation sheet. */
@Composable
private fun TimeGroup(at: LocalDateTime, group: List<DoseItem>, now: LocalDateTime, past: Boolean, onLog: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val late = group.any { it.open } && now.isAfter(at.plusMinutes(60))
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeFmt.format(at), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (late) Gold else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (group.any { it.open }) Chip("Log", true, onLog) else TextButton(onLog) { Text("Edit", fontSize = 13.sp) }
        }
        group.forEach { i ->
            val (art, title, sub) = describe(i)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onLog)) {
                art()
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = if (i.status != null) Muted else MaterialTheme.colorScheme.onSurface)
                    if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 13.sp)
                }
                when (i.status) {
                    DoseStatus.TAKEN -> Text(
                        "✓ " + (i.takenAt?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "Taken"),
                        color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                    DoseStatus.SKIPPED -> Text("Skipped", color = Muted, fontSize = 13.sp)
                    else -> if (past) Text("Not logged", color = Gold, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun describe(i: DoseItem): Triple<@Composable () -> Unit, String, String> = when (i) {
    is DoseItem.Med -> Triple(
        { PillArt(i.dose.med, 44.dp) }, i.dose.med.name,
        listOf(MedSchedule.strengthLabel(i.dose.med), MedSchedule.qtyLabel(i.dose.med, i.dose.qty)).filter { it.isNotBlank() }.joinToString(" · "),
    )
    is DoseItem.Course -> Triple({ PillArt(VareniclineArt, 44.dp) }, "Varenicline", "${Doses.formatMg(i.slot.mg)} · course day ${i.slot.day}")
}

/**
 * The Health-app style log sheet: choose Taken or Skipped for each dose at this time and when you took them,
 * then confirm with Log. Nothing is saved until you press it.
 */
@Composable
private fun LogSheet(group: List<DoseItem>, now: ZonedDateTime, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val scheduled = group.first().at.atZone(zone)
    val closeHours by BatbloomApp.instance.prefs.closeHours.collectAsState()
    val schedule by BatbloomApp.instance.prefs.schedule.collectAsState()
    // A missed varenicline dose with the next one close: the user's rule is to skip it, never double up.
    val skipCourse = group.filterIsInstance<DoseItem.Course>().filter { c ->
        c.open && now.isAfter(scheduled.plusMinutes(60)) &&
            Doses.missedAdvice(now, schedule.slotsFrom(c.slot.date).map { schedule.timeOf(it, zone) }.firstOrNull { it.isAfter(scheduled) }, closeHours) == Doses.Missed.Skip
    }.map { it.key }.toSet()
    // Default: Taken for open doses (Skipped where the rule says so), their current status for logged ones.
    val choice = remember(group.map { it.key }) {
        androidx.compose.runtime.mutableStateMapOf<String, String?>().apply {
            group.forEach { put(it.key, it.status ?: if (it.key in skipCourse) DoseStatus.SKIPPED else DoseStatus.TAKEN) }
        }
    }
    val sameDay = scheduled.toLocalDate() == now.toLocalDate()
    var whenMode by remember { mutableStateOf(if (sameDay) "now" else "scheduled") }
    var custom by remember { mutableStateOf<ZonedDateTime?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                (if (sameDay) "" else "Yesterday, ") + timeFmt.format(scheduled),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            // Missed course dose: the user's own rule about doubling up
            if (skipCourse.isNotEmpty()) Text(
                "Varenicline: the next dose is close, so skip this one. Never take two at once.", color = Gold, fontSize = 13.sp,
            )
            group.forEach { i ->
                val (art, title, sub) = describe(i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    art()
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 12.sp)
                    }
                    Segmented(choice[i.key]) { choice[i.key] = it }
                }
            }
            Text("Taken at", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Now", whenMode == "now") { whenMode = "now" }
                Chip(timeFmt.format(scheduled), whenMode == "scheduled") { whenMode = "scheduled" }
                Chip(if (whenMode == "custom" && custom != null) timeFmt.format(custom!!) else "Other…", whenMode == "custom") { pickTime = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ group.forEach { choice[it.key] = DoseStatus.SKIPPED } }) { Text("Skip all") }
                Spacer(Modifier.weight(1f))
                TextButton(onClose) { Text("Cancel") }
                Button({
                    val at = when (whenMode) {
                        "scheduled" -> scheduled
                        "custom" -> custom ?: now
                        else -> now
                    }.toInstant().toEpochMilli()
                    BatbloomApp.instance.appScope.launch {
                        group.forEach { i ->
                            val c = choice[i.key]
                            if (c == i.status) {
                                // Already logged and unchanged: keep its time unless a time was picked on purpose.
                                if (c == DoseStatus.TAKEN && whenMode != "now" && i.takenAt != at) save(ctx, i, c, at)
                                return@forEach
                            }
                            save(ctx, i, c, at)
                        }
                    }
                    onClose()
                }) { Text("Log") }
            }
        }
    }
    if (pickTime) {
        val base = custom ?: now
        val st = rememberTimePickerState(base.hour, base.minute, false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton({
                    custom = Doses.takenAt(scheduled, LocalTime.of(st.hour, st.minute), ZonedDateTime.now(zone))
                    whenMode = "custom"; pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
}

private suspend fun save(ctx: android.content.Context, i: DoseItem, status: String?, at: Long) {
    when (i) {
        is DoseItem.Med -> if (status == null) i.log?.let { Meds.undo(ctx, it.id) } else Meds.mark(ctx, i.dose, status, at)
        is DoseItem.Course -> DoseAlarms.mark(ctx, i.slot, status, at)
    }
}

/** Taken / Skipped toggle; tapping the selected one clears it (not logged). */
@Composable
private fun Segmented(value: String?, onChange: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Skipped", value == DoseStatus.SKIPPED) { onChange(if (value == DoseStatus.SKIPPED) null else DoseStatus.SKIPPED) }
        Chip("Taken", value == DoseStatus.TAKEN) { onChange(if (value == DoseStatus.TAKEN) null else DoseStatus.TAKEN) }
    }
}

/** Reminders for all medications, including varenicline (moved here from Settings). */
@Composable
private fun ReminderSettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = BatbloomApp.instance.prefs
    val reminders by prefs.reminders.collectAsState()
    val snooze by prefs.snoozeMinutes.collectAsState()
    val close by prefs.closeHours.collectAsState()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Reminders") },
        confirmButton = { TextButton(onClose) { Text("Done") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Medication reminders", Modifier.weight(1f))
                    androidx.compose.material3.Switch(reminders, {
                        prefs.setReminders(it); DoseAlarms.scheduleNext(ctx)
                        BatbloomApp.instance.appScope.launch { com.malluwayne.batbloom.meds.MedAlarms.scheduleNext(ctx) }
                    })
                }
                Text("A follow-up comes 30 minutes later if you haven't logged it.", color = Muted, fontSize = 12.sp)
                if (!DoseAlarms.canExact(ctx)) Text("Exact alarms are off, so reminders may be a few minutes late.", color = Muted, fontSize = 12.sp)
                StepRow("Snooze (minutes)", snooze, 5, 60, 5) { prefs.setSnoozeMinutes(it) }
                StepRow("Varenicline: skip a missed dose if the next is within (hours)", close, 1, 8, 1) { prefs.setCloseHours(it) }
            }
        },
    )
}

@Composable
fun StepRow(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        TextButton({ onChange((value - step).coerceAtLeast(min)) }) { Text("−", fontSize = 18.sp) }
        Text("$value", fontWeight = FontWeight.SemiBold)
        TextButton({ onChange((value + step).coerceAtMost(max)) }) { Text("+", fontSize = 18.sp) }
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
        history.firstOrNull { it.status == DoseStatus.TAKEN }?.let { last ->
            SectionCard {
                Text("Last taken", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    sinceLabel(System.currentTimeMillis() - last.actedAt) + " · " + fmt.format(Instant.ofEpochMilli(last.actedAt).atZone(zone)),
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
            TextButton({ BatbloomApp.instance.appScope.launch { Meds.setArchived(ctx, m, !m.archived) }; onBack() }) { Text(if (m.archived) "Unarchive" else "Archive") }
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
                    BatbloomApp.instance.appScope.launch { Meds.save(ctx, m.copy(stock = v ?: -1.0)) }
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
        confirmButton = { TextButton({ BatbloomApp.instance.appScope.launch { Meds.delete(ctx, m.id) }; confirmDelete = false; onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}

private fun parseQtyLine(m: MedEntity): String? {
    if (Frequency.of(m.frequency) == Frequency.AsNeeded) return null
    if (Frequency.of(m.frequency) == Frequency.Taper) return MedSchedule.parsePhases(m.times).joinToString("\n") { p ->
        "Days ${p.from}\u2013${p.to}: " + p.times.joinToString(", ") { "${timeFmt.format(it.time)} ${MedSchedule.qtyLabel(m, it.qty)}" }
    }
    return MedSchedule.parseTimes(m.times).joinToString(" · ") { "${timeFmt.format(it.time)}: ${MedSchedule.qtyLabel(m, it.qty)}" }
}
