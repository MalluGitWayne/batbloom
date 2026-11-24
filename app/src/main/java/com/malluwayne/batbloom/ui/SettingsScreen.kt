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
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val schedule by prefs.schedule.collectAsState()
    val reminders by prefs.reminders.collectAsState()
    val snooze by prefs.snoozeMinutes.collectAsState()
    val close by prefs.closeHours.collectAsState()
    val exactOk = remember(now) { DoseAlarms.canExact(ctx) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(schedule) { DoseAlarms.scheduleNext(ctx) }

    androidx.compose.runtime.CompositionLocalProvider(LocalCollapsibleCards provides true) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        UpdateSection()

        SectionCard("Appearance") {
            val mode by prefs.theme.collectAsState()
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    androidx.compose.material3.FilterChip(mode == m, { prefs.setTheme(m) }, { Text(m.label) })
                }
            }
        }

        SectionCard("Permissions") {
            StatusRow("Usage access (leave detection)", hasUsage, "Turn on") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            StatusRow("Notifications", hasNotif, "Allow") {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            BackgroundSection(now)
        }

        SectionCard("Focus") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allowed apps", Modifier.weight(1f))
                TextButton({ showApps = true }) { Text(if (allowed.isEmpty()) "Choose" else "${allowed.size} chosen  \u203A") }
            }
            TimeGuardSection(now)
            if (custom.isNotEmpty()) SubSection("Custom tags") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    custom.forEach { t -> InputChip(false, { prefs.removeTag(t) }, { Text("$t  \u2715") }) }
                }
            }
        }

        SectionCard("FMGE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exam date", Modifier.weight(1f))
                TextButton({ pickDate = "exam" }) { Text(exam?.let { fmt.format(it) } ?: "Set") }
                if (exam != null) TextButton({ prefs.setExamDate(null) }) { Text("\u2715", color = Muted) }
            }
            Text("Daily goal: ${Rules.formatMinutes(goal)}")
            Slider(
                goal.toFloat(), { prefs.setDailyGoal(((it / 30).roundToInt() * 30).coerceAtLeast(30)) }, valueRange = 30f..720f, steps = 22,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            StudyReminderSection()
        }

        SectionCard("Quit smoking") {
            val quitTime by prefs.quitTime.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quit day", Modifier.weight(1f))
                TextButton({ pickDate = "quit" }) { Text(quit?.let { fmt.format(it) } ?: "Set") }
            }
            if (quit != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last cigarette at", Modifier.weight(1f))
                TextButton({ pickTime = "quit" }) { Text(timeFmt.format(quitTime)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ prefs.quitNow() }) { Text("I've quit now") }
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

        SectionCard("Varenicline course") {
            LabeledRow("Day 1", fmt.format(schedule.start))
            LabeledRow("Last day", "Day ${schedule.courseDays}, ${fmt.format(schedule.endDate)}")
            OutlinedButton({ pickDate = "course" }) { Text("Change start date") }
            Stepper2("Course length (days)", schedule.courseDays, 7, 200, 7) { prefs.setSchedule(schedule.copy(courseDays = it)) }
            Stepper2("Phase 1 ends on day", schedule.phase1End, 1, schedule.phase2End, 1) { prefs.setSchedule(schedule.copy(phase1End = it)) }
            Stepper2("Phase 2 ends on day", schedule.phase2End, schedule.phase1End, schedule.courseDays, 1) { prefs.setSchedule(schedule.copy(phase2End = it)) }
            val ranges = listOf("Days 1–${schedule.phase1End}", "Days ${schedule.phase1End + 1}–${schedule.phase2End}", "Days ${schedule.phase2End + 1}–${schedule.courseDays}")
            schedule.mg.forEachIndexed { i, (am, pm) ->
                Text(ranges[i], fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                MgRow("Morning", am) { v -> prefs.setSchedule(schedule.copy(mg = schedule.mg.toMutableList().also { it[i] = v to pm })) }
                MgRow("Evening", pm) { v -> prefs.setSchedule(schedule.copy(mg = schedule.mg.toMutableList().also { it[i] = am to v })) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickTime = "am" }) { Text("Morning ${timeFmt.format(schedule.amTime)}") }
                OutlinedButton({ pickTime = "pm" }) { Text("Evening ${timeFmt.format(schedule.pmTime)}") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dose reminders", Modifier.weight(1f))
                Switch(reminders, {
                    prefs.setReminders(it); DoseAlarms.scheduleNext(ctx)
                    BatbloomApp.instance.appScope.launch { com.malluwayne.batbloom.meds.MedAlarms.scheduleNext(ctx) }
                })
            }
            if (!exactOk) Text("Exact alarms are off, so reminders may be a few minutes late.", color = Muted, fontSize = 12.sp)
            Stepper2("Snooze (minutes)", snooze, 5, 60, 5) { prefs.setSnoozeMinutes(it) }
            Stepper2("Skip a missed dose if the next is within (hours)", close, 1, 8, 1) { prefs.setCloseHours(it) }
            TextButton({ prefs.setSchedule(com.malluwayne.batbloom.quit.DoseSchedule()); DoseAlarms.scheduleNext(ctx) }) { Text("Reset to my original plan") }
        }

        SectionCard("Medications") {
            val medsTab by prefs.medsTab.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show Meds tab")
                    Text(
                        if (medsTab) "For other medicines, vitamins and supplements" else "Hidden. Reminders for medications you added are paused.",
                        color = Muted, fontSize = 12.sp,
                    )
                }
                Switch(medsTab, {
                    prefs.setMedsTab(it)
                    BatbloomApp.instance.appScope.launch { com.malluwayne.batbloom.meds.MedAlarms.scheduleNext(ctx) }
                })
            }
            Text("Varenicline stays on the Quit tab either way.", color = Muted, fontSize = 12.sp)
        }

        BackupSection()

        SectionCard("About") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(96)
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Batbloom", style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${com.malluwayne.batbloom.update.Updates.appVersion(ctx)} · grow a night garden", color = Muted, fontSize = 13.sp)
                }
            }
            Text(
                "Data stays on this phone. Not a substitute for medical advice.",
                fontSize = 13.sp, color = Muted,
            )
            TextButton({
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }) { Text("App info") }
        }
    }

    }
    if (showApps) AllowListDialog(allowed) { showApps = false }

    pickTime?.let { which ->
        val t = when (which) { "am" -> schedule.amTime; "pm" -> schedule.pmTime; else -> prefs.quitTime.value }
        val st = rememberTimePickerState(t.hour, t.minute, false)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton({
                    val nt = java.time.LocalTime.of(st.hour, st.minute)
                    when (which) {
                        "am" -> prefs.setSchedule(schedule.copy(amTime = nt))
                        "pm" -> prefs.setSchedule(schedule.copy(pmTime = nt))
                        else -> prefs.setQuitTime(nt)
                    }
                    pickTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickTime = null }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }

    pickDate?.let { which ->
        val initial = (when (which) { "quit" -> quit; "course" -> schedule.start; else -> exam }) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        when (which) {
                            "quit" -> prefs.setQuitDate(d)
                            "course" -> prefs.setSchedule(schedule.copy(start = d))
                            else -> prefs.setExamDate(d)
                        }
                    }
                    pickDate = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = null }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun Stepper2(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        TextButton({ onChange((value - step).coerceAtLeast(min)) }) { Text("−", fontSize = 18.sp) }
        Text("$value", fontWeight = FontWeight.SemiBold)
        TextButton({ onChange((value + step).coerceAtMost(max)) }) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
private fun MgRow(label: String, mg: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, color = Muted)
        listOf(0.0, 0.5, 1.0).forEach { v ->
            androidx.compose.material3.FilterChip(mg == v, { onChange(v) }, { Text(if (v == 0.0) "None" else com.malluwayne.batbloom.quit.Doses.formatMg(v), fontSize = 12.sp) })
        }
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
