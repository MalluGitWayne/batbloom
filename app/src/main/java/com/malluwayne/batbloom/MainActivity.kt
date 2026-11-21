package com.malluwayne.batbloom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.ui.BatbloomTheme
import com.malluwayne.batbloom.ui.FocusScreen
import com.malluwayne.batbloom.ui.GardenScreen
import com.malluwayne.batbloom.ui.SettingsScreen
import com.malluwayne.batbloom.ui.ShopScreen
import com.malluwayne.batbloom.ui.StatsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { BatbloomTheme { AppRoot() } }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_CRAVING -> Starter.craving(this)
            ACTION_FOCUS -> Starter.fromPrefs(this)
        }
        intent?.action = null
    }

    companion object {
        const val ACTION_OPEN = "batbloom.OPEN"
        const val ACTION_CRAVING = "batbloom.CRAVING"
        const val ACTION_FOCUS = "batbloom.FOCUS"
    }
}

/** Starts sessions from the saved choices (Focus button, widget). */
object Starter {
    fun fromPrefs(context: Context) {
        val p = BatbloomApp.instance.prefs
        val tag = p.tag.value
        FocusEngine.start(
            context,
            FocusEngine.Request(
                minutes = p.minutes.value,
                tag = tag,
                subject = if (tag == Tags.FMGE) p.subject.value else null,
                plantId = p.selectedPlant.value,
                plan = if (p.pomodoro.value) p.plan.value else null,
            ),
        )
    }

    fun craving(context: Context) {
        val p = BatbloomApp.instance.prefs
        FocusEngine.start(context, FocusEngine.Request(Rules.CRAVING_MINUTES, Tags.CRAVING, null, p.selectedPlant.value))
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Focus("Focus", Icons.Filled.PlayArrow),
    Garden("Garden", Icons.Filled.Home),
    Stats("Stats", Icons.Filled.DateRange),
    Shop("Shop", Icons.Filled.ShoppingCart),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (Tab.entries[tab]) {
            Tab.Focus -> FocusScreen(m)
            Tab.Garden -> GardenScreen(m)
            Tab.Stats -> StatsScreen(m)
            Tab.Shop -> ShopScreen(m)
            Tab.Settings -> SettingsScreen(m)
        }
    }
}
