@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.malluwayne.batbloom.ui

import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.quit.DoseAlarms
import com.malluwayne.batbloom.quit.QuitMath
import com.malluwayne.batbloom.stats.Treat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Short, general tips; one a day. */
private val QuitTips = listOf(
    "A craving usually fades in 3 to 5 minutes. Wait it out: drink a glass of water or take a short walk.",
    "Change the routines that went with smoking: a different chair, tea instead of coffee, a walk after meals.",
    "Tell a friend you've quit. Saying it out loud makes it easier to keep.",
    "Keep your hands busy: a pen, a stress ball, a short focus session.",
    "Alcohol makes slips more likely. Go easy, especially in the first weeks.",
    "Deep breaths help: in for 4, hold for 4, out for 6. Repeat five times.",
    "Sleep badly? It's common in the first weeks and gets better.",
    "Remember why you started. Read your reason out loud.",
    "Every craving you ride out makes the next one weaker.",
    "Slipped? Don't give up. One cigarette doesn't undo your progress: start again now.",
    "Snack on fruit, carrots or sugar-free gum instead of sweets.",
    "Exercise, even ten minutes, cuts cravings and lifts your mood.",
)

@Composable
fun QuitScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val quit by prefs.quitDate.collectAsState()
    val quitTime by prefs.quitTime.collectAsState()
    val smoking by prefs.smoking.collectAsState()
    val setupDone by prefs.smokingSetupDone.collectAsState()
    val reason by prefs.quitReason.collectAsState()
    val treat by prefs.treat.collectAsState()
    val schedule by prefs.schedule.collectAsState()
    val doses by app.repo.doses.collectAsState(emptyList())
    val badges by app.repo.badges.collectAsState(emptyList())
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val nowMs = rememberNow(30_000)
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val today = now.toLocalDate()
    var setup by remember { mutableStateOf(false) }
    var breathe by remember { mutableStateOf(false) }
    val askBreathe by com.malluwayne.batbloom.Nav.breathe.collectAsState()
    LaunchedEffect(askBreathe) { if (askBreathe) { breathe = true; com.malluwayne.batbloom.Nav.breathe.value = false } }
    var slipAsk by remember { mutableStateOf(false) }
    val slips by prefs.slips.collectAsState()
    val since = remember { DateTimeFormatter.ofPattern("EEE d MMM, h:mm a") }

    LaunchedEffect(quit, today) { DoseAlarms.checkBadges(ctx) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Quit", style = MaterialTheme.typography.headlineMedium)
                val q = quit
                Text(
                    when {
                        q == null -> "Set your quit day to start"
                        q.atTime(quitTime).atZone(zone).isAfter(now) -> "Quit day: " + since.format(q.atTime(quitTime))
                        else -> "Smoke-free since " + since.format(q.atTime(quitTime))
                    },
                    color = Muted, fontSize = 13.sp,
                )
            }
            TextButton({ setup = true }) { Text("Edit") }
        }

        // Not set up yet: a gentle prompt instead of a dialog that pops up on its own.
        if (!setupDone) SectionCard(modifier = Modifier.clickable { setup = true }) {
            Text("Set up your tracker", fontWeight = FontWeight.SemiBold)
            Text("Add how much you smoked and what a pack costs, for your savings. Tap to start.", color = Muted, fontSize = 13.sp)
        }

        val q = quit
        if (q == null) {
            SectionCard {
                Text("Ready to quit?", style = MaterialTheme.typography.titleMedium)
                Text("Pick your quit day, or start now. Batbloom counts your smoke-free time, the money you save and your health milestones.", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ prefs.quitNow() }) { Text("I've quit now") }
                    OutlinedButton({ setup = true }) { Text("Pick a day") }
                }
            }
        } else {
            val start = q.atTime(quitTime).atZone(zone)
            MilestoneHero(start, java.time.Duration.between(start, now), onQuitNow = { prefs.quitNow() })

            // Help in the moment
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ breathe = true }, Modifier.weight(1f)) { Text("Craving? Breathe") }
                OutlinedButton({ slipAsk = true }, Modifier.weight(1f)) { Text("I slipped") }
            }
            if (slips.isNotEmpty()) Text(
                "${slips.size} slip${if (slips.size == 1) "" else "s"} logged \u00B7 last ${sinceLabel(nowMs - slips.last())}. Every smoke-free day still counts.",
                color = Muted, fontSize = 12.sp,
            )

            val p = QuitMath.progress(start, now, smoking.perDay, smoking.packSize, smoking.packPrice)
            val perYear = if (smoking.packSize > 0) smoking.perDay * 365.0 / smoking.packSize * smoking.packPrice else 0.0
            if (!p.sinceQuit.isNegative) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuitTile("${p.notSmoked}", "cigarettes not smoked", Modifier.weight(1f))
                    QuitTile(rupees(p.moneySaved), "saved", Modifier.weight(1f), Teal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuitTile(QuitMath.formatDuration(p.lifeRegained), "life regained", Modifier.weight(1f), Lavender)
                    QuitTile(rupees(perYear), "saved every year", Modifier.weight(1f), Gold)
                }
            }

            // Saving for a treat
            if (treat.name.isNotBlank() && treat.price > 0) SectionCard {
                val frac = (p.moneySaved / treat.price).coerceIn(0.0, 1.0).toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Saving for ${treat.name}", fontWeight = FontWeight.SemiBold)
                        Text("${rupees(p.moneySaved.coerceAtMost(treat.price))} of ${rupees(treat.price)}", color = Muted, fontSize = 13.sp)
                    }
                    Text(if (frac >= 1f) "🎉" else "${(frac * 100).roundToInt()}%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Teal)
                }
                Meter(frac, Teal)
                val perDay = if (smoking.packSize > 0) smoking.perDay.toDouble() / smoking.packSize * smoking.packPrice else 0.0
                Text(
                    if (frac >= 1f) "You've saved enough. Treat yourself!"
                    else if (perDay > 0) "About ${((treat.price - p.moneySaved) / perDay).roundToInt().coerceAtLeast(1)} more smoke-free days" else "",
                    color = Muted, fontSize = 12.sp,
                )
            } else SectionCard(modifier = Modifier.clickable { setup = true }) {
                Text("Saving for something?", fontWeight = FontWeight.SemiBold)
                Text("Add a treat and watch the money you don't spend on cigarettes add up. Tap to add one.", color = Muted, fontSize = 13.sp)
            }
        }

        // Why I'm quitting
        if (reason.isNotBlank()) Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF3B2B4F), Color(0xFF1E1B33))), RoundedCornerShape(22.dp),
            ).clickable { setup = true }.padding(18.dp),
        ) {
            Text("WHY I'M QUITTING", color = Color(0xFFB9A3FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("“$reason”", color = Color.White, fontSize = 18.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 6.dp))
        } else SectionCard(modifier = Modifier.clickable { setup = true }) {
            Text("Why are you quitting?", fontWeight = FontWeight.SemiBold)
            Text("Write your reason. It shows here to keep you going. Tap to add it.", color = Muted, fontSize = 13.sp)
        }

        // Tips: today's first, swipe for more, ✕ to hide (turn back on in Edit).
        val showTips by prefs.quitTips.collectAsState()
        if (showTips) SectionCard {
            val start = (today.toEpochDay() % QuitTips.size).toInt()
            val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = start) { QuitTips.size }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tip", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${pager.currentPage + 1}/${QuitTips.size}", color = Muted, fontSize = 12.sp)
                androidx.compose.material3.IconButton({ prefs.setQuitTips(false) }, Modifier.size(32.dp)) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Hide tips",
                        tint = Muted, modifier = Modifier.size(18.dp),
                    )
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(pager, pageSpacing = 16.dp) { i ->
                Text(QuitTips[i], fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
            }
        }

        BadgeShelf(badges.associate { it.id to it.earnedAt }, schedule, quit, sessions, doses)
        Spacer(Modifier.height(8.dp))
    }

    if (setup) QuitSetupDialog(firstRun = !setupDone) { saved -> setup = false; if (saved) prefs.setSmokingSetupDone() }
    if (breathe) BreathingDialog { done ->
        breathe = false
        if (done) { prefs.addBreathing(); app.appScope.launch { DoseAlarms.checkBadges(app) } }
    }
    if (slipAsk) AlertDialog(
        onDismissRequest = { slipAsk = false },
        title = { Text("It happens") },
        text = { Text("A slip isn't a failure. Most people who quit for good slipped along the way. Do you want to keep your smoke-free time, or start again from now?") },
        confirmButton = { TextButton({ prefs.addSlip(System.currentTimeMillis()); slipAsk = false }) { Text("Keep my progress") } },
        dismissButton = { TextButton({ prefs.addSlip(System.currentTimeMillis()); prefs.quitNow(); slipAsk = false }) { Text("Restart from now") } },
    )
}

