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
import androidx.compose.runtime.mutableDoubleStateOf
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

// Small pieces shared by the Meds screens: rows, chips, steppers and dialogs.

@Composable
internal fun ReminderSettingsDialog(onClose: () -> Unit) {
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
internal fun Chip(label: String, primary: Boolean, onClick: () -> Unit) {
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
internal fun MedListRow(art: @Composable () -> Unit, title: String, subtitle: String, warn: Boolean = false, onClick: () -> Unit) {
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
internal fun LogAsNeededDialog(m: MedEntity, onDismiss: () -> Unit, onLog: (Double) -> Unit) {
    var qty by remember { mutableDoubleStateOf(MedSchedule.parseTimes(m.times).firstOrNull()?.qty ?: 1.0) }
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

/** One day in a week of doses: how many were due and how many were taken. */
internal data class DayDoses(val date: LocalDate, val due: Int, val taken: Int)

/** Seven dots, one per day: teal all taken, gold some, red none, faint when nothing was due or today is still open. */
@Composable
internal fun WeekDots(days: List<DayDoses>, today: LocalDate, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        days.forEach { d ->
            val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            val color = when {
                d.due == 0 -> track
                d.taken >= d.due -> Teal
                d.taken > 0 -> Gold
                d.date == today -> track
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.padding(vertical = 4.dp).size(22.dp).background(color, RoundedCornerShape(50))
                        .then(if (d.date == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(50)) else Modifier),
                )
                Text(
                    d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                    color = if (d.date == today) MaterialTheme.colorScheme.onSurface else Muted, fontSize = 11.sp,
                    fontWeight = if (d.date == today) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
