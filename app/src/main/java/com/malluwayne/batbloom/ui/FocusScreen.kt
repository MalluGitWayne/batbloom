@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

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
    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            State.Idle -> SetupView()
            is State.Running -> RunningView(s)
            is State.Break -> BreakView(s)
            is State.Finished -> ResultView(s)
        }
    }
}

@Composable
private fun SkyWithPlant(plantId: String, growth: Float, withered: Boolean, height: Int) {
    Box(
        Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        NightSky(Modifier.fillMaxSize())
        PlantArt(PlantCatalog.byId(plantId), growth, withered, Modifier.fillMaxSize())
    }
}

@Composable
private fun SetupView() {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val coins by app.repo.coins.collectAsState(0)
    val owned by app.repo.unlocked.collectAsState(setOf("moonflower"))
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val minutes by prefs.minutes.collectAsState()
    val tag by prefs.tag.collectAsState()
    val subject by prefs.subject.collectAsState()
    val plant by prefs.selectedPlant.collectAsState()
    val pomodoro by prefs.pomodoro.collectAsState()
    val plan by prefs.plan.collectAsState()
    val custom by prefs.customTags.collectAsState()
    val now = rememberNow(60_000)
    var pickSubject by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val summary = remember(sessions, today) { Stats.compute(sessions, today, zone) }
    val hasUsage = remember(now) { Device.hasUsageAccess(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Batbloom", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton({ com.malluwayne.batbloom.Nav.shop.value = true }) { Text("● $coins  Shop", color = Gold, fontWeight = FontWeight.Bold) }
        }
        SkyWithPlant(plant, 1f, false, 220)

        QuitCard(now)
        FmgeCard(summary.fmgeTodayMinutes)

        if (!hasUsage) UsageAccessCard()

        // Mode
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!pomodoro, { prefs.setPomodoro(false) }, { Text("Single session") })
            FilterChip(pomodoro, { prefs.setPomodoro(true) }, { Text("Pomodoro") })
        }
        if (!pomodoro) {
            Text("$minutes min", fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Slider(
                value = minutes.toFloat(),
                onValueChange = { prefs.setMinutes((it / 5).roundToInt() * 5) },
                valueRange = Rules.MIN_MINUTES.toFloat()..Rules.MAX_MINUTES.toFloat(),
                steps = (Rules.MAX_MINUTES - Rules.MIN_MINUTES) / 5 - 1,
            )
        } else {
            SectionCard {
                Stepper("Focus", plan.focusMinutes, 10, 60, 5, "min") { prefs.setPlan(plan.copy(focusMinutes = it)) }
                Stepper("Short break", plan.shortBreak, 1, 15, 1, "min") { prefs.setPlan(plan.copy(shortBreak = it)) }
                Stepper("Long break", plan.longBreak, 5, 40, 5, "min") { prefs.setPlan(plan.copy(longBreak = it)) }
                Stepper("Rounds", plan.rounds, 1, 8, 1, "") { prefs.setPlan(plan.copy(rounds = it)) }
                Text(
                    "Long break after every ${plan.longEvery} rounds. Breaks start and end by themselves. Total ${Rules.formatMinutes(plan.totalMinutes())}.",
                    color = Muted, fontSize = 12.sp,
                )
            }
        }

        Text("Tag", color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (Tags.all.filter { it != Tags.CRAVING } + custom).forEach { t ->
                FilterChip(tag == t, { prefs.setTag(t) }, { Text(t) })
            }
            FilterChip(false, { addTag = true }, { Text("+ Tag") })
        }
        if (tag == Tags.FMGE) {
            OutlinedButton(onClick = { pickSubject = true }, modifier = Modifier.fillMaxWidth()) { Text("Subject: $subject") }
        }

        Text("Plant", color = Muted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlantCatalog.all.filter { it.id in owned }) { p ->
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Panel)
                        .border(2.dp, if (p.id == plant) Lavender else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { prefs.setSelectedPlant(p.id) },
                ) { PlantArt(p, 1f, false, Modifier.fillMaxSize(), animate = false) }
            }
        }

        Button(onClick = { Starter.fromPrefs(ctx) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (pomodoro) "Start Pomodoro" else "Plant & focus", fontSize = 18.sp)
        }
        Button(
            onClick = { Starter.craving(ctx) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = MaterialTheme.colorScheme.onTertiary),
        ) { Text("Craving? Grow a 10-minute bloom", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
    }

    if (pickSubject) AlertDialog(
        onDismissRequest = { pickSubject = false },
        confirmButton = { TextButton({ pickSubject = false }) { Text("Close") } },
        title = { Text("FMGE subject") },
        text = {
            LazyColumn {
                items(Tags.fmgeSubjects) { s ->
                    Text(
                        s, Modifier.fillMaxWidth().clickable { prefs.setSubject(s); pickSubject = false }.padding(vertical = 12.dp),
                        color = if (s == subject) Lavender else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    )
    if (addTag) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTag = false },
            confirmButton = { TextButton({ prefs.addTag(text); prefs.setTag(text.trim().take(20)); addTag = false }) { Text("Add") } },
            dismissButton = { TextButton({ addTag = false }) { Text("Cancel") } },
            title = { Text("New tag") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("e.g. Revision, Grand test") }) },
        )
    }
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
private fun QuitCard(now: Long) {
    val prefs = BatbloomApp.instance.prefs
    val quit by prefs.quitDate.collectAsState()
    val smoking by prefs.smoking.collectAsState()
    val reason by prefs.quitReason.collectAsState()
    val treat by prefs.treat.collectAsState()
    val q = quit ?: return
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val slips by BatbloomApp.instance.repo.slips.collectAsState(emptyList())
    val p0 = com.malluwayne.batbloom.quit.QuitMath.progress(
        q.atStartOfDay(zone), java.time.Instant.ofEpochMilli(now).atZone(zone),
        smoking.perDay, smoking.packSize, smoking.packPrice, slips.sumOf { it.count },
    )
    val sf = Stats.SmokeFreeView(!p0.sinceQuit.isNegative && !p0.sinceQuit.isZero, p0.notSmoked, p0.moneySaved)
    SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)) {
        Text(Stats.quitLine(q, today), color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (sf.started) Text("${sf.notSmoked} cigarettes not smoked · ${rupees(sf.moneySaved)} saved")
        if (treat.name.isNotBlank() && treat.price > 0) {
            val p = (sf.moneySaved / treat.price).coerceIn(0.0, 1.0).toFloat()
            Text("Treat: ${treat.name} · ${(p * 100).roundToInt()}%", fontSize = 13.sp)
            LinearProgressIndicator({ p }, Modifier.fillMaxWidth(), color = Gold)
        }
        if (reason.isNotBlank()) Text("“$reason”", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun FmgeCard(todayMinutes: Int) {
    val prefs = BatbloomApp.instance.prefs
    val exam by prefs.examDate.collectAsState()
    val goal by prefs.dailyGoal.collectAsState()
    val tag by prefs.tag.collectAsState()
    if (exam == null && tag != Tags.FMGE) return
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FMGE today", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${Rules.formatMinutes(todayMinutes)} / ${Rules.formatMinutes(goal)}", color = Teal)
        }
        LinearProgressIndicator({ if (goal > 0) (todayMinutes.toFloat() / goal).coerceAtMost(1f) else 0f }, Modifier.fillMaxWidth(), color = Teal)
        exam?.let { Text(Stats.examLine(it, LocalDate.now()), color = Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun UsageAccessCard() {
    val ctx = LocalContext.current
    SectionCard("Turn on leave detection", container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
        Text(
            "To know when you switch to another app during a session, Batbloom needs Usage access. " +
                "It only checks which app is on screen while a plant is growing, on this phone. Nothing is sent anywhere: the app has no internet permission.",
            fontSize = 13.sp,
        )
        Button({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open Usage access") }
    }
}

private val cravingTips = listOf(
    "A craving rises and falls like a wave. Ride this one out.",
    "Breathe in for 4, hold for 4, out for 6. Do it five times.",
    "Drink a glass of cold water, slowly.",
    "Stand up, stretch, or walk around the room.",
    "Chew gum or eat something crunchy.",
    "Text a friend instead of reaching for a cigarette.",
    "Every craving you beat makes the next one weaker.",
)

@Composable
private fun RunningView(s: State.Running) {
    val ctx = LocalContext.current
    val now = rememberNow()
    val progress = Rules.progress(s.startAt, s.endAt, now)
    var confirm by remember { mutableStateOf(false) }
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
        Text(clock(s.endAt - now), fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Text("${PlantCatalog.byId(s.plantId).name} · ${stageFor(progress).label}", color = Lavender)
        LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())

        s.warningSeconds?.let {
            SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                Text("Your plant is wilting! Leave ${s.warningApp ?: "that app"} within ${it}s.", fontWeight = FontWeight.Bold)
            }
        }
        if (!hasUsage) Text("Leave detection is off. Turn on Usage access from the Focus screen.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)

        if (s.tag == Tags.CRAVING) CravingPanel(s, now, reason)

        TextButton({ confirm = true }) { Text("Give up", color = MaterialTheme.colorScheme.error) }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        confirmButton = { TextButton({ confirm = false; FocusEngine.giveUp(ctx) }) { Text("Give up") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep going") } },
        title = { Text("Give up?") },
        text = { Text("Your ${PlantCatalog.byId(s.plantId).name} will wilt and you earn no coins.") },
    )
}

@Composable
private fun CravingPanel(s: State.Running, now: Long, reason: String) {
    val app = BatbloomApp.instance
    val scope = rememberCoroutineScope()
    var intensity by remember { mutableStateOf(s.request.cravingIntensity) }
    var trigger by remember { mutableStateOf(s.request.trigger) }
    fun save() { if (s.sessionId >= 0) scope.launch { app.repo.setCraving(s.sessionId, intensity, trigger) } }
    val tips = if (reason.isNotBlank()) cravingTips + "Remember why: $reason" else cravingTips
    SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)) {
        Text(tips[((now / 20_000) % tips.size).toInt()], fontWeight = FontWeight.SemiBold)
        Text("How strong is it?", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { i -> FilterChip(intensity == i, { intensity = i; save() }, { Text("$i") }) }
        }
        Text("What set it off?", color = Muted, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tags.cravingTriggers.forEach { t -> FilterChip(trigger == t, { trigger = t; save() }, { Text(t, fontSize = 12.sp) }) }
        }
    }
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
        SkyWithPlant(s.request.plantId, 1f, !s.success, 340)
        Text(
            when {
                s.rounds != null -> "${s.blooms} of ${s.rounds} rounds bloomed"
                s.success -> "Your $name bloomed!"
                else -> "Your $name wilted"
            },
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        )
        if (s.coins > 0) Text("+${s.coins} coins", color = Gold, fontSize = 20.sp)
        val detail = when {
            s.reason == null && s.request.tag == Tags.CRAVING && s.success -> "Craving beaten. That's one more win."
            s.reason == null -> "${s.minutes} min of ${listOfNotNull(s.request.tag, s.request.subject).joinToString(" · ")}"
            s.reason == "gave up" -> "You gave up. A shorter session next time?"
            s.reason == "interrupted" -> "The session was interrupted."
            else -> "You opened ${s.reason}. If you need it while focusing, add it to the allowed apps in Settings."
        }
        Text(detail, color = Muted, textAlign = TextAlign.Center)
        Button({ FocusEngine.dismiss() }, Modifier.fillMaxWidth()) { Text("Done") }
    }
}
