@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.animation.togetherWith

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.Starter
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.focus.Device
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.focus.FocusEngine.State
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.focus.stageFor
import com.malluwayne.batbloom.plants.NightSky
import com.malluwayne.batbloom.plants.PlantArt
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun FocusScreen(modifier: Modifier) {
    val state by FocusEngine.state.collectAsState()
    // Smooth hand-over between setup, running, break and result (keyed by screen, not by every tick).
    androidx.compose.animation.AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(380)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(380), initialScale = 0.96f))
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)))
        },
        modifier = modifier.fillMaxSize(),
        label = "focus",
    ) { st ->
        when (st) {
            State.Idle -> SetupView()
            is State.Running -> RunningView(st)
            is State.Break -> BreakView(st)
            is State.Finished -> ResultView(st)
        }
    }
}

@Composable
private fun SkyWithPlant(plantId: String, growth: Float, withered: Boolean, height: Int, burst: Boolean = false) {
    val spec = PlantCatalog.byId(plantId)
    Box(
        Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(28.dp))
            .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Lavender.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        NightSky(Modifier.fillMaxSize())
        PlantArt(spec, growth, withered, Modifier.fillMaxSize())
        if (!withered) StagePulse(stageFor(growth), Modifier.fillMaxSize(), spec.glow)
        if (burst) BloomBurst(Modifier.fillMaxSize(), spec.glow)
    }
}

