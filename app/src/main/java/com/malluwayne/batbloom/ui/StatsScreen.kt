package com.malluwayne.batbloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.stats.Stats
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val sessions by app.repo.sessions.collectAsState(emptyList())
    var month by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val s = remember(sessions, today) { Stats.compute(sessions, today, zone) }
    val p = remember(sessions, today, month) { com.malluwayne.batbloom.stats.Periods.summary(sessions, today, zone, if (month) 30 else 7) }
    val periodName = if (month) "30 days" else "7 days"

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        val plant by app.prefs.selectedPlant.collectAsState()
        if (sessions.isEmpty()) {
            // Nothing yet: one calm card instead of a page of zeros.
            SectionCard {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp))) {
                    com.malluwayne.batbloom.plants.NightSky(Modifier.fillMaxSize(), animate = false)
                    com.malluwayne.batbloom.plants.PlantArt(
                        com.malluwayne.batbloom.plants.PlantCatalog.byId(plant), 0.25f, false,
                        Modifier.fillMaxSize(), animate = false,
                    )
                }
                Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }

        // Today, big; the rest as three small numbers.
        Column {
            Text(Rules.formatMinutes(s.todayMinutes), fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("focused today", color = Muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth()) {
            Figure(Rules.formatMinutes(s.weekMinutes), "this week", Modifier.weight(1f))
            Figure("${s.streakDays} day${if (s.streakDays == 1) "" else "s"}", if (s.bestStreak > s.streakDays) "streak \u00B7 best ${s.bestStreak}" else "streak", Modifier.weight(1f), Gold)
            Figure("${s.completed}", "blooms", Modifier.weight(1f), Lavender)
        }

        // Period switch and chart
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Focus time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            PillTabs(listOf("Week", "Month"), if (month) 1 else 0) { month = it == 1 }
        }
        SectionCard {
            Bars(p.days.map { it.first to it.second.toDouble() }, Lavender, today = today, compact = month)
            Text(
                "${Rules.formatMinutes(p.totalMinutes)} in $periodName \u00B7 about ${Rules.formatMinutes(p.avgPerDay)} a day",
                color = Muted, fontSize = 12.sp,
            )
        }

        // Insights: a few plain sentences, only when there is something to say.
        val insights = buildList {
            p.timeOfDay.maxByOrNull { it.second }?.let { (part, m) -> add("You focus best in the ${part.lowercase()}" to Rules.formatMinutes(m)) }
            if (p.sessions > 0) add("Average session" to Rules.formatMinutes(p.avgSession))
            p.bestDay?.let { (d, m) -> add("Best day: ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${d.dayOfMonth}" to Rules.formatMinutes(m)) }
            if (p.sessions > 0) add("Bloomed, not wilted" to "${p.successRate}%")
        }
        if (insights.isNotEmpty()) SectionCard {
            insights.forEach { (label, value) -> LabeledRow(label, value) }
        }

        // FMGE subjects live in the Exam tab now.

        if (p.tagMinutes.size > 1) SectionCard("By tag \u00B7 $periodName") {
            val max = p.tagMinutes.maxOf { it.second }.coerceAtLeast(1)
            p.tagMinutes.forEach { (t, m) -> HBar(t, Rules.formatMinutes(m), m.toFloat() / max, Lavender) }
        }

    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun HBar(label: String, value: String, fraction: Float, color: Color) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            Text(value, fontSize = 13.sp, color = Muted)
        }
        LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth().height(6.dp), color = color, drawStopIndicator = {})
    }
}

@Composable
private fun Bars(data: List<Pair<LocalDate, Double>>, color: Color, today: LocalDate, compact: Boolean) {
    val max = data.maxOf { it.second }.coerceAtLeast(1.0)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val desc = "Focus time: " + data.filter { it.second > 0 }.joinToString(", ") { (d, v) -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${Rules.formatMinutes(v.toInt())}" }.ifBlank { "no sessions" }
    Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = desc }) {
        val gap = size.width / data.size
        val bw = gap * if (compact) 0.6f else 0.42f
        val r = CornerRadius(bw / 2, bw / 2)
        data.forEachIndexed { i, (d, v) ->
            val h = (v / max).toFloat() * size.height
            val x = i * gap + (gap - bw) / 2
            drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), r)
            if (h > 0) drawRoundRect(if (d == today) color else color.copy(alpha = 0.55f), Offset(x, size.height - h), Size(bw, h), r)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        data.forEachIndexed { i, (d, _) ->
            val show = !compact || i % 7 == (data.size - 1) % 7
            Text(
                if (!show) "" else if (compact) "${d.dayOfMonth}" else d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, maxLines = 1,
                color = if (d == today) MaterialTheme.colorScheme.onSurface else Muted,
                fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
