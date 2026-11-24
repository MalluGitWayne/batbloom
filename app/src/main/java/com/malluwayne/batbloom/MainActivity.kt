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
import com.malluwayne.batbloom.ui.GardenTab
import com.malluwayne.batbloom.ui.StatsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val mode by BatbloomApp.instance.prefs.theme.collectAsState()
            val palette = com.malluwayne.batbloom.ui.resolvePalette(mode)
            androidx.compose.runtime.LaunchedEffect(palette.isLight) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (palette.isLight) androidx.activity.SystemBarStyle.light(t, t) else androidx.activity.SystemBarStyle.dark(t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            BatbloomTheme(palette) { AppRoot() }
        }
        Updates.load(this)
        lifecycleScope.launch { Updates.dailyCheck(this@MainActivity) }
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.malluwayne.batbloom.data.AutoBackup.runIfDue(this@MainActivity) }
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Time Guard also checks whenever you open Batbloom (besides its 15-minute timer).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.malluwayne.batbloom.guard.Reminders.checkGuard(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Each launch intent is handled once (not again when the screen rotates). It's marked with an extra rather
        // than by clearing its action, so the intent still matches the one that started the activity.
        if (intent == null || intent.getBooleanExtra(HANDLED, false)) return
        // Tell Android which shortcut was used, so the launcher can rank them.
        when (intent.action) { ACTION_FOCUS -> "focus"; ACTION_BREATHE -> "breathe"; ACTION_MEDS -> "dose"; else -> null }
            ?.let { androidx.core.content.pm.ShortcutManagerCompat.reportShortcutUsed(this, it) }
        when (intent.action) {
            ACTION_FOCUS -> Starter.fromPrefs(this)
            ACTION_QUIT -> Nav.tab.value = 2
            ACTION_OPEN -> Nav.tab.value = Tab.Home.ordinal
            ACTION_STATS -> { Nav.tab.value = 1; Nav.garden.value = 1 }
            ACTION_AWARDS -> { Nav.tab.value = 1; Nav.garden.value = 2 }
            ACTION_MEDS -> Nav.tab.value = 3
            ACTION_EXAM -> Nav.tab.value = Tab.Exam.ordinal
            ACTION_BREATHE -> { Nav.tab.value = Tab.Quit.ordinal; Nav.breathe.value = true }
            ACTION_OPEN_FOCUS -> Nav.tab.value = Tab.Focus.ordinal
            ACTION_FMGE -> {
                val p = BatbloomApp.instance.prefs
                p.setTag(Tags.FMGE)
                intent.getStringExtra(EXTRA_SUBJECT)?.let { p.setSubject(it) }
                Nav.tab.value = 0
            }
        }
        if (intent.action == ACTION_FOCUS) Nav.tab.value = 0
        intent.putExtra(HANDLED, true)
    }

    companion object {
        const val ACTION_OPEN = "batbloom.OPEN"
        const val ACTION_FOCUS = "batbloom.FOCUS"
        const val ACTION_QUIT = "batbloom.QUIT"
        const val ACTION_FMGE = "batbloom.FMGE"
        const val ACTION_STATS = "batbloom.STATS"
        const val ACTION_AWARDS = "batbloom.AWARDS"
        const val ACTION_MEDS = "batbloom.MEDS"
        const val ACTION_EXAM = "batbloom.EXAM"
        const val ACTION_BREATHE = "batbloom.BREATHE"
        const val ACTION_OPEN_FOCUS = "batbloom.OPEN_FOCUS"
        const val EXTRA_SUBJECT = "subject"
        private const val HANDLED = "batbloom.handled"
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
                plan = if (p.pomodoro.value && !p.stopwatch.value) p.plan.value else null,
                stopwatch = p.stopwatch.value,
            ),
        )
    }

}

/** Which tab is showing, and whether the shop is open. Widgets and notifications switch tabs through this. */
object Nav {
    val tab = MutableStateFlow(6) // Home
    /** Inside the Garden tab: 0 = garden, 1 = stats. */
    val garden = MutableStateFlow(0)
    /** Opens the craving breathing exercise on the Quit tab (from the shortcut). */
    val breathe = MutableStateFlow(false)
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Focus("Focus", Icons.Filled.PlayArrow),
    Garden("Garden", Icons.Filled.DateRange),
    Quit("Quit", Icons.Filled.Favorite),
    Meds("Meds", com.malluwayne.batbloom.ui.PillIcon),
    Settings("Settings", Icons.Filled.Settings),
    // Added last so the numbers above (used by notifications and widgets) stay the same.
    Exam("Exam", com.malluwayne.batbloom.ui.BookIcon),
    Home("Home", Icons.Filled.Home),
}

/** The order tabs show in the bar. */
/** Garden isn't in the bar: it opens from Home and Focus. */
private val TabOrder = listOf(Tab.Home, Tab.Focus, Tab.Exam, Tab.Quit, Tab.Meds, Tab.Settings)

@Composable
private fun AppRoot() {
    val rawTab by Nav.tab.collectAsState()
    val prefs = BatbloomApp.instance.prefs
    val medsTab by prefs.medsTab.collectAsState()
    val quitTab by prefs.quitTab.collectAsState()
    val examTab by prefs.examTab.collectAsState()
    val hidden = buildSet { if (!medsTab) add(Tab.Meds); if (!quitTab) add(Tab.Quit); if (!examTab) add(Tab.Exam) }
    // Tab numbers stay fixed (notifications and widgets use them); a hidden tab falls back to Focus.
    val tab = if (Tab.entries.getOrNull(rawTab)?.let { it in hidden } != false) Tab.Home.ordinal else rawTab
    // Back goes Home first (Garden goes back to where it was opened from: Home).
    BackHandler(enabled = tab != Tab.Home.ordinal) { Nav.tab.value = Tab.Home.ordinal }
    InstallConfirmLauncher()
    com.malluwayne.batbloom.ui.AchievementCelebrations()
    Scaffold(
        bottomBar = {
            NavigationBar {
                TabOrder.filter { it !in hidden }.forEach { t ->
                    val i = t.ordinal
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { Nav.tab.value = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, maxLines = 1, softWrap = false) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        androidx.compose.animation.Crossfade(tab.coerceIn(0, Tab.entries.size - 1), animationSpec = androidx.compose.animation.core.tween(260), label = "tab") { t ->
            when (Tab.entries[t]) {
                Tab.Focus -> FocusScreen(m)
                Tab.Garden -> GardenTab(m)
                Tab.Quit -> QuitScreen(m)
                Tab.Meds -> com.malluwayne.batbloom.ui.MedsScreen(m)
                Tab.Settings -> SettingsScreen(m)
                Tab.Exam -> com.malluwayne.batbloom.ui.ExamScreen(m)
                Tab.Home -> com.malluwayne.batbloom.ui.HomeScreen(m)
            }
        }
    }
}
