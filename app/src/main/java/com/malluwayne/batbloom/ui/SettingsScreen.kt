@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.core.net.toUri

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import com.malluwayne.batbloom.quit.DoseAlarms
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.focus.Device
import com.malluwayne.batbloom.focus.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val now = rememberNow(2_000)
    val hasUsage = remember(now) { Device.hasUsageAccess(ctx) }
    val hasNotif = remember(now) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val allowed by app.repo.allowedApps.collectAsState(emptySet())
    val custom by prefs.customTags.collectAsState()
    var showApps by remember { mutableStateOf(false) }

    androidx.compose.runtime.CompositionLocalProvider(LocalCollapsibleCards provides true) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // Theme: one tap, always visible.
        val mode by prefs.theme.collectAsState()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    mode == m, { prefs.setTheme(m) },
                    SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    icon = {},
                ) { Text(if (m == ThemeMode.Oled) "OLED" else m.label, maxLines = 1, fontSize = 13.sp) }
            }
        }

        val dndOn = remember(now) { com.malluwayne.batbloom.focus.Dnd.enabled(ctx) && com.malluwayne.batbloom.focus.Dnd.hasAccess(ctx) }
        val examTab by prefs.examTab.collectAsState()
        val quitTab by prefs.quitTab.collectAsState()
        val medsTab by prefs.medsTab.collectAsState()
        val bgOk = remember(now) { (ctx.getSystemService(android.os.PowerManager::class.java)).isIgnoringBatteryOptimizations(ctx.packageName) }
        val toFix = listOf(hasUsage, hasNotif, bgOk).count { !it }

        SectionCard(
            "Focus",
            subtitle = listOfNotNull(
                if (allowed.isEmpty()) "No allowed apps" else "${allowed.size} allowed app${if (allowed.size == 1) "" else "s"}",
                if (dndOn) "Do Not Disturb on" else null,
            ).joinToString(" · "),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allowed apps", Modifier.weight(1f))
                TextButton({ showApps = true }) { Text(if (allowed.isEmpty()) "Choose" else "${allowed.size} chosen  ›") }
            }
            DndRow(now)
            TimeGuardSection(now)
            if (custom.isNotEmpty()) SubSection("Custom tags") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    custom.forEach { t -> InputChip(false, { prefs.removeTag(t) }, { Text("$t  ✕") }) }
                }
            }
        }

        SectionCard(
            "Tabs",
            subtitle = listOfNotNull("Exam".takeIf { examTab }, "Quit".takeIf { quitTab }, "Meds".takeIf { medsTab })
                .joinToString(", ").ifBlank { "Only Home and Focus" },
        ) {
            TabSwitch("Exam", "FMGE countdown, subjects, questions and mock tests", "Hidden. The daily study reminder is paused.", examTab) {
                prefs.setExamTab(it); com.malluwayne.batbloom.Shortcuts.update(ctx); com.malluwayne.batbloom.guard.Reminders.scheduleAll(ctx)
            }
            TabSwitch("Quit", "Smoke-free timer, savings and milestones", "Hidden. Milestone notifications are paused.", quitTab) {
                prefs.setQuitTab(it); com.malluwayne.batbloom.Shortcuts.update(ctx); com.malluwayne.batbloom.guard.Reminders.scheduleMilestone(ctx)
            }
            TabSwitch("Meds", "Your medications and the varenicline course", "Hidden. Reminders for medications you added are paused.", medsTab) {
                prefs.setMedsTab(it); com.malluwayne.batbloom.Shortcuts.update(ctx)
                BatbloomApp.instance.appScope.launch { com.malluwayne.batbloom.meds.MedAlarms.scheduleNext(ctx) }
            }
        }

        SectionCard(
            "Permissions",
            subtitle = if (toFix == 0) "All set" else "$toFix to fix",
            subtitleColor = if (toFix == 0) null else Gold,
        ) {
            StatusRow("Usage access (leave detection)", hasUsage, "Turn on") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            StatusRow("Notifications", hasNotif, "Allow") {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            BackgroundSection(now)
        }

        BackupSection()
        UpdateSection()

        // About: a quiet footer instead of another card.
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(40)
            Text("Batbloom ${com.malluwayne.batbloom.update.Updates.appVersion(ctx)}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Data stays on this phone. Not a substitute for medical advice.", color = Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton({
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, ("package:${ctx.packageName}").toUri()))
            }) { Text("App info", fontSize = 13.sp) }
        }
    }

    }
    if (showApps) AllowListDialog(allowed) { showApps = false }
}

/** Do Not Disturb during sessions. Needs Android's "Do Not Disturb access" once; [now] re-checks it on return. */
@Composable
private fun DndRow(now: Long) {
    val ctx = LocalContext.current
    val access = remember(now) { com.malluwayne.batbloom.focus.Dnd.hasAccess(ctx) }
    var on by remember { mutableStateOf(com.malluwayne.batbloom.focus.Dnd.enabled(ctx)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Do Not Disturb while focusing")
            Text(
                when {
                    on && !access -> "Allow Do Not Disturb access to turn this on"
                    on -> "Silences notifications during a session. Medication reminders and alarms still come."
                    else -> "Off"
                },
                color = Muted, fontSize = 12.sp,
            )
        }
        Switch(on && access, {
            on = it; com.malluwayne.batbloom.focus.Dnd.setEnabled(ctx, it)
            if (it && !access) ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
    }
    if (on && !access) TextButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text("Allow access") }
}

@Composable
private fun TabSwitch(name: String, on: String, off: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name)
            Text(if (value) on else off, color = Muted, fontSize = 12.sp)
        }
        Switch(value, onChange)
    }
}
private data class AppRow(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun AllowListDialog(allowed: Set<String>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = BatbloomApp.instance.repo
    var apps by remember { mutableStateOf<List<AppRow>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launch, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != ctx.packageName }
                .map { ai ->
                    AppRow(
                        ai.packageName, pm.getApplicationLabel(ai).toString(),
                        runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Allowed apps", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClose) { Text("Done") }
                }
                Text("These apps can be used during a session without the plant wilting (e.g. a dictionary, your notes, a PDF reader).", color = Muted, fontSize = 13.sp)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search") }, singleLine = true)
                val list = apps
                if (list == null) Text("Loading apps…", color = Muted)
                else LazyColumn {
                    val shown = list.filter { query.isBlank() || it.label.contains(query, true) }
                        .sortedByDescending { it.pkg in allowed }
                    items(shown, key = { it.pkg }) { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            a.icon?.let { Image(it, null, Modifier.size(36.dp)) }
                            Text(a.label, Modifier.weight(1f).padding(start = 12.dp))
                            Checkbox(a.pkg in allowed, { on -> scope.launch { repo.setAllowed(a.pkg, on) } })
                        }
                    }
                }
            }
        }
    }
}