/** Guided breathing for a craving: in for 4, hold for 4, out for 6, for about two minutes. */
@Composable
/** [onClose] gets true when the exercise ran to the end or the craving passed (counts toward achievements). */
private fun BreathingDialog(onClose: (Boolean) -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.55f) }
    var phase by remember { mutableStateOf("Breathe in") }
    var left by remember { mutableIntStateOf(112) }
    LaunchedEffect(Unit) {
        val tween = { ms: Int -> androidx.compose.animation.core.tween<Float>(ms, easing = androidx.compose.animation.core.FastOutSlowInEasing) }
        while (left > 0) {
            phase = "Breathe in"; scale.animateTo(1f, tween(4000)); left -= 4
            phase = "Hold"; kotlinx.coroutines.delay(4000); left -= 4
            phase = "Breathe out"; scale.animateTo(0.55f, tween(6000)); left -= 6
        }
        phase = "Well done"
    }
    androidx.compose.ui.window.Dialog({ onClose(left <= 0) }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xF0081018)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(phase, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(30.dp))
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val r = size.minDimension / 2 * scale.value
                drawCircle(Brush.radialGradient(listOf(Color(0x557FE3D0), Color.Transparent), center, r * 1.35f), r * 1.35f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF7FE3D0), Color(0xFF2E7D74)), center, r), r)
            }
            Spacer(Modifier.height(30.dp))
            Text(if (left > 0) "${left / 60}:${"%02d".format(left % 60)} left" else "The craving should be easing now.", color = Color(0xFFCBD2F0))
            Text("Cravings usually pass in 3 to 5 minutes.", color = Color(0xFF8FA3C7), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(24.dp))
            Button({ onClose(true) }) { Text("The craving has passed") }
        }
    }
}

