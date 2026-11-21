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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val goal by app.prefs.dailyGoal.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val s = remember(sessions, today) { Stats.compute(sessions, today, zone) }
    val c = remember(sessions, today) { Stats.cravings(sessions, today, zone) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Today", Rules.formatMinutes(s.todayMinutes), Modifier.weight(1f))
            StatTile("This week", Rules.formatMinutes(s.weekMinutes), Modifier.weight(1f))
            StatTile("All time", Rules.formatMinutes(s.allMinutes), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Streak", "${s.streakDays}d", Modifier.weight(1f), Gold)
            StatTile("Success", "${s.successRate}%", Modifier.weight(1f), Teal)
            StatTile("Blooms", "${s.completed}", Modifier.weight(1f), Teal)
        }

        SectionCard("Last 7 days") { Bars(s.last7.map { it.first to it.second.toDouble() }, Lavender) { Rules.formatMinutes(it.toInt()) } }

        if (s.tagMinutesWeek.isNotEmpty()) SectionCard("This week by tag") {
            val max = s.tagMinutesWeek.maxOf { it.second }.coerceAtLeast(1)
            s.tagMinutesWeek.forEach { (t, m) -> HBar(t, Rules.formatMinutes(m), m.toFloat() / max, Lavender) }
        }

        SectionCard("FMGE subjects") {
            Text("Today ${Rules.formatMinutes(s.fmgeTodayMinutes)} of ${Rules.formatMinutes(goal)} goal", color = Teal)
            val max = s.subjectMinutes.maxOf { it.second }.coerceAtLeast(1)
            val untouched = s.subjectMinutes.filter { it.second == 0 }.map { it.first }
            s.subjectMinutes.filter { it.second > 0 }.forEach { (n, m) -> HBar(n, Rules.formatMinutes(m), m.toFloat() / max, Teal) }
            if (untouched.isNotEmpty()) Text("Not started yet: ${untouched.joinToString(", ")}", color = Muted, fontSize = 12.sp)
        }

        SectionCard("Cravings") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Beaten", "${c.beaten}", Modifier.weight(1f), Gold)
                StatTile("Lost", "${c.lost}", Modifier.weight(1f), Muted)
            }
            if (c.intensity7.any { it.second != null }) {
                Text("Average strength (1-5), last 7 days", color = Muted, fontSize = 12.sp)
                Bars(c.intensity7.map { it.first to (it.second ?: 0.0) }, Gold, maxValue = 5.0) { if (it == 0.0) "" else "%.1f".format(it) }
            }
            if (c.topTriggers.isNotEmpty()) {
                Text("Top triggers", color = Muted, fontSize = 12.sp)
                val max = c.topTriggers.maxOf { it.second }
                c.topTriggers.forEach { (t, n) -> HBar(t, "$n", n.toFloat() / max, Gold) }
            }
            if (c.beaten + c.lost == 0) Text("Tap the craving button when one hits. Each one you ride out is counted here.", color = Muted, fontSize = 13.sp)
        }
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
private fun Bars(data: List<Pair<LocalDate, Double>>, color: Color, maxValue: Double? = null, label: (Double) -> String) {
    val max = maxValue ?: data.maxOf { it.second }.coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val gap = size.width / data.size
        val bw = gap * 0.55f
        data.forEachIndexed { i, (_, v) ->
            val h = (v / max).toFloat() * size.height
            val x = i * gap + (gap - bw) / 2
            drawRoundRect(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Size(bw, size.height), CornerRadius(8f))
            if (h > 0) drawRoundRect(color, Offset(x, size.height - h), Size(bw, h), CornerRadius(8f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        data.forEach { (d, v) ->
            Column(Modifier.weight(1f)) {
                Text(label(v), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp, color = Muted, maxLines = 1)
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 11.sp)
            }
        }
    }
}
