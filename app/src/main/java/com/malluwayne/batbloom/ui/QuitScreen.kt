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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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

@Composable
fun QuitScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val schedule by prefs.schedule.collectAsState()
    val quit by prefs.quitDate.collectAsState()
    val quitTime by prefs.quitTime.collectAsState()
    val smoking by prefs.smoking.collectAsState()
    val setupDone by prefs.smokingSetupDone.collectAsState()
    val stockLeft by prefs.tabletsLeft.collectAsState()
    val strength by prefs.tabletStrength.collectAsState()
    val closeHours by prefs.closeHours.collectAsState()
    val doses by app.repo.doses.collectAsState(emptyList())
    val badges by app.repo.badges.collectAsState(emptyList())
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val nowMs = rememberNow(30_000)
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val today = now.toLocalDate()
    val day = schedule.dayNumber(today)
    val logByKey = remember(doses) { doses.associateBy { it.key } }
    var stockDialog by remember { mutableStateOf(false) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    val warn = MaterialTheme.colorScheme.error

    LaunchedEffect(quit, today) { DoseAlarms.checkBadges(ctx) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Quit", style = MaterialTheme.typography.headlineMedium)
            Text(
                when {
                    day < 1 -> "Varenicline from ${dateFmt.format(schedule.start)}"
                    day > schedule.courseDays -> "Course finished ${dateFmt.format(schedule.endDate)}"
                    else -> "Day $day of ${schedule.courseDays} \u00B7 varenicline"
                },
                color = Muted, fontSize = 13.sp,
            )
            if (day in 1..schedule.courseDays) LinearProgressIndicator(
                { day.toFloat() / schedule.courseDays }, Modifier.fillMaxWidth().height(4.dp), drawStopIndicator = {},
            )
        }

        quit?.let { q ->
            val start = q.atTime(quitTime).atZone(zone)
            MilestoneHero(start, java.time.Duration.between(start, now), onQuitNow = { prefs.quitNow() })
        }

        // Today's tablets: one row per dose, tap the circle to tick it off
        val todaySlots = schedule.slotsOn(today)
        SectionCard("Today") {
            if (todaySlots.isEmpty()) Text("No doses today", color = Muted)
            todaySlots.forEach { slot ->
                val at = schedule.timeOf(slot, zone)
                val log = logByKey[slot.key]
                val overdue = log == null && now.isAfter(at.plusMinutes(60))
                DoseRow(
                    title = "${slot.label} \u00B7 ${Doses.formatMg(slot.mg)}",
                    detail = when (log?.status) {
                        DoseStatus.TAKEN -> "Taken ${timeFmt.format(Instant.ofEpochMilli(log.actedAt).atZone(zone))}"
                        DoseStatus.SKIPPED -> "Skipped"
                        else -> timeFmt.format(at) + " \u00B7 " + Doses.formatTablets(Doses.tablets(slot.mg, strength)).replace(" tablets", " \u00D7 ${Doses.formatMg(strength)}").replace(" tablet", " \u00D7 ${Doses.formatMg(strength)}")
                    },
                    status = log?.status,
                    overdue = overdue,
                    onToggle = { scope.launch { DoseAlarms.mark(ctx, slot, if (log == null) DoseStatus.TAKEN else null) } },
                    onSkip = if (log == null) ({ scope.launch { DoseAlarms.mark(ctx, slot, DoseStatus.SKIPPED) } }) else null,
                )
                if (overdue) {
                    val next = schedule.nextSlot(now)?.let { schedule.timeOf(it, zone) }
                    Text(
                        if (Doses.missedAdvice(now, next, closeHours) == Doses.Missed.TakeNow) "Missed \u00B7 take it now" else "Missed \u00B7 skip it, next dose is close",
                        color = Gold, fontSize = 12.sp, modifier = Modifier.padding(start = 52.dp),
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            // Stock, one line; tap to update
            val logged = todaySlots.filter { it.key in logByKey }.map { it.key }.toSet()
            val st = if (stockLeft >= 0) Doses.stock(schedule, stockLeft, strength, today, logged) else null
            Row(
                Modifier.fillMaxWidth().clickable { stockDialog = true }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        st == null -> "Add tablet stock"
                        st.runsOutOn == null -> "${Doses.formatTablets(stockLeft)} left \u00B7 enough for the course"
                        else -> "${Doses.formatTablets(stockLeft)} left \u00B7 until ${st.coveredUntil?.let { dateFmt.format(it) } ?: "today"}"
                    },
                    Modifier.weight(1f), fontSize = 13.sp,
                    color = if (st != null && Doses.lowStock(st)) warn else Muted,
                    fontWeight = if (st != null && Doses.lowStock(st)) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text("\u203A", color = Muted, fontSize = 18.sp)
            }
            schedule.nextSlot(now)?.takeIf { it.date != today }?.let {
                Text("Next \u00B7 ${dateFmt.format(it.date)}, ${timeFmt.format(schedule.timeOf(it, zone))} \u00B7 ${Doses.formatMg(it.mg)}", color = Muted, fontSize = 13.sp)
            }
        }

        // Savings since the quit day
        val q = quit
        if (q != null) {
            val p = QuitMath.progress(q.atTime(quitTime).atZone(zone), now, smoking.perDay, smoking.packSize, smoking.packPrice)
            if (!p.sinceQuit.isNegative) SectionCard {
                Row(Modifier.fillMaxWidth()) {
                    QuitFigure("${p.notSmoked}", "not smoked", Modifier.weight(1f))
                    QuitFigure(rupees(p.moneySaved), "saved", Modifier.weight(1f))
                    QuitFigure(QuitMath.formatDuration(p.lifeRegained), "life regained", Modifier.weight(1f))
                }
            }
        }

        BadgeShelf(badges.associate { it.id to it.earnedAt }, schedule, quit, sessions)

        androidx.compose.runtime.CompositionLocalProvider(LocalCollapsibleCards provides true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (doses.isNotEmpty()) SectionCard("Dose log") {
                    doses.take(14).forEach { d: DoseLogEntity ->
                        LabeledRow(
                            "Day ${d.day} \u00B7 ${if (d.key.endsWith("AM")) "morning" else "evening"} \u00B7 ${Doses.formatMg(d.mg)}",
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
            title = { Text("Quit tracker") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput("Cigarettes a day (before quitting)", perDay) { perDay = it }
                    NumberInput("Cigarettes per pack", pack) { pack = it }
                    NumberInput("Price per pack (\u20B9)", price) { price = it }
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
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(7)) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun QuitFigure(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

/** A dose: a round button to tick it off, the dose, and a quiet Skip while it is still open. */
@Composable
private fun DoseRow(title: String, detail: String, status: String?, overdue: Boolean, onToggle: () -> Unit, onSkip: (() -> Unit)?) {
    val teal = Teal
    val muted = Muted
    val outline = if (overdue) Gold else MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (status == DoseStatus.TAKEN) teal else androidx.compose.ui.graphics.Color.Transparent)
                .border(2.dp, if (status == DoseStatus.TAKEN) teal else if (status == DoseStatus.SKIPPED) muted else outline, androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                DoseStatus.TAKEN -> Text("\u2713", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                DoseStatus.SKIPPED -> Text("\u2013", color = muted, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (status != null) muted else MaterialTheme.colorScheme.onSurface)
            Text(detail, fontSize = 13.sp, color = if (status == DoseStatus.TAKEN) teal else muted)
        }
        if (onSkip != null) TextButton(onSkip) { Text("Skip", color = muted, fontSize = 13.sp) }
    }
}
