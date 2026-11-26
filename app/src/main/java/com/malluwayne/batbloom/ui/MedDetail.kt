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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.meds.Frequency
import com.malluwayne.batbloom.meds.MedForm
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.meds.Meds
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// One medication: schedule, supply, last 7 days, history, archive and delete.

@Composable
internal fun MedDetail(m: MedEntity, modifier: Modifier, onBack: () -> Unit, onEdit: () -> Unit) {
    val app = BatbloomApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val history by remember(m.id) { app.db.meds().history(m.id, 500) }.collectAsState(emptyList())
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
        if (!m.archived && MedSchedule.finished(m, today)) SectionCard {
            Text("Course finished 🎉", fontWeight = FontWeight.Bold)
            Text("The last dose was on ${DateTimeFormatter.ofPattern("d MMM").format(MedSchedule.courseEnd(m))}. Archive it to keep the history and clear it from your list.", color = Muted, fontSize = 13.sp)
            Button({ BatbloomApp.instance.appScope.launch { Meds.setArchived(ctx, m, true) }; onBack() }) { Text("Archive") }
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
                        days != null -> "${MedSchedule.fmtQty(m.stock)} left · lasts until ${DateTimeFormatter.ofPattern("EEE d MMM").format(MedSchedule.runsOutOn(m, today))}"
                        else -> "${MedSchedule.fmtQty(m.stock)} left"
                    },
                    color = if (days != null && days <= 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text("›", color = Muted, fontSize = 20.sp)
            }
        }
        if (Frequency.of(m.frequency) != Frequency.AsNeeded) {
            val taken = remember(history) { history.filter { it.status == DoseStatus.TAKEN }.mapNotNull { it.slot }.toSet() }
            var month by remember { mutableStateOf(java.time.YearMonth.from(today)) }
            val first = java.time.YearMonth.from(LocalDate.parse(m.startDate))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateTimeFormatter.ofPattern("MMMM yyyy").format(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton({ month = month.minusMonths(1) }, enabled = month > first) { Text("‹", fontSize = 20.sp) }
                    TextButton({ month = month.plusMonths(1) }, enabled = month < java.time.YearMonth.from(today)) { Text("›", fontSize = 20.sp) }
                }
                MonthDots(month, today) { d ->
                    val doses = if (d.isAfter(today)) emptyList() else MedSchedule.dosesOn(listOf(m), d)
                    DayDoses(d, doses.size, doses.count { it.slot in taken })
                }
                val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { !it.isAfter(today) }
                    .map { d -> MedSchedule.dosesOn(listOf(m), d).let { l -> l.size to l.count { it.slot in taken } } }
                val due = days.sumOf { it.first }
                if (due > 0) Text("${days.sumOf { it.second } * 100 / due}% of doses taken this month", color = Muted, fontSize = 13.sp)
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

internal fun parseQtyLine(m: MedEntity): String? {
    if (Frequency.of(m.frequency) == Frequency.AsNeeded) return null
    if (Frequency.of(m.frequency) == Frequency.Taper) return MedSchedule.parsePhases(m.times).joinToString("\n") { p ->
        "Days ${p.from}\u2013${p.to}: " + p.times.joinToString(", ") { "${timeFmt.format(it.time)} ${MedSchedule.qtyLabel(m, it.qty)}" }
    }
    return MedSchedule.parseTimes(m.times).joinToString(" · ") { "${timeFmt.format(it.time)}: ${MedSchedule.qtyLabel(m, it.qty)}" }
}
