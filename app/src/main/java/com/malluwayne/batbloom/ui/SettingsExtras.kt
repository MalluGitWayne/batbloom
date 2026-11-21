@file:OptIn(ExperimentalMaterial3Api::class)

package com.malluwayne.batbloom.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.data.Backup
import com.malluwayne.batbloom.focus.Device
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.guard.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TimeGuardSection(refreshKey: Long) {
    val ctx = LocalContext.current
    val prefs = BatbloomApp.instance.prefs
    val limits by prefs.guard.collectAsState()
    val usage = remember(limits, refreshKey) { Reminders.usageToday(ctx, limits.keys) }
    var picking by remember { mutableStateOf(false) }
    fun save(m: Map<String, Int>) { prefs.setGuard(m); Reminders.scheduleAll(ctx) }

    SubSection("Time Guard") {
        Text(
            "Daily limits for apps that eat your time. You get a nudge when you pass one.",
            fontSize = 13.sp, color = Muted,
        )
        if (!Device.hasUsageAccess(ctx)) Text("Usage access is off, so Time Guard can't see app time yet.", color = Gold, fontSize = 13.sp)
        limits.forEach { (pkg, limit) ->
            val used = usage[pkg] ?: 0
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Device.appLabel(ctx, pkg), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton({ save(limits + (pkg to (limit - 15).coerceAtLeast(5))) }) { Text("−") }
                    Text(Rules.formatMinutes(limit))
                    TextButton({ save(limits + (pkg to (limit + 15).coerceAtMost(600))) }) { Text("+") }
                    TextButton({ save(limits - pkg) }) { Text("✕", color = Muted) }
                }
                val f = if (limit > 0) (used.toFloat() / limit).coerceAtMost(1f) else 0f
                LinearProgressIndicator({ f }, Modifier.fillMaxWidth(), color = if (used >= limit) MaterialTheme.colorScheme.error else Teal, drawStopIndicator = {})
                Text("${Rules.formatMinutes(used)} used today", fontSize = 12.sp, color = Muted)
            }
        }
        OutlinedButton({ picking = true }) { Text("Add an app") }
    }
    if (picking) AppPickerDialog(exclude = limits.keys, onPick = { save(limits + (it to 30)); picking = false }, onClose = { picking = false })
}

@Composable
private fun AppPickerDialog(exclude: Set<String>, onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
                .filter { it.packageName != ctx.packageName && it.packageName !in exclude }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Guard which app?") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val list = apps
                if (list == null) Text("Loading…", color = Muted)
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list.filter { q.isBlank() || it.second.contains(q, true) }) { (pkg, label) ->
                        Text(label, Modifier.fillMaxWidth().clickable { onPick(pkg) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Cancel") } },
    )
}

@Composable
fun StudyReminderSection() {
    val ctx = LocalContext.current
    val prefs = BatbloomApp.instance.prefs
    val on by prefs.fmgeReminder.collectAsState()
    val time by prefs.fmgeTime.collectAsState()
    var pick by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    SubSection("Daily reminder") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Remind me", Modifier.weight(1f))
            Switch(on, { prefs.setFmgeReminder(it); Reminders.scheduleAll(ctx) })
        }
        if (on) OutlinedButton({ pick = true }) { Text("At ${fmt.format(time)}") }
    }
    if (pick) {
        val st = rememberTimePickerState(time.hour, time.minute, false)
        AlertDialog(
            onDismissRequest = { pick = false },
            confirmButton = {
                TextButton({ prefs.setFmgeTime(LocalTime.of(st.hour, st.minute)); Reminders.scheduleAll(ctx); pick = false }) { Text("OK") }
            },
            dismissButton = { TextButton({ pick = false }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
}

@Composable
fun BackupSection() {
    val ctx = LocalContext.current
    val app = BatbloomApp.instance
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<com.malluwayne.batbloom.data.BackupData?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val json = Backup.toJson(Backup.collect(ctx, app.db))
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
                "Backup saved."
            }.getOrElse { "Couldn't save the backup: ${it.message}" }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { Backup.fromJson(it.readBytes().decodeToString()) } }
            }.onSuccess { pendingRestore = it }.onFailure { message = it.message ?: "Couldn't read that file." }
        }
    }

    SectionCard("Backup") {
        Text(
            "Everything lives only on this phone. Save a backup file (sessions, garden, doses, diary, settings) to your phone or Google Drive, " +
                "and restore it after a reinstall or on a new phone.",
            fontSize = 13.sp, color = Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ save.launch("batbloom-backup-${LocalDate.now()}.json") }) { Text("Save backup") }
            OutlinedButton({ open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore") }
        }
        message?.let { Text(it, fontSize = 13.sp, color = Teal) }
    }

    pendingRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("It has ${b.sessions.size} sessions and ${b.doses.size} dose entries. Everything in Batbloom now will be replaced. Batbloom restarts afterwards.") },
            confirmButton = {
                TextButton({
                    pendingRestore = null
                    scope.launch {
                        withContext(Dispatchers.IO) { Backup.restore(ctx, app.db, b) }
                        restartApp(ctx)
                    }
                }) { Text("Replace and restart") }
            },
            dismissButton = { TextButton({ pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

private fun restartApp(ctx: android.content.Context) {
    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    ctx.startActivity(i)
    Runtime.getRuntime().exit(0)
}

@SuppressLint("BatteryLife")
@Composable
fun BackgroundSection(refreshKey: Long) {
    val ctx = LocalContext.current
    val unrestricted = remember(refreshKey) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val maker = Build.MANUFACTURER.lowercase()
    SubSection("Battery") {
        Text(
            "Keeps dose reminders and sessions on time on phones with strict battery savers.",
            fontSize = 13.sp, color = Muted,
        )
        StatusRow("Run freely in the background", unrestricted, "Allow") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
        }
        val tip = when {
            listOf("oneplus", "oppo", "realme").any { it in maker } ->
                "On ${Build.MANUFACTURER}: App info › Battery › allow background activity and auto-launch, and lock Batbloom in Recents."
            listOf("xiaomi", "redmi", "poco").any { it in maker } -> "On Xiaomi: App info › Autostart on, and Battery saver › No restrictions."
            listOf("samsung").any { it in maker } -> "On Samsung: Battery › Background usage limits › add Batbloom to Never sleeping apps."
            listOf("vivo", "iqoo").any { it in maker } -> "On vivo: allow Autostart and High background power consumption for Batbloom."
            else -> null
        }
        tip?.let { Text(it, fontSize = 13.sp) }
        TextButton({
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }) { Text("Open app info") }
    }
}