@Composable
private fun SetupView() {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val minutes by prefs.minutes.collectAsState()
    val tag by prefs.tag.collectAsState()
    val subject by prefs.subject.collectAsState()
    val plant by prefs.selectedPlant.collectAsState()
    val pomodoro by prefs.pomodoro.collectAsState()
    val stopwatch by prefs.stopwatch.collectAsState()
    val plan by prefs.plan.collectAsState()
    val goal by prefs.dailyGoal.collectAsState()
    val exam by prefs.examDate.collectAsState()
    val quit by prefs.quitDate.collectAsState()
    val now = rememberNow(60_000)
    var pickWhat by remember { mutableStateOf(false) }
    var pickPlant by remember { mutableStateOf(false) }
    var editPlan by remember { mutableStateOf(false) }
    var explainUsage by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val summary = remember(sessions, today) { Stats.compute(sessions, today, zone) }
    val due = remember(sessions, today) { com.malluwayne.batbloom.stats.Revision.due(sessions, today, zone) }
    val hasUsage = remember(now) { Device.hasUsageAccess(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(32)
            Spacer(Modifier.width(10.dp))
            Text("Batbloom", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        UpdateBanner { com.malluwayne.batbloom.Nav.tab.value = 3 }
        Spacer(Modifier.height(8.dp))

        // The plant; tap it to choose another
        Box(Modifier.clip(RoundedCornerShape(28.dp)).clickable { pickPlant = true }) {
            SkyWithPlant(plant, 1f, false, 280)
            Text(
                "${PlantCatalog.byId(plant).name}  \u203A", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
        }

        // One quiet status line
        val status = listOfNotNull(
            if (goal > 0) "FMGE ${Rules.formatMinutes(summary.fmgeTodayMinutes)} of ${Rules.formatMinutes(goal)}" else null,
            exam?.let { Stats.examLine(it, today) },
            quit?.let { q ->
                val since = java.time.Duration.between(q.atStartOfDay(zone), java.time.Instant.ofEpochMilli(now).atZone(zone))
                com.malluwayne.batbloom.quit.Milestones.nextLine(since) ?: Stats.quitLine(q, today)
            },
        ).joinToString("  \u00B7  ")
        if (status.isNotEmpty()) Text(status, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (due.isNotEmpty()) Text(
            "Revise today: " + due.take(3).joinToString(", ") { it.subject },
            color = Gold, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).clickable { prefs.setTag(Tags.FMGE); prefs.setSubject(due.first().subject) },
        )

        Spacer(Modifier.height(24.dp))

        // Mode, then the length
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeTab("Timer", !pomodoro && !stopwatch) { prefs.setPomodoro(false); prefs.setStopwatch(false) }
            ModeTab("Pomodoro", pomodoro) { prefs.setPomodoro(true) }
            ModeTab("Stopwatch", stopwatch) { prefs.setStopwatch(true) }
        }
        Spacer(Modifier.height(8.dp))
        when {
            stopwatch -> {
                Text("Count up", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("at least ${Rules.MIN_MINUTES} min", color = Muted, fontSize = 13.sp)
            }
            pomodoro -> {
                Text("${plan.rounds} \u00D7 ${plan.focusMinutes} min", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                TextButton({ editPlan = true }) {
                    Text("${plan.shortBreak} min breaks \u00B7 ${plan.longBreak} min long break  \u203A", color = Muted, fontSize = 13.sp)
                }
            }
            else -> {
                Text("$minutes min", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { prefs.setMinutes((it / 5).roundToInt() * 5) },
                    valueRange = Rules.MIN_MINUTES.toFloat()..Rules.MAX_MINUTES.toFloat(),
                    steps = (Rules.MAX_MINUTES - Rules.MIN_MINUTES) / 5 - 1,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                )
            }
        }

        // What you are focusing on: one tap to change
        TextButton({ pickWhat = true }) {
            Text(listOfNotNull(tag, if (tag == Tags.FMGE) subject else null).joinToString(" \u00B7 ") + "  \u203A", fontSize = 15.sp)
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { Starter.fromPrefs(ctx) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (stopwatch) "Start stopwatch" else if (pomodoro) "Start Pomodoro" else "Start", fontSize = 18.sp)
        }
        if (!hasUsage) TextButton({ explainUsage = true }) {
            Text("Leave detection is off \u00B7 Turn on", color = Muted, fontSize = 12.sp)
        }
    }

    if (pickWhat) FocusOnDialog { pickWhat = false }
    if (pickPlant) PlantDialog { pickPlant = false }
    if (editPlan) AlertDialog(
        onDismissRequest = { editPlan = false },
        confirmButton = { TextButton({ editPlan = false }) { Text("Done") } },
        title = { Text("Pomodoro") },
        text = {
            Column {
                Stepper("Focus", plan.focusMinutes, 10, 60, 5, "min") { prefs.setPlan(plan.copy(focusMinutes = it)) }
                Stepper("Short break", plan.shortBreak, 1, 15, 1, "min") { prefs.setPlan(plan.copy(shortBreak = it)) }
                Stepper("Long break", plan.longBreak, 5, 40, 5, "min") { prefs.setPlan(plan.copy(longBreak = it)) }
                Stepper("Rounds", plan.rounds, 1, 8, 1, "") { prefs.setPlan(plan.copy(rounds = it)) }
                Text(
                    "Total ${Rules.formatMinutes(plan.totalMinutes())}",
                    color = Muted, fontSize = 12.sp,
                )
            }
        },
    )
    if (explainUsage) AlertDialog(
        onDismissRequest = { explainUsage = false },
        title = { Text("Leave detection") },
        text = {
            Text(
                "To notice when you switch to another app during a session, Batbloom needs Usage access. It only checks which app is on " +
                    "screen while a plant is growing, and that never leaves your phone.",
            )
        },
        confirmButton = {
            TextButton({ explainUsage = false; ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open Usage access") }
        },
        dismissButton = { TextButton({ explainUsage = false }) { Text("Not now") } },
    )
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) MaterialTheme.colorScheme.onSurface else Muted,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Tag, FMGE subject and custom tags, in one dialog. */
@Composable
private fun FocusOnDialog(onClose: () -> Unit) {
    val prefs = BatbloomApp.instance.prefs
    val tag by prefs.tag.collectAsState()
    val subject by prefs.subject.collectAsState()
    val custom by prefs.customTags.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Done") } },
        title = { Text("Focusing on") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Tags.all.filter { it != Tags.CRAVING } + custom).forEach { t -> FilterChip(tag == t, { prefs.setTag(t) }, { Text(t) }) }
                    FilterChip(false, { adding = true }, { Text("+ Tag") })
                }
                if (adding) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("New tag") })
                    TextButton({ prefs.addTag(text); prefs.setTag(text.trim().take(20)); adding = false; text = "" }) { Text("Add") }
                }
                if (tag == Tags.FMGE) {
                    Text("Subject", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Tags.fmgeSubjects.forEach { s ->
                        Text(
                            s, Modifier.fillMaxWidth().clickable { prefs.setSubject(s) }.padding(vertical = 9.dp),
                            color = if (s == subject) Lavender else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (s == subject) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PlantDialog(onClose: () -> Unit) {
    val app = BatbloomApp.instance
    val owned by app.repo.unlocked.collectAsState(setOf("moonflower"))
    val blooms by app.repo.bloomCount.collectAsState(0)
    val plant by app.prefs.selectedPlant.collectAsState()
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Close") } },
        title = { Text("Plant") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlantCatalog.all.forEach { p ->
                    val have = p.id in owned
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(76.dp).then(if (have) Modifier.clickable { app.prefs.setSelectedPlant(p.id); onClose() } else Modifier),
                    ) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).plantTile()
                                .border(2.dp, if (p.id == plant) Lavender else Color.Transparent, RoundedCornerShape(16.dp))
                                .graphicsLayer(alpha = if (have) 1f else 0.35f),
                        ) { PlantArt(p, 1f, false, Modifier.fillMaxSize(), animate = false) }
                        Text(
                            when {
                                have -> p.name
                                p.badge != null -> "Finish course"
                                else -> "${blooms}/${p.cost} blooms"
                            },
                            fontSize = 11.sp, maxLines = 1, color = Muted,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton({ onChange((value - step).coerceAtLeast(min)) }) { Text("−", fontSize = 20.sp) }
        Text("$value $unit".trim(), Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        TextButton({ onChange((value + step).coerceAtMost(max)) }) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun RunningView(s: State.Running) {
    val ctx = LocalContext.current
    val now = rememberNow()
    val sw = s.request.stopwatch
    val progress = if (sw) Rules.stopwatchGrowth(now - s.startAt) else Rules.progress(s.startAt, s.endAt, now)
    var confirm by remember { mutableStateOf(false) }
    var clockMode by remember { mutableStateOf(false) }
    if (clockMode) ClockMode(s, onExit = { clockMode = false })
    val hasUsage = remember { Device.hasUsageAccess(ctx) }
    val reason by BatbloomApp.instance.prefs.quitReason.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val head = listOfNotNull(s.tag, s.subject).joinToString(" · ") + (s.round?.let { "  ·  round $it/${s.request.plan?.rounds}" } ?: "")
        Text(head, color = Muted)
        SkyWithPlant(s.plantId, progress, false, 340)
        Text(clock(if (sw) now - s.startAt else s.endAt - now), fontSize = 56.sp, fontWeight = FontWeight.Bold)
        if (sw) {
            val enough = Rules.stopwatchCounts(now - s.startAt)
            Button({ FocusEngine.stop(ctx) }, Modifier.fillMaxWidth()) { Text(if (enough) "Stop & plant it" else "Stop (needs ${Rules.MIN_MINUTES} min)") }
        }
        Text("${PlantCatalog.byId(s.plantId).name} · ${stageFor(progress).label}", color = Lavender)

        s.warningSeconds?.let {
            SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                Text("Your plant is wilting! Leave ${s.warningApp ?: "that app"} within ${it}s.", fontWeight = FontWeight.Bold)
            }
        }
        if (!hasUsage) Text("Leave detection is off", color = Muted, fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ clockMode = true }) { Text("Clock mode") }
            TextButton({ confirm = true }) { Text("Give up", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        confirmButton = { TextButton({ confirm = false; FocusEngine.giveUp(ctx) }) { Text("Give up") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep going") } },
        title = { Text("Give up?") },
        text = { Text("Your ${PlantCatalog.byId(s.plantId).name} will wilt.") },
    )
}

@Composable
private fun BreakView(s: State.Break) {
    val ctx = LocalContext.current
    val now = rememberNow()
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (s.long) "Long break" else "Short break", style = MaterialTheme.typography.headlineSmall, color = Teal)
        SkyWithPlant(s.request.plantId, 1f, false, 300)
        Text(clock(s.endAt - now), fontSize = 52.sp, fontWeight = FontWeight.Bold)
        Text(
            "${s.blooms} bloomed so far. Round ${s.nextRound} of ${s.request.plan?.rounds} starts by itself. Apps are free during the break.",
            textAlign = TextAlign.Center, color = Muted,
        )
        OutlinedButton({ FocusEngine.giveUp(ctx) }) { Text("End cycle now") }
    }
}

@Composable
private fun ResultView(s: State.Finished) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = PlantCatalog.byId(s.request.plantId).name
        SkyWithPlant(s.request.plantId, 1f, !s.success, 340, burst = s.success)
        Text(
            when {
                s.rounds != null -> "${s.blooms} of ${s.rounds} rounds bloomed"
                s.success -> "Your $name bloomed!"
                else -> "Your $name wilted"
            },
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        val detail = when {
            s.reason == null -> "${s.minutes} min of ${listOfNotNull(s.request.tag, s.request.subject).joinToString(" · ")}"
            s.reason == "gave up" -> "You gave up. A shorter session next time?"
            s.reason == "too short" -> "Stopped before ${Rules.MIN_MINUTES} minutes, so nothing was planted this time."
            s.reason == "interrupted" -> "The session was interrupted."
            else -> "You opened ${s.reason}. If you need it while focusing, add it to the allowed apps in Settings."
        }
        Text(detail, color = Muted, textAlign = TextAlign.Center)
        Button({ FocusEngine.dismiss() }, Modifier.fillMaxWidth()) { Text("Done") }
    }
}


/** The launcher icon, drawn in the app (its adaptive layers, cropped to a circle). */
@Composable
fun AppLogo(sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
        val m = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.malluwayne.batbloom.R.drawable.ic_launcher_background), null, m)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.malluwayne.batbloom.R.drawable.ic_launcher_foreground), null, m)
    }
}


/**
 * Clock mode: black, dim, always-on screen with the timer, like an always-on display. The text drifts a little every
 * minute so the screen doesn't burn in. Tap anywhere to leave. The session keeps running either way.
 */
@Composable
private fun ClockMode(s: State.Running, onExit: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onExit,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(Unit) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            view.keepScreenOn = true
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = 0.05f }
                androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { view.keepScreenOn = false }
        }
        val now = rememberNow()
        val sw = s.request.stopwatch
        val minute = (now / 60_000).toInt()
        val dx = ((minute * 37) % 61 - 30).dp
        val dy = ((minute * 53) % 121 - 60).dp
        val endsAt = remember(s.endAt) { java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(java.time.Instant.ofEpochMilli(s.endAt).atZone(ZoneId.systemDefault())) }
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(dx, dy)) {
                Box(Modifier.size(90.dp).graphicsLayer(alpha = 0.55f)) {
                    PlantArt(PlantCatalog.byId(s.plantId), if (sw) Rules.stopwatchGrowth(now - s.startAt) else Rules.progress(s.startAt, s.endAt, now), false, Modifier.fillMaxSize(), animate = false)
                }
                Text(
                    clock(if (sw) now - s.startAt else s.endAt - now),
                    color = Color(0xFFBDBDC8), fontSize = 64.sp, fontWeight = FontWeight.Light,
                )
                Text(
                    listOfNotNull(s.tag, s.subject).joinToString(" \u00B7 ") + if (sw) "" else "  \u00B7  ends $endsAt",
                    color = Color(0xFF6E6E7A), fontSize = 14.sp,
                )
            }
        }
    }
}
