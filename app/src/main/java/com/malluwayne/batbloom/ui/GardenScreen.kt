package com.malluwayne.batbloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.plants.PlantArt
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.stats.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun GardenScreen(modifier: Modifier) {
    val sessions by BatbloomApp.instance.repo.sessions.collectAsState(emptyList())
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val shown = remember(sessions, filter) {
        sessions.filter {
            val d = Stats.dayOf(it.startedAt, zone)
            when (filter) { 1 -> d == today; 2 -> !d.isBefore(weekStart); else -> true }
        }
    }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm") }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your garden", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val blooms = shown.count { it.status == Status.DONE }
                Text("$blooms bloomed · ${shown.size - blooms} wilted", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Today", "This week").forEachIndexed { i, l -> FilterChip(filter == i, { filter = i }, { Text(l) }) }
                }
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Nothing here yet. Finish a focus session and its plant is planted here.",
                color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp),
            )
        }
        items(shown, key = { it.id }) { s ->
            val ok = s.status == Status.DONE
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(84.dp)) {
                    PlantArt(PlantCatalog.byId(s.plantId), 1f, !ok, Modifier.fillMaxSize(), animate = false)
                }
                Text("${s.plannedMinutes}m · ${s.tag}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                s.subject?.let { Text(it, fontSize = 11.sp, color = Teal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(fmt.format(java.time.Instant.ofEpochMilli(s.startedAt).atZone(zone)), fontSize = 10.sp, color = Muted)
            }
        }
    }
}
