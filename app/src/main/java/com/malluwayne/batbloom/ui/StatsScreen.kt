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
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
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

        // Three numbers, then the chart (it shows today's value at the top).
        Row(Modifier.fillMaxWidth()) {
            Figure(Rules.formatMinutes(s.todayMinutes), "today", Modifier.weight(1f))
            Figure(Rules.formatMinutes(s.weekMinutes), "this week", Modifier.weight(1f))
            Figure("${s.streakDays}d", if (s.bestStreak > s.streakDays) "streak · best ${s.bestStreak}" else "streak", Modifier.weight(1f), Gold)
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Focus time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                PillTabs(listOf("Week", "Month"), if (month) 1 else 0) { month = it == 1 }
            }
            val days = p.days
            val nameFmt = remember(locale) { java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", locale) }
            BarChart(
                values = days.map { it.second.toFloat() },
                labels = days.mapIndexed { i, (d, _) ->
                    when {
                        !month -> d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                        i % 7 == (days.size - 1) % 7 -> "${d.dayOfMonth}"
                        else -> ""
                    }
                },
                names = days.map { (d, _) -> if (d == today) "today" else nameFmt.format(d) },
                color = Lavender,
                format = { Rules.formatMinutes(it.toInt()) },
                reference = p.avgPerDay.toFloat().takeIf { it > 0f },
                referenceLabel = "average ${Rules.formatMinutes(p.avgPerDay)} a day",
            )
            Text("${Rules.formatMinutes(p.totalMinutes)} in $periodName · ${s.completed} blooms in all", color = Muted, fontSize = 12.sp)
        }

        // Insights: a few plain sentences, only when there is something to say.
        val insights = buildList {
            p.timeOfDay.maxByOrNull { it.second }?.let { (part, m) -> add("Best time of day" to "${part.lowercase().replaceFirstChar { it.uppercase() }} · ${Rules.formatMinutes(m)}") }
            if (p.sessions > 0) add("Average session" to Rules.formatMinutes(p.avgSession))
            p.bestDay?.let { (d, m) -> add("Best day" to "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${d.dayOfMonth} · ${Rules.formatMinutes(m)}") }
            if (p.sessions > 0) add("Bloomed, not wilted" to "${p.successRate}%")
        }
        if (insights.isNotEmpty()) SectionCard {
            insights.forEach { (label, value) -> LabeledRow(label, value) }
        }

        if (p.tagMinutes.size > 1) SectionCard("By tag · $periodName") {
            val max = p.tagMinutes.maxOf { it.second }.coerceAtLeast(1)
            p.tagMinutes.forEach { (t, m) -> HBar(t, Rules.formatMinutes(m), m.toFloat() / max, Lavender) }
        }

    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun HBar(label: String, value: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            Text(value, fontSize = 13.sp, color = Muted)
        }
        Meter(fraction, color)
    }
}
