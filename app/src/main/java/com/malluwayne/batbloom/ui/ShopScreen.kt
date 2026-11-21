package com.malluwayne.batbloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.plants.NightSky
import com.malluwayne.batbloom.plants.PlantArt
import com.malluwayne.batbloom.plants.PlantCatalog
import kotlinx.coroutines.launch

@Composable
fun ShopScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val coins by app.repo.coins.collectAsState(0)
    val owned by app.repo.unlocked.collectAsState(setOf("moonflower"))
    val selected by app.prefs.selectedPlant.collectAsState()
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("Seed shop", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("You have ● $coins coins. Earn 1 per minute of focus, +5 for every full half hour.", color = Muted)
            }
        }
        items(PlantCatalog.all) { p ->
            val have = p.id in owned
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) {
                        NightSky(Modifier.fillMaxSize(), animate = false)
                        PlantArt(p, 1f, false, Modifier.fillMaxSize(), animate = false)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(p.description, color = Muted, fontSize = 13.sp)
                    }
                }
                when {
                    have && p.id == selected -> OutlinedButton({}, enabled = false) { Text("Planting this") }
                    have -> OutlinedButton({ app.prefs.setSelectedPlant(p.id) }) { Text("Use") }
                    else -> Button(
                        { scope.launch { app.repo.unlock(p.id, p.cost); app.prefs.setSelectedPlant(p.id) } },
                        enabled = Rules.canUnlock(coins, p.cost, have),
                    ) { Text("Unlock · ● ${p.cost}") }
                }
            }
        }
    }
}