@Composable
private fun QuitTile(value: String, label: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    SectionCard(modifier = modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

/** Everything about your quit in one place. [onClose] gets true when saved. */
@Composable
private fun QuitSetupDialog(firstRun: Boolean, onClose: (Boolean) -> Unit) {
    val prefs = BatbloomApp.instance.prefs
    val quit by prefs.quitDate.collectAsState()
    val quitTime by prefs.quitTime.collectAsState()
    val smoking by prefs.smoking.collectAsState()
    val reason by prefs.quitReason.collectAsState()
    val treat by prefs.treat.collectAsState()
    var perDay by remember { mutableStateOf(smoking.perDay.toString()) }
    var pack by remember { mutableStateOf(smoking.packSize.toString()) }
    var price by remember { mutableStateOf(smoking.packPrice.roundToInt().toString()) }
    var why by remember { mutableStateOf(reason) }
    var treatName by remember { mutableStateOf(treat.name) }
    var treatPrice by remember { mutableStateOf(if (treat.price > 0) treat.price.roundToInt().toString() else "") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var tips by remember { mutableStateOf(prefs.quitTips.value) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy") }
    val tf = remember { DateTimeFormatter.ofPattern("h:mm a") }

    fun save() {
        prefs.setSmoking(
            smoking.copy(
                perDay = perDay.toIntOrNull() ?: smoking.perDay,
                packSize = pack.toIntOrNull()?.takeIf { it > 0 } ?: smoking.packSize,
                packPrice = price.toDoubleOrNull() ?: smoking.packPrice,
            ),
        )
        prefs.setQuitReason(why.trim())
        prefs.setTreat(Treat(treatName.trim(), treatPrice.toDoubleOrNull() ?: 0.0))
        prefs.setQuitTips(tips)
        onClose(true)
    }

    AlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(if (firstRun) "Set up your quit tracker" else "Your quit") },
        confirmButton = { TextButton(::save) { Text("Save") } },
        dismissButton = { TextButton({ onClose(false) }) { Text(if (firstRun) "Later" else "Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quit day", Modifier.weight(1f))
                    TextButton({ pickDate = true }) { Text(quit?.let { fmt.format(it) } ?: "Set") }
                }
                if (quit != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last cigarette at", Modifier.weight(1f))
                    TextButton({ pickTime = true }) { Text(tf.format(quitTime)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ prefs.quitNow() }) { Text("I've quit now") }
                    if (quit != null) TextButton({ prefs.setQuitDate(null) }) { Text("Clear") }
                }
                NumberBox("Cigarettes a day (before quitting)", perDay) { perDay = it }
                NumberBox("Cigarettes per pack", pack) { pack = it }
                NumberBox("Price per pack (₹)", price) { price = it }
                OutlinedTextField(why, { why = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Why I'm quitting") }, placeholder = { Text("e.g. To breathe easy on my runs") })
                OutlinedTextField(treatName, { treatName = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Treat I'm saving for") }, placeholder = { Text("e.g. New headphones") })
                NumberBox("Treat price (₹)", treatPrice) { treatPrice = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show tips", Modifier.weight(1f))
                    androidx.compose.material3.Switch(tips, { tips = it })
                }
            }
        },
    )
    if (pickDate) {
        val st = rememberDatePickerState((quit ?: java.time.LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { prefs.setQuitDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
    if (pickTime) {
        val st = rememberTimePickerState(quitTime.hour, quitTime.minute, false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton({ prefs.setQuitTime(LocalTime.of(st.hour, st.minute)); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
}

@Composable
fun NumberBox(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(7)) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
