package com.malluwayne.batbloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.meds.HistoryEntry
import com.malluwayne.batbloom.meds.MedHistory
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hTime = DateTimeFormatter.ofPattern("h:mm a")
private val hDay = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/**
 * Every dose you logged, all medications and varenicline together, newest first and grouped by day, like the
 * Health app's medication log. Filter by medication, see late doses, and share it as a spreadsheet.
 */
@Composable
fun MedHistoryScreen(modifier: Modifier, onBack: () -> Unit) {
    val app = BatbloomApp.instance
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // 90 days at a time; "Show older" reaches further back.
    var days by remember { mutableIntStateOf(90) }
    val since = remember(days, today) { today.minusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
    val logs by remember(since) { app.db.meds().logsAfter(since) }.collectAsState(emptyList())
    val meds by remember { app.db.meds().all() }.collectAsState(emptyList())
    val course by app.repo.doses.collectAsState(emptyList())
    val schedule by app.prefs.schedule.collectAsState()
    val all = remember(meds, logs, course, schedule, since) {
        MedHistory.entries(meds, logs, course.filter { it.actedAt >= since }, schedule, zone)
    }
    var filter by remember { mutableStateOf<String?>(null) } // a medication's name, or null for all
    val names = remember(all) { all.map { it.name }.distinct() }
    val shown = if (filter == null) all else all.filter { it.name == filter }
    val byName = remember(meds) { meds.associateBy { it.name } }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Medications") }
            Spacer(Modifier.weight(1f))
            TextButton({ shareCsv(ctx, shown) }, enabled = shown.isNotEmpty()) { Text("Share") }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                val (taken, due) = MedHistory.takenShare(shown.filter { !it.day.isBefore(today.minusDays(29)) })
                Text(
                    if (due > 0) "Last 30 days: ${taken * 100 / due}% of scheduled doses taken" else "Every dose you log shows here",
                    color = Muted, fontSize = 13.sp,
                )
            }
            if (names.size > 1) item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("All", filter == null) { filter = null }
                    names.forEach { n -> Chip(n, filter == n) { filter = if (filter == n) null else n } }
                }
            }
            if (shown.isEmpty()) item {
                SectionCard { Text("Nothing logged in the last $days days.", color = Muted) }
            }
            shown.groupBy { it.day }.forEach { (day, entries) ->
                item(key = day.toString()) {
                    Text(
                        when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> hDay.format(day) },
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item(key = "$day-list") {
                    SectionCard {
                        entries.forEach { e -> HistoryRow(e, byName[e.name]) }
                    }
                }
            }
            if (logs.isNotEmpty() || course.isNotEmpty()) item {
                TextButton({ days += 90 }, Modifier.fillMaxWidth()) { Text("Show older") }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Time on the left, the medication and dose, and a tick (taken) or cross (skipped). Late doses say so. */
@Composable
internal fun HistoryRow(e: HistoryEntry, med: MedEntity?, showArt: Boolean = true) {
    val taken = e.status == DoseStatus.TAKEN
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            hTime.format(if (taken) e.actedAt else e.scheduled ?: e.actedAt),
            Modifier.width(76.dp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (taken) MaterialTheme.colorScheme.onSurface else Muted,
        )
        if (showArt) Box(Modifier.padding(end = 10.dp)) { if (med != null) PillArt(med, 34.dp) else PillArt(VareniclineArt, 34.dp) }
        Column(Modifier.weight(1f)) {
            // In a single medication's day view the name is already known: the dose leads instead.
            if (showArt) Text(e.name, fontWeight = FontWeight.SemiBold, color = if (taken) MaterialTheme.colorScheme.onSurface else Muted)
            Text(e.detail, color = if (showArt) Muted else MaterialTheme.colorScheme.onSurface, fontSize = if (showArt) 12.sp else 14.sp)
            val late = e.minutesLate
            when {
                !taken -> Text("Skipped" + (e.scheduled?.let { " · ${hTime.format(it)} dose" } ?: ""), color = Muted, fontSize = 12.sp)
                late != null && late > 60 -> Text(
                    "${lateText(late)} late · ${hTime.format(e.scheduled)} dose" +
                        // Taken on a later day: say which, since the time alone would read as this day.
                        if (e.actedAt.toLocalDate() != e.day) " · taken ${DateTimeFormatter.ofPattern("EEE d MMM").format(e.actedAt)}" else "",
                    color = Gold, fontSize = 12.sp,
                )
                late != null && late < -60 -> Text("Early · ${hTime.format(e.scheduled)} dose", color = Muted, fontSize = 12.sp)
                e.scheduled == null -> Text("As needed", color = Muted, fontSize = 12.sp)
            }
        }
        Box(
            Modifier.size(24.dp).background(if (taken) Teal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (taken) Icons.Filled.Check else Icons.Filled.Close, if (taken) "Taken" else "Skipped",
                tint = if (taken) Color(0xFF0B2A26) else Muted, modifier = Modifier.size(15.dp))
        }
    }
}

private fun lateText(min: Long): String = when {
    min < 60 -> "${min}m"
    min < 24 * 60 -> "${min / 60}h" + if (min % 60 > 0) " ${min % 60}m" else ""
    else -> "${min / 1440}d" + if ((min % 1440) / 60 > 0) " ${(min % 1440) / 60}h" else ""
}

/** Shares the history as a spreadsheet file (opens in Sheets or Excel, or goes by mail). */
private fun shareCsv(ctx: android.content.Context, entries: List<HistoryEntry>) {
    runCatching {
        val dir = java.io.File(ctx.cacheDir, "shared").apply { mkdirs() }
        val file = java.io.File(dir, "batbloom-medication-history-${LocalDate.now()}.csv").apply {
            // The UTF-8 mark first, so Excel reads "·" and names in any language correctly (Sheets doesn't mind it).
            writeText(0xFEFF.toChar() + MedHistory.csv(entries))
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", file)
        ctx.startActivity(android.content.Intent.createChooser(
            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/csv")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Medication history")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share medication history",
        ))
    }
}
