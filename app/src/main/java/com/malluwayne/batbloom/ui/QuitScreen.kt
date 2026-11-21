@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.malluwayne.batbloom.Starter
import com.malluwayne.batbloom.data.DoseLogEntity
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.quit.Badges
import com.malluwayne.batbloom.quit.DoseAlarms
import com.malluwayne.batbloom.quit.Doses
import com.malluwayne.batbloom.quit.Milestones
import com.malluwayne.batbloom.quit.QuitMath
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

val SideEffectKinds = listOf("Nausea", "Sleep trouble", "Vivid dreams", "Mood change", "Headache", "Constipation", "Other")
private val Warn = Color(0xFFFF8A80)

@Composable
fun QuitScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val schedule by prefs.schedule.collectAsState()
    val quit by prefs.quitDate.collectAsState()
    val smoking by prefs.smoking.collectAsState()
    val setupDone by prefs.smokingSetupDone.collectAsState()
    val stockLeft by prefs.tabletsLeft.collectAsState()
    val strength by prefs.tabletStrength.collectAsState()
    val closeHours by prefs.closeHours.collectAsState()
    val doses by app.repo.doses.collectAsState(emptyList())
    val slips by app.repo.slips.collectAsState(emptyList())
    val effects by app.repo.sideEffects.collectAsState(emptyList())
    val badges by app.repo.badges.collectAsState(emptyList())
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val nowMs = rememberNow(30_000)
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val today = now.toLocalDate()
    val day = schedule.dayNumber(today)
    val logByKey = remember(doses) { doses.associateBy { it.key } }
    var slipDialog by remember { mutableStateOf(false) }
    var effectDialog by remember { mutableStateOf(false) }
    var stockDialog by remember { mutableStateOf(false) }
    var doctorAlert by remember { mutableStateOf(false) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM") }

    LaunchedEffect(Unit) { DoseAlarms.checkBadges(ctx) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Quit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            when {
                day < 1 -> "Varenicline course starts ${dateFmt.format(schedule.start)}"
                day > schedule.courseDays -> "Course finished on ${dateFmt.format(schedule.endDate)}"
                else -> "Day $day of ${schedule.courseDays} · varenicline"
            },
            color = Lavender,
        )
        if (day in 1..schedule.courseDays) LinearProgressIndicator({ day.toFloat() / schedule.courseDays }, Modifier.fillMaxWidth())

        // SOS
        Button(
            onClick = { Starter.craving(ctx); Nav.tab.value = 0 },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = MaterialTheme.colorScheme.onTertiary),
        ) { Text("Craving SOS · 10 minutes", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

        // Health milestones, front and centre
        quit?.let { MilestoneHero(it, java.time.Duration.between(it.atStartOfDay(zone), now)) }

        // Today's doses
        val todaySlots = schedule.slotsOn(today)
        SectionCard("Today's tablets") {
            if (todaySlots.isEmpty()) Text("No doses today.", color = Muted)
            todaySlots.forEach { slot ->
                val at = schedule.timeOf(slot, zone)
                val log = logByKey[slot.key]
                val tablets = Doses.tablets(slot.mg, strength)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${slot.label} · ${Doses.formatMg(slot.mg)}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${timeFmt.format(at)} · ${Doses.formatTablets(tablets)} of ${Doses.formatMg(strength)}" +
                                when (log?.status) {
                                    DoseStatus.TAKEN -> " · taken ${timeFmt.format(Instant.ofEpochMilli(log.actedAt).atZone(zone))}"
                                    DoseStatus.SKIPPED -> " · skipped"
                                    else -> ""
                                },
                            color = if (log?.status == DoseStatus.TAKEN) Teal else Muted, fontSize = 13.sp,
                        )
                    }
                    if (log == null) {
                        TextButton({ scope.launch { DoseAlarms.mark(ctx, slot, DoseStatus.TAKEN) } }) { Text("Taken") }
                        TextButton({ scope.launch { DoseAlarms.mark(ctx, slot, DoseStatus.SKIPPED) } }) { Text("Skip", color = Muted) }
                    } else {
                        TextButton({ scope.launch { DoseAlarms.mark(ctx, slot, null) } }) { Text("Undo", color = Muted) }
                    }
                }
                // Missed dose advice
                if (log == null && now.isAfter(at.plusMinutes(60))) {
                    val next = schedule.nextSlot(now)?.let { schedule.timeOf(it, zone) }
                    val advice = Doses.missedAdvice(now, next, closeHours)
                    Text(
                        if (advice == Doses.Missed.TakeNow) "Missed? Take it now, after food."
                        else "Your next dose is close, so skip this one. Never take two at once.",
                        color = Gold, fontSize = 13.sp,
                    )
                }
            }
            schedule.nextSlot(now)?.let {
                Text("Next: ${it.label.lowercase()} ${Doses.formatMg(it.mg)}, ${dateFmt.format(it.date)} at ${timeFmt.format(schedule.timeOf(it, zone))}", fontSize = 13.sp)
            }
        }

        // Stock
        SectionCard("Tablet stock") {
            if (stockLeft < 0) {
            } else {
                val logged = todaySlots.filter { it.key in logByKey }.map { it.key }.toSet()
                val st = Doses.stock(schedule, stockLeft, strength, today, logged)
                LabeledRow("Left", "${Doses.formatTablets(stockLeft)} of ${Doses.formatMg(strength)}")
                Text(
                    when {
                        st.runsOutOn == null -> "Enough for the rest of the course."
                        else -> "Enough until ${st.coveredUntil?.let { dateFmt.format(it) } ?: "today"}."
                    },
                    color = if (Doses.lowStock(st)) Warn else Muted, fontSize = 13.sp,
                )
                if (Doses.lowStock(st)) Text("Running low: get your refill now.", color = Warn, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton({ stockDialog = true }) { Text(if (stockLeft < 0) "Enter stock" else "Update stock") }
        }

        // Quit tracker
        val q = quit
        val slipped = slips.sumOf { it.count }
        SectionCard("Smoke-free") {
            if (q == null) Text("Set your quit day in Settings.", color = Muted)
            else {
                val p = QuitMath.progress(q.atStartOfDay(zone), now, smoking.perDay, smoking.packSize, smoking.packPrice, slipped)
                Text(Stats.quitLine(q, today), color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (!p.sinceQuit.isNegative) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Smoke-free", QuitMath.formatDuration(p.sinceQuit), Modifier.weight(1f), Gold)
                        StatTile("Not smoked", "${p.notSmoked}", Modifier.weight(1f), Teal)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Saved", rupees(p.moneySaved), Modifier.weight(1f), Teal)
                        StatTile("Life regained", QuitMath.formatDuration(p.lifeRegained), Modifier.weight(1f), Lavender)
                    }
                }
            }
        }

        // Everything below folds up: tap a section to open it.
        androidx.compose.runtime.CompositionLocalProvider(LocalCollapsibleCards provides true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Cravings chart
        val cravings = remember(sessions) { sessions.filter { it.tag == com.malluwayne.batbloom.data.Tags.CRAVING } }
        SectionCard("Cravings, last 14 days") {
            val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
            val byDay = cravings.groupBy { Stats.dayOf(it.startedAt, zone) }
            val max = days.maxOf { byDay[it]?.size ?: 0 }.coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val gap = size.width / days.size
                days.forEachIndexed { i, d ->
                    val list = byDay[d].orEmpty()
                    val won = list.count { it.status == com.malluwayne.batbloom.data.Status.DONE }
                    val lost = list.size - won
                    val unit = size.height / max
                    val x = i * gap + gap * 0.2f
                    val w = gap * 0.6f
                    drawRect(Gold, androidx.compose.ui.geometry.Offset(x, size.height - won * unit), androidx.compose.ui.geometry.Size(w, won * unit))
                    drawRect(Muted, androidx.compose.ui.geometry.Offset(x, size.height - (won + lost) * unit), androidx.compose.ui.geometry.Size(w, lost * unit))
                }
            }
            Text("Gold: resisted · grey: not resisted. Total ${cravings.size}, resisted ${cravings.count { it.status == com.malluwayne.batbloom.data.Status.DONE }}.", color = Muted, fontSize = 12.sp)
        }

        // Slips
        SectionCard("Slips") {
            OutlinedButton({ slipDialog = true }) { Text("Log a slip") }
            slips.take(5).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${dateFmt.format(Instant.ofEpochMilli(s.at).atZone(zone))} · ${s.count} cigarette${if (s.count == 1) "" else "s"}" + (s.note?.let { " · $it" } ?: ""), Modifier.weight(1f), fontSize = 13.sp)
                    TextButton({ scope.launch { app.repo.deleteSlip(s.id) } }) { Text("✕", color = Muted) }
                }
            }
        }

        // Side effects
        SectionCard("Side-effect diary") {
            SectionCard(container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)) {
                Text(
                    "Stop and contact your doctor straight away if you notice changes in mood or behaviour (such as depression, agitation or thoughts of self-harm), " +
                        "a rash or swelling, trouble breathing, chest pain or a seizure. In an emergency, call your local emergency number.",
                    fontSize = 13.sp,
                )
            }
            OutlinedButton({ effectDialog = true }) { Text("Add an entry") }
            effects.take(8).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${dateFmt.format(Instant.ofEpochMilli(e.at).atZone(zone))} · ${e.kind} · ${listOf("", "mild", "moderate", "severe")[e.severity]}" + (e.note?.let { " · $it" } ?: ""),
                        Modifier.weight(1f), fontSize = 13.sp, color = if (e.kind == "Mood change") Warn else MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton({ scope.launch { app.repo.deleteSideEffect(e.id) } }) { Text("✕", color = Muted) }
                }
            }
        }

        // Badges
        SectionCard("Badges") {
            val have = badges.map { it.id }.toSet()
            Badges.all.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (b.id in have) "★" else "☆", color = if (b.id in have) Gold else Muted, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, fontWeight = FontWeight.SemiBold, color = if (b.id in have) MaterialTheme.colorScheme.onSurface else Muted)
                        Text(b.text, fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }

        // Dose log
        if (doses.isNotEmpty()) SectionCard("Dose log") {
            doses.take(14).forEach { d: DoseLogEntity ->
                LabeledRow(
                    "Day ${d.day} · ${if (d.key.endsWith("AM")) "morning" else "evening"} · ${Doses.formatMg(d.mg)}",
                    if (d.status == DoseStatus.TAKEN) "Taken ${timeFmt.format(Instant.ofEpochMilli(d.actedAt).atZone(zone))}" else "Skipped",
                )
            }
        }

        }
        }
    }

    // First-run setup for the tracker
    if (!setupDone) {
        var perDay by remember { mutableStateOf(smoking.perDay.toString()) }
        var price by remember { mutableStateOf(smoking.packPrice.roundToInt().toString()) }
        var pack by remember { mutableStateOf(smoking.packSize.toString()) }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Set up your quit tracker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("Cigarettes a day (before quitting)", perDay) { perDay = it }
                    NumberInput("Cigarettes per pack", pack) { pack = it }
                    NumberInput("Price per pack (₹)", price) { price = it }
                }
            },
            confirmButton = {
                TextButton({
                    prefs.setSmoking(
                        smoking.copy(
                            perDay = perDay.toIntOrNull() ?: smoking.perDay,
                            packSize = pack.toIntOrNull()?.takeIf { it > 0 } ?: smoking.packSize,
                            packPrice = price.toDoubleOrNull() ?: smoking.packPrice,
                        ),
                    )
                    prefs.setSmokingSetupDone()
                }) { Text("Save") }
            },
        )
    }

    if (stockDialog) {
        var count by remember { mutableStateOf(if (stockLeft >= 0) stockLeft.toString().removeSuffix(".0") else "") }
        var mg by remember { mutableStateOf(strength) }
        AlertDialog(
            onDismissRequest = { stockDialog = false },
            title = { Text("Tablet stock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("Tablets left", count) { count = it }
                    Text("Strength per tablet", color = Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5, 1.0).forEach { v -> FilterChip(mg == v, { mg = v }, { Text(Doses.formatMg(v)) }) }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    count.toDoubleOrNull()?.let { prefs.setTabletsLeft(it) }
                    prefs.setTabletStrength(mg)
                    prefs.lastStockWarning = ""
                    stockDialog = false
                    scope.launch { DoseAlarms.checkStock(ctx) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ stockDialog = false }) { Text("Cancel") } },
        )
    }

    if (slipDialog) {
        var count by remember { mutableStateOf("1") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { slipDialog = false },
            title = { Text("Log a slip") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Thanks for being honest. One slip doesn't undo your progress. Keep taking your tablets as prescribed.", fontSize = 13.sp)
                    NumberInput("How many cigarettes", count) { count = it }
                    OutlinedTextField(note, { note = it.take(80) }, label = { Text("What happened? (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    scope.launch { app.repo.addSlip(count.toIntOrNull()?.coerceAtLeast(1) ?: 1, note.ifBlank { null }) }
                    slipDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ slipDialog = false }) { Text("Cancel") } },
        )
    }

    if (effectDialog) {
        var kind by remember { mutableStateOf(SideEffectKinds.first()) }
        var severity by remember { mutableStateOf(1) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { effectDialog = false },
            title = { Text("Side-effect diary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SideEffectKinds.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(k, fontSize = 12.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Mild", "Moderate", "Severe").forEachIndexed { i, l -> FilterChip(severity == i + 1, { severity = i + 1 }, { Text(l) }) }
                    }
                    OutlinedTextField(note, { note = it.take(120) }, label = { Text("Notes (optional)") })
                }
            },
            confirmButton = {
                TextButton({
                    scope.launch { app.repo.addSideEffect(kind, severity, note.ifBlank { null }) }
                    effectDialog = false
                    if (kind == "Mood change" || severity == 3) doctorAlert = true
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ effectDialog = false }) { Text("Cancel") } },
        )
    }

    if (doctorAlert) AlertDialog(
        onDismissRequest = { doctorAlert = false },
        title = { Text("Please talk to your doctor") },
        text = {
            Text(
                "Changes in mood or behaviour, or any severe side effect, should be checked by your doctor promptly. " +
                    "If you have thoughts of harming yourself, trouble breathing, chest pain, swelling or a seizure, get medical help now.",
            )
        },
        confirmButton = { TextButton({ doctorAlert = false }) { Text("OK") } },
    )
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(7)) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
