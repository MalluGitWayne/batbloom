package com.malluwayne.batbloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.Nav
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.meds.MedSchedule
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.quit.Badges
import com.malluwayne.batbloom.quit.Doses
import com.malluwayne.batbloom.quit.QuitMath
import com.malluwayne.batbloom.stats.Stats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Tab numbers (fixed; see MainActivity's Tab). */
object TabIds {
    const val FOCUS = 0
    const val GARDEN = 1
    const val QUIT = 2
    const val MEDS = 3
    const val SETTINGS = 4
    const val EXAM = 5
    const val HOME = 6
}

/** Today at a glance: focus, the next dose, smoke-free time, the exam and achievements. Each card opens its tab. */
@Composable
fun HomeScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val prefs = app.prefs
    val zone = ZoneId.systemDefault()
    val nowMs = rememberNow(30_000)
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val today = now.toLocalDate()
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val badges by app.repo.badges.collectAsState(emptyList())
    val plant by prefs.selectedPlant.collectAsState()
    val medsOn by prefs.medsTab.collectAsState()
    val quitOn by prefs.quitTab.collectAsState()
    val examOn by prefs.examTab.collectAsState()
    val stats = remember(sessions, today) { Stats.compute(sessions, today, zone) }
    val tf = remember { DateTimeFormatter.ofPattern("h:mm a") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
                Text(DateTimeFormatter.ofPattern("EEEE, d MMMM").format(today), color = Muted, fontSize = 13.sp)
            }
        }
        UpdateBanner { Nav.tab.value = TabIds.SETTINGS }

        // Focus + garden
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF2B2560), Color(0xFF14162C))), RoundedCornerShape(24.dp))
                .clickable { Nav.tab.value = TabIds.FOCUS }.padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FOCUS TODAY", color = Color(0xFFB9C8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(Rules.formatMinutes(stats.todayMinutes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("${stats.streakDays}-day streak · ${stats.completed} blooms", color = Color(0xFFCBD2F0), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Button({ Nav.tab.value = TabIds.FOCUS }) { Text("Start focus") }
                        androidx.compose.material3.OutlinedButton({ Nav.tab.value = TabIds.GARDEN; Nav.garden.value = 0 }) { Text("Garden", color = Color.White) }
                    }
                }
                com.malluwayne.batbloom.plants.PlantArt(PlantCatalog.byId(plant), 1f, false, Modifier.width(96.dp).height(130.dp), animate = false)
            }
        }

        // Next dose
        if (medsOn) {
            val meds by app.db.meds().all().collectAsState(emptyList())
            val since = remember(today) { today.atStartOfDay(zone).toInstant().toEpochMilli() }
            val logs by remember(today) { app.db.meds().logsSince(since, today.toString()) }.collectAsState(emptyList())
            val schedule by prefs.schedule.collectAsState()
            val courseLogs by app.repo.doses.collectAsState(emptyList())
            data class Due(val name: String, val detail: String, val at: java.time.LocalDateTime, val done: Boolean)
            val all = remember(meds, logs, schedule, courseLogs, today) {
                val logged = logs.filter { it.slot != null }.map { it.medId to it.slot }.toSet()
                val cKeys = courseLogs.map { it.key }.toSet()
                MedSchedule.dosesOn(meds, today).map { Due(it.med.name, MedSchedule.qtyLabel(it.med, it.qty), it.at, (it.med.id to it.slot) in logged) } +
                    schedule.slotsOn(today).map { Due("Varenicline", Doses.formatMg(it.mg), schedule.timeOf(it, zone).toLocalDateTime(), it.key in cKeys) }
            }.sortedBy { it.at }
            if (all.isNotEmpty()) {
                val open = all.filter { !it.done }
                val next = open.firstOrNull()
                SectionCard(modifier = Modifier.clickable { Nav.tab.value = TabIds.MEDS }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Medications", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (next == null) Text("All ${all.size} doses logged today ✓", fontWeight = FontWeight.SemiBold, color = Teal)
                            else {
                                val late = now.toLocalDateTime().isAfter(next.at.plusMinutes(60))
                                Text((if (late) "Missed: " else "Next: ") + "${next.name} · ${next.detail}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${tf.format(next.at)} · ${all.count { it.done }} of ${all.size} logged today",
                                    color = if (late) Gold else Muted, fontSize = 13.sp,
                                )
                            }
                        }
                        Text("›", color = Muted, fontSize = 22.sp)
                    }
                }
            }
        }

        // Smoke-free
        if (quitOn) {
            val quit by prefs.quitDate.collectAsState()
            val quitTime by prefs.quitTime.collectAsState()
            val smoking by prefs.smoking.collectAsState()
            quit?.let { q ->
                val start = q.atTime(quitTime).atZone(zone)
                val p = QuitMath.progress(start, now, smoking.perDay, smoking.packSize, smoking.packPrice)
                SectionCard(modifier = Modifier.clickable { Nav.tab.value = TabIds.QUIT }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Smoke-free", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (p.sinceQuit.isNegative) Text("Quit day in ${ChronoUnit.DAYS.between(today, q)} days", fontWeight = FontWeight.SemiBold)
                            else {
                                Text(QuitMath.formatDuration(p.sinceQuit), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Teal)
                                Text("${rupees(p.moneySaved)} saved · ${p.notSmoked} not smoked", color = Muted, fontSize = 13.sp)
                            }
                        }
                        Text("›", color = Muted, fontSize = 22.sp)
                    }
                }
            }
        }

        // Exam
        if (examOn) {
            val exam by prefs.examDate.collectAsState()
            val goal by prefs.dailyGoal.collectAsState()

            SectionCard(modifier = Modifier.clickable { Nav.tab.value = TabIds.EXAM }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FMGE", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            exam?.let { e -> ChronoUnit.DAYS.between(today, e).let { d -> if (d > 0) "$d days to go" else if (d == 0L) "Exam day!" else "Exam done" } } ?: "Set your exam date",
                            fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        )
                        Text(
                            "${Rules.formatMinutes(stats.fmgeTodayMinutes)} of ${Rules.formatMinutes(goal)} today",
                            color = Muted, fontSize = 13.sp,
                        )
                    }
                    Text("›", color = Muted, fontSize = 22.sp)
                }
                if (goal > 0) Meter((stats.fmgeTodayMinutes.toFloat() / goal).coerceAtMost(1f), Teal)
            }
        }

        // Achievements
        val earned = badges.map { it.id }.filter { id -> Badges.all.any { it.id == id } }
        val latest = badges.filter { b -> Badges.all.any { it.id == b.id } }.maxByOrNull { it.earnedAt }?.let { Badges.byId(it.id) }
        SectionCard(modifier = Modifier.clickable { Nav.tab.value = TabIds.GARDEN; Nav.garden.value = 2 }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                latest?.let { Emblem(it, true, 56.dp) }
                Column(Modifier.weight(1f).padding(start = if (latest != null) 12.dp else 0.dp)) {
                    Text("Achievements", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${Badges.points(earned)} pts · ${earned.size} of ${Badges.all.size}", fontWeight = FontWeight.Bold)
                    latest?.let { Text("Latest: ${it.title}", color = Muted, fontSize = 13.sp) }
                }
                Text("›", color = Muted, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
