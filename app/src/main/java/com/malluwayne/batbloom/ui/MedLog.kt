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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextDecoration
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

// Today's doses grouped by time, and the confirm-before-log sheet.

/** One scheduled dose in the log: of a medication, or of the varenicline course. */
internal sealed class DoseItem(val at: LocalDateTime) {
    class Med(val dose: MedDose, val log: MedLogEntity?) : DoseItem(dose.at)
    class Course(val slot: DoseSlot, at: LocalDateTime, val log: DoseLogEntity?) : DoseItem(at)
    val status: String? get() = when (this) { is Med -> log?.status; is Course -> log?.status }
    val takenAt: Long? get() = when (this) { is Med -> log?.actedAt; is Course -> log?.actedAt }
    val open get() = status == null
    val key: String get() = when (this) { is Med -> "m${dose.med.id}-${dose.slot}"; is Course -> "c${slot.key}" }
}

/** A scheduled time with its doses; "Log" (or tapping a dose) opens the confirmation sheet. */
@Composable
internal fun TimeGroup(at: LocalDateTime, group: List<DoseItem>, now: LocalDateTime, past: Boolean, onLog: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val open = group.any { it.open }
    val late = open && now.isAfter(at.plusMinutes(60))
    val allTaken = group.all { it.status == DoseStatus.TAKEN }
    val base = MaterialTheme.colorScheme.surfaceContainer
    // Done times glow teal, late ones gold: the state reads before any text does.
    val container = when {
        allTaken -> Teal.copy(alpha = 0.10f).compositeOver(base)
        else -> base
    }
    SectionCard(container = container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeFmt.format(at), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (late) Gold else MaterialTheme.colorScheme.onSurface)
            Text(
                "  " + partOfDay(at.toLocalTime()), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            when {
                open -> Chip(if (group.size > 1) "Log all" else "Log", true, onLog)
                allTaken -> Text("All taken", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onLog).padding(6.dp))
                else -> Text("Edit", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onLog).padding(6.dp))
            }
        }
        group.forEach { i ->
            val (art, title, sub) = describe(i)
            val status = i.status
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onLog)) {
                Box(Modifier.alpha(when (status) { DoseStatus.TAKEN -> 0.55f; DoseStatus.SKIPPED -> 0.35f; else -> 1f })) { art() }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        title, fontWeight = FontWeight.SemiBold,
                        color = if (status == null) MaterialTheme.colorScheme.onSurface else Muted,
                        textDecoration = if (status == DoseStatus.SKIPPED) TextDecoration.LineThrough else null,
                    )
                    Text(
                        when (status) {
                            DoseStatus.TAKEN -> "Taken" + (i.takenAt?.let { " at " + timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "")
                            DoseStatus.SKIPPED -> "Skipped"
                            else -> sub
                        },
                        color = if (status == DoseStatus.TAKEN) Teal else Muted,
                        fontSize = 13.sp,
                    )
                }
                StatusDot(status, late || past)
            }
        }
    }
}

/** Taken: a filled teal tick. Skipped: a grey cross. Still to take: an empty ring, gold when it's late. */
@Composable
private fun StatusDot(status: String?, late: Boolean) {
    val ring = if (late) Gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Box(
        Modifier.size(28.dp).then(
            when (status) {
                DoseStatus.TAKEN -> Modifier.background(Teal, CircleShape)
                DoseStatus.SKIPPED -> Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), CircleShape)
                else -> Modifier.border(2.dp, ring, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            DoseStatus.TAKEN -> Icon(Icons.Filled.Check, "Taken", tint = Color(0xFF0B2A26), modifier = Modifier.size(18.dp))
            DoseStatus.SKIPPED -> Icon(Icons.Filled.Close, "Skipped", tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

private fun partOfDay(t: LocalTime) = when (t.hour) {
    in 4..11 -> "Morning"
    in 12..16 -> "Afternoon"
    in 17..20 -> "Evening"
    else -> "Night"
}

internal fun describe(i: DoseItem): Triple<@Composable () -> Unit, String, String> = when (i) {
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
internal fun LogSheet(group: List<DoseItem>, now: ZonedDateTime, onClose: () -> Unit) {
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
    // Opens fully (no half-open step), like the old skipPartiallyExpanded = true. The set lists the positions the
    // sheet may take: hidden and fully open only.
    val sheetState = androidx.compose.material3.rememberBottomSheetState(
        androidx.compose.material3.SheetValue.Hidden,
        setOf(androidx.compose.material3.SheetValue.Hidden, androidx.compose.material3.SheetValue.Expanded),
    )

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
internal fun Segmented(value: String?, onChange: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Skipped", value == DoseStatus.SKIPPED) { onChange(if (value == DoseStatus.SKIPPED) null else DoseStatus.SKIPPED) }
        Chip("Taken", value == DoseStatus.TAKEN) { onChange(if (value == DoseStatus.TAKEN) null else DoseStatus.TAKEN) }
    }
}
