package com.malluwayne.batbloom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.Nav
import com.malluwayne.batbloom.data.DoseLogEntity
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.quit.Badge
import com.malluwayne.batbloom.quit.Badges
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.quit.FocusFacts
import com.malluwayne.batbloom.quit.Tier
import com.malluwayne.batbloom.stats.Stats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Everything the achievement views need, computed once. */
private class AchievementState(
    val earnedAt: Map<String, Long>,
    val schedule: DoseSchedule,
    val quit: LocalDate?,
    val today: LocalDate,
    val facts: FocusFacts,
) {
    fun progress(b: Badge): Pair<Int, Int>? = Badges.progress(b.id, today, schedule, quit, facts)
    fun ratio(b: Badge): Float? = progress(b)?.let { (h, t) -> h.toFloat() / t }
    val earnedIds get() = earnedAt.keys.filter { id -> Badges.all.any { it.id == id } }
}

@Composable
private fun rememberAchievements(
    earnedAt: Map<String, Long>, schedule: DoseSchedule, quit: LocalDate?, sessions: List<SessionEntity>, doses: List<DoseLogEntity>,
): AchievementState {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val app = BatbloomApp.instance
    val mcqTotal by app.prefs.mcqTotal.collectAsState()
    val tests by app.prefs.mockTests.collectAsState()
    val breathing by app.prefs.breathing.collectAsState()
    val extra by androidx.compose.runtime.produceState(Badges.Extra(), earnedAt, mcqTotal, tests, breathing, today) {
        value = app.repo.extraFacts(app.prefs, today)
    }
    return remember(earnedAt, schedule, quit, sessions, doses, today, extra) {
        val st = Stats.compute(sessions, today, zone)
        val taken = doses.filter { it.status == DoseStatus.TAKEN }.map { it.key }.toSet()
        AchievementState(
            earnedAt, schedule, quit, today,
            Badges.facts(sessions, maxOf(st.bestStreak, st.streakDays), zone, Badges.bestDoseStreak(schedule, taken, today), extra),
        )
    }
}

