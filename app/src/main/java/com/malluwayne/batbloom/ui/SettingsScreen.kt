@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

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
import com.malluwayne.batbloom.stats.Treat
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
    val quit by prefs.quitDate.collectAsState()
    val exam by prefs.examDate.collectAsState()
    val goal by prefs.dailyGoal.collectAsState()
    val custom by prefs.customTags.collectAsState()
    val smoking by prefs.smoking.collectAsState()
    val reason by prefs.quitReason.collectAsState()
    val treat by prefs.treat.collectAsState()
    var showApps by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<String?>(null) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionCard("Leave detection") {
            Text(
                "During a session Batbloom checks which app is on screen. Opening an app that is not allowed gives you " +
                    "${Rules.GRACE_MS / 1000} seconds to come back, then the plant wilts. Home screen, phone calls and the lock screen are always fine. " +
                    "This needs Usage access, and only runs while a plant is growing.",
                fontSize = 13.sp, color = Muted,
            )
            LabeledRow("Usage access", if (hasUsage) "On" else "Off")
            if (!hasUsage) Button({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Turn on Usage access") }
            LabeledRow("Allowed apps", "${allowed.size}")
            OutlinedButton({ showApps = true }) { Text("Choose allowed apps") }
        }

        SectionCard("Notifications") {
            LabeledRow("Countdown and alerts", if (hasNotif) "On" else "Off")
            if (!hasNotif) Button({
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                )
            }) { Text("Allow notifications") }
        }

        SectionCard("FMGE") {
            LabeledRow("Exam date", exam?.let { fmt.format(it) } ?: "Not set")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickDate = "exam" }) { Text(if (exam == null) "Set date" else "Change") }
                if (exam != null) TextButton({ prefs.setExamDate(null) }) { Text("Clear") }
            }
            Text("Daily study goal: ${Rules.formatMinutes(goal)}")
            Slider(goal.toFloat(), { prefs.setDailyGoal(((it / 30).roundToInt() * 30).coerceAtLeast(30)) }, valueRange = 30f..720f, steps = 22)
        }

        SectionCard("Quit smoking") {
            LabeledRow("Quit day", quit?.let { fmt.format(it) } ?: "Not set")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickDate = "quit" }) { Text(if (quit == null) "Set date" else "Change") }
                if (quit != null) TextButton({ prefs.setQuitDate(null) }) { Text("Clear") }
            }
            NumberField("Cigarettes a day (before quitting)", smoking.perDay.toString()) { v -> v.toIntOrNull()?.let { prefs.setSmoking(smoking.copy(perDay = it)) } }
            NumberField("Cigarettes per pack", smoking.packSize.toString()) { v -> v.toIntOrNull()?.let { prefs.setSmoking(smoking.copy(packSize = it)) } }
            NumberField("Price per pack (₹)", smoking.packPrice.roundToInt().toString()) { v -> v.toDoubleOrNull()?.let { prefs.setSmoking(smoking.copy(packPrice = it)) } }
            TextSetting("Why I'm quitting", reason, "e.g. To breathe easy on my runs") { prefs.setQuitReason(it) }
            TextSetting("Treat I'm saving for", treat.name, "e.g. New headphones") { prefs.setTreat(treat.copy(name = it)) }
            NumberField("Treat price (₹)", if (treat.price > 0) treat.price.roundToInt().toString() else "") { v ->
                prefs.setTreat(Treat(treat.name, v.toDoubleOrNull() ?: 0.0))
            }
        }

        SectionCard("Custom tags") {
            if (custom.isEmpty()) Text("Add your own from the tag row on the Focus screen.", color = Muted, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                custom.forEach { t -> InputChip(false, { prefs.removeTag(t) }, { Text("$t  ✕") }) }
            }
        }

        SectionCard("Widgets") {
            Text(
                "Long-press your home screen, tap Widgets and find Batbloom. The big widget shows the live countdown or today's focus, " +
                    "your FMGE goal and countdowns, with Craving and Focus buttons. The small one is a one-tap craving button.",
                fontSize = 13.sp, color = Muted,
            )
        }

        SectionCard("About") {
            Text(
                "Batbloom has no ads and no internet permission. Everything stays on this phone. Coins are earned by focusing only.",
                fontSize = 13.sp, color = Muted,
            )
            TextButton({
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }) { Text("App info") }
        }
    }

    if (showApps) AllowListDialog(allowed) { showApps = false }

    pickDate?.let { which ->
        val initial = (if (which == "quit") quit else exam) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "quit") prefs.setQuitDate(d) else prefs.setExamDate(d)
                    }
                    pickDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun NumberField(label: String, value: String, onDone: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        text, { text = it.filter { c -> c.isDigit() || c == '.' }.take(7); onDone(text) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TextSetting(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        text, { text = it.take(80); onChange(text) },
        label = { Text(label) }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
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
