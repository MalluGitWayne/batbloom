@file:OptIn(ExperimentalFoundationApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    var confirmClear by remember { mutableStateOf<Int?>(null) }
    var removeOne by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val repo = BatbloomApp.instance.repo
    confirmClear?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text("Clear wilted plants?") },
            text = { Text("Removes $n wilted plant${if (n == 1) "" else "s"} from your garden and stats. Your blooms stay.") },
            confirmButton = { TextButton({ confirmClear = null; scope.launch { repo.clearWilted() } }) { Text("Clear") } },
            dismissButton = { TextButton({ confirmClear = null }) { Text("Cancel") } },
        )
    }
    removeOne?.let { id ->
        AlertDialog(
            onDismissRequest = { removeOne = null },
            title = { Text("Remove this wilted plant?") },
            text = { Text("It disappears from your garden and stats.") },
            confirmButton = { TextButton({ removeOne = null; scope.launch { repo.removeWilted(id) } }) { Text("Remove") } },
            dismissButton = { TextButton({ removeOne = null }) { Text("Cancel") } },
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val blooms = shown.count { it.status == Status.DONE }
                val wilted = sessions.count { it.status == Status.FAILED }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$blooms bloomed · ${shown.size - blooms} wilted", color = Muted, modifier = Modifier.weight(1f))
                    if (wilted > 0) androidx.compose.material3.TextButton({ confirmClear = wilted }) { Text("Clear wilted") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Today", "This week").forEachIndexed { i, l -> FilterChip(filter == i, { filter = i }, { Text(l) }) }
                }
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "No plants yet.",
                color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp),
            )
        }
        items(shown, key = { it.id }) { s ->
            val ok = s.status == Status.DONE
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Panel)
                    .combinedClickable(onClick = {}, onLongClick = { if (!ok) removeOne = s.id })
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).plantTile()) {
                    PlantArt(PlantCatalog.byId(s.plantId), 1f, !ok, Modifier.fillMaxSize(), animate = false)
                }
                Text("${s.plannedMinutes}m · ${s.tag}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                s.subject?.let { Text(it, fontSize = 11.sp, color = Teal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(fmt.format(java.time.Instant.ofEpochMilli(s.startedAt).atZone(zone)), fontSize = 10.sp, color = Muted)
            }
        }
    }
}

/** Garden and Stats share one tab, switched at the top. */
@Composable
fun GardenTab(modifier: Modifier) {
    val sub by com.malluwayne.batbloom.Nav.garden.collectAsState()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listOf("Garden", "Stats", "Awards").getOrElse(sub) { "Garden" }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            PillTabs(listOf("Garden", "Stats", "Awards"), sub) { com.malluwayne.batbloom.Nav.garden.value = it }
        }
        when (sub) {
            1 -> StatsScreen(Modifier.weight(1f))
            2 -> AchievementsScreen(Modifier.weight(1f))
            else -> GardenScreen(Modifier.weight(1f))
        }
    }
}