/** On the Quit tab: score, the latest achievements and the next one coming, with a link to all of them. */
@Composable
fun BadgeShelf(earnedAt: Map<String, Long>, schedule: DoseSchedule, quit: LocalDate?, sessions: List<SessionEntity>, doses: List<DoseLogEntity>) {
    val s = rememberAchievements(earnedAt, schedule, quit, sessions, doses)
    var open by remember { mutableStateOf<Badge?>(null) }
    val quitBadges = Badges.all.filter { it.group == Badges.QUIT }
    val latest = quitBadges.filter { it.id in earnedAt }.sortedByDescending { earnedAt[it.id] }.take(4)
    val next = quitBadges.filter { it.id !in earnedAt }.minByOrNull { Badges.dueDate(it.id, schedule, quit) ?: LocalDate.MAX }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium)
                Text("${s.earnedIds.size} of ${Badges.all.size} · ${Badges.points(s.earnedIds)} pts", color = Muted, fontSize = 13.sp)
            }
            TextButton({ Nav.tab.value = 1; Nav.garden.value = 2 }) { Text("See all ›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            latest.forEach { b ->
                Column(Modifier.weight(1f).clickable { open = b }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Emblem(b, true, 64.dp)
                    Text(b.title.replace(" smoke-free", ""), fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 12.sp)
                }
            }
            next?.let { b ->
                Column(Modifier.weight(1f).clickable { open = b }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Emblem(b, false, 64.dp, s.ratio(b))
                    Text("Next: " + b.title.replace(" smoke-free", ""), fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 12.sp, color = Muted)
                }
            }
            repeat((4 - latest.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
    }
    open?.let { b -> AchievementDialog(b, s) { open = null } }
}

/** The Achievements page (Garden tab): score, tiers, what's close, and every achievement by group. */
@Composable
fun AchievementsScreen(modifier: Modifier) {
    val app = BatbloomApp.instance
    val badges by app.repo.badges.collectAsState(emptyList())
    val sessions by app.repo.sessions.collectAsState(emptyList())
    val schedule by app.prefs.schedule.collectAsState()
    val quit by app.prefs.quitDate.collectAsState()
    val doses by app.repo.doses.collectAsState(emptyList())
    val s = rememberAchievements(badges.associate { it.id to it.earnedAt }, schedule, quit, sessions, doses)
    var open by remember { mutableStateOf<Badge?>(null) }
    val earned = s.earnedIds.toSet()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Score card
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF2B2560), Color(0xFF16183A), Color(0xFF0E1026))), RoundedCornerShape(24.dp),
            ).padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SCORE", color = Color(0xFFB9C8FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        CountUpText(Badges.points(earned), "", MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), Color.White, " pts")
                        Text("${earned.size} of ${Badges.all.size} unlocked", color = Color(0xFFCBD2F0), fontSize = 13.sp)
                    }
                    val top = Badges.all.filter { it.id in earned }.maxWithOrNull(compareBy<Badge> { it.tier.ordinal }.thenBy { s.earnedAt[it.id] ?: 0 })
                    if (top != null) Emblem(top, true, 92.dp, animate = true)
                }
                Spacer(Modifier.height(12.dp))
                Meter(Badges.points(earned).toFloat() / Badges.maxPoints, Color(0xFFF5C542))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Tier.entries.forEach { t ->
                        val have = Badges.all.count { it.tier == t && it.id in earned }
                        val total = Badges.all.count { it.tier == t }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$have/$total", color = tierColor(t), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(t.label, color = Color(0xFF9AA3C7), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Almost there
        val close = Badges.all.filter { it.id !in earned && !it.secret }
            .mapNotNull { b -> s.ratio(b)?.takeIf { it > 0f }?.let { b to it } }
            .sortedByDescending { it.second }.take(3)
        if (close.isNotEmpty()) SectionCard("Almost there") {
            close.forEach { (b, r) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = b }.padding(vertical = 4.dp)) {
                    Emblem(b, false, 48.dp, r)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(b.title, fontWeight = FontWeight.SemiBold)
                        val (h, t) = s.progress(b)!!
                        Text(progressLabel(b, h, t), color = Muted, fontSize = 12.sp)
                        Meter(r, Color(b.accent), Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Badges.groups.forEach { g ->
            val list = Badges.all.filter { it.group == g }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${list.count { it.id in earned }}/${list.size}", color = Muted, fontSize = 13.sp)
            }
            list.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { b -> AchievementTile(b, b.id in earned, s, Modifier.weight(1f)) { open = b } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    open?.let { b -> AchievementDialog(b, s) { open = null } }
}

private fun progressLabel(b: Badge, have: Int, target: Int): String = when {
    Badges.target(b.id) == null -> "Day $have of $target"
    b.id.startsWith("hours") || b.id.startsWith("fmge") -> "$have of $target hours"
    b.id == "marathon" -> "Longest: $have of $target min"
    b.id.startsWith("streak") -> "Best streak: $have of $target days"
    b.id.startsWith("dose") -> "Best run: $have of $target days with every tablet"
    b.id.startsWith("meds") -> "Best run: $have of $target days with every medication"
    b.id.startsWith("mcq") -> "$have of $target questions"
    b.id.startsWith("mock") -> "Best: $have% of $target%"
    b.id.startsWith("breathe") -> "$have of $target cravings"
    b.id == "hat_trick" -> "Best day: $have of $target sessions"
    b.id == "all_subjects" -> "$have of $target subjects"
    else -> "$have of $target"
}

@Composable
private fun AchievementTile(b: Badge, earned: Boolean, s: AchievementState, modifier: Modifier, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier
            .background(if (earned) tierColor(b.tier).copy(alpha = 0.10f) else surface.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hidden = b.secret && !earned
        Emblem(b, earned, 76.dp, if (earned) null else s.ratio(b))
        Text(
            if (hidden) "???" else b.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp,
            color = if (earned) MaterialTheme.colorScheme.onSurface else Muted, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            when {
                earned -> b.tier.label.uppercase()
                hidden -> "SECRET"
                else -> s.progress(b)?.let { (h, t) -> "$h/$t" } ?: "Locked"
            },
            fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
            color = if (earned) tierText(b.tier) else Muted,
        )
    }
}

@Composable
private fun AchievementDialog(b: Badge, s: AchievementState, onDismiss: () -> Unit) {
    val earned = s.earnedAt[b.id]
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hidden = b.secret && earned == null
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("OK") } },
        dismissButton = if (earned != null) ({ TextButton({ AchievementShare.share(ctx, b, earned) }) { Text("Share") } }) else null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Emblem(b, earned != null, 150.dp, if (earned == null) s.ratio(b) else null, animate = true)
                TierPill(b.tier, earned != null)
                Text(if (hidden) "Secret achievement" else b.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Text(if (hidden) "Keep using Batbloom to find out what unlocks it." else b.text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                val due = Badges.dueDate(b.id, s.schedule, s.quit)
                val line = when {
                    earned != null -> "Unlocked " + fmt.format(Instant.ofEpochMilli(earned).atZone(zone))
                    due != null -> "Unlocks " + fmt.format(due)
                    else -> null
                }
                line?.let { Text(it, color = if (earned != null) tierText(b.tier) else Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                if (earned == null && !hidden) s.progress(b)?.let { (h, t) ->
                    Text(progressLabel(b, h, t), color = Muted, modifier = Modifier.padding(top = 10.dp))
                    Meter(h.toFloat() / t, Color(b.accent), Modifier.padding(top = 6.dp))
                }
            }
        },
    )
}

@Composable
private fun TierPill(t: Tier, earned: Boolean) {
    val c = tierText(t)
    Text(
        "${t.label.uppercase()} · ${t.points} PTS",
        color = if (earned) c else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 6.dp).background((if (earned) c else Muted).copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * Shows a full-screen "Achievement unlocked" moment for each achievement earned since the last time.
 * The first time it runs, everything already earned counts as seen.
 */
@Composable
fun AchievementCelebrations() {
    val app = BatbloomApp.instance
    val badges by app.repo.badges.collectAsState(null)
    val seen by app.prefs.seenBadges.collectAsState()
    val list = badges ?: return
    LaunchedEffect(list, seen) { if (seen == null) app.prefs.markBadgesSeen(list.map { it.id }) }
    val s = seen ?: return
    val next = list.filter { it.id !in s && Badges.all.any { b -> b.id == it.id } }.minByOrNull { it.earnedAt } ?: return
    androidx.compose.runtime.key(next.id) {
        UnlockCelebration(Badges.byId(next.id)) { app.prefs.markBadgesSeen(listOf(next.id)) }
    }
}

@Composable
private fun UnlockCelebration(b: Badge, onDone: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(b.id) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm) }
    val pop = remember(b.id) { Animatable(0f) }
    val fade = remember(b.id) { Animatable(0f) }
    LaunchedEffect(b.id) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(b.id) { kotlinx.coroutines.delay(350); fade.animateTo(1f, tween(600)) }
    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val tc = tierColor(b.tier)
    val accent = Color(b.accent)
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xE6070816))
                .clickable(remember { MutableInteractionSource() }, null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("ACHIEVEMENT UNLOCKED", color = tc, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 13.sp, modifier = Modifier.alpha(fade.value))
                Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2)
                        rotate(angle, c) {
                            repeat(12) { i ->
                                val a0 = (i * 30f) * PI.toFloat() / 180f
                                val a1 = (i * 30f + 12f) * PI.toFloat() / 180f
                                val r = size.minDimension * 0.5f
                                drawPath(Path().apply {
                                    moveTo(c.x, c.y); lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r); lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r); close()
                                }, Brush.radialGradient(listOf(tc.copy(alpha = 0.35f * pop.value.coerceAtMost(1f)), Color.Transparent), c, r))
                            }
                        }
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), c, size.minDimension * 0.4f), size.minDimension * 0.4f, c)
                    }
                    BloomBurst(Modifier.fillMaxSize(), tc)
                    Box(Modifier.scale(pop.value)) { Emblem(b, true, 200.dp, animate = true) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(fade.value)) {
                    Text(b.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "${b.tier.label.uppercase()} · +${b.tier.points} PTS",
                        color = tc, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = 8.dp).background(tc.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                    Text(b.text, color = Color(0xFFCBD2F0), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    Button(
                        onDone, Modifier.padding(top = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tc, contentColor = Color(0xFF14162C)),
                    ) { Text("Awesome!", fontWeight = FontWeight.Bold) }
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    TextButton({ AchievementShare.share(ctx, b, System.currentTimeMillis()) }) { Text("Share", color = tc) }
                }
            }
        }
    }
}
