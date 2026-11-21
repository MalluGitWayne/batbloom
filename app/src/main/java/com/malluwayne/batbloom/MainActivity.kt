package com.malluwayne.batbloom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.ui.BatbloomTheme
import com.malluwayne.batbloom.ui.FocusScreen
import com.malluwayne.batbloom.ui.GardenScreen
import com.malluwayne.batbloom.ui.QuitScreen
import com.malluwayne.batbloom.ui.InstallConfirmLauncher
import com.malluwayne.batbloom.update.Updates
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
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
        Updates.load(this)
        lifecycleScope.launch { Updates.dailyCheck(this@MainActivity) }
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
            ACTION_QUIT -> Nav.tab.value = 2
            ACTION_OPEN -> { Nav.tab.value = 0; Nav.shop.value = false }
        }
        if (intent?.action == ACTION_CRAVING || intent?.action == ACTION_FOCUS) Nav.tab.value = 0
        intent?.action = null
    }

    companion object {
        const val ACTION_OPEN = "batbloom.OPEN"
        const val ACTION_CRAVING = "batbloom.CRAVING"
        const val ACTION_FOCUS = "batbloom.FOCUS"
        const val ACTION_QUIT = "batbloom.QUIT"
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

/** Which tab is showing, and whether the shop is open. Widgets and notifications switch tabs through this. */
object Nav {
    val tab = MutableStateFlow(0)
    val shop = MutableStateFlow(false)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Focus("Focus", Icons.Filled.PlayArrow),
    Garden("Garden", Icons.Filled.Home),
    Quit("Quit", Icons.Filled.Favorite),
    Stats("Stats", Icons.Filled.DateRange),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    val tab by Nav.tab.collectAsState()
    val shop by Nav.shop.collectAsState()
    BackHandler(shop) { Nav.shop.value = false }
    InstallConfirmLauncher()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { Nav.tab.value = i; Nav.shop.value = false },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        if (shop) ShopScreen(m) else when (Tab.entries[tab]) {
            Tab.Focus -> FocusScreen(m)
            Tab.Garden -> GardenScreen(m)
            Tab.Quit -> QuitScreen(m)
            Tab.Stats -> StatsScreen(m)
            Tab.Settings -> SettingsScreen(m)
        }
    }
}
