package com.malluwayne.batbloom.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.quit.Milestones
import java.time.Duration
import java.time.format.DateTimeFormatter


/** "6d 14h 22m", "14h 03m", "22m". */
private fun longDuration(d: Duration): String {
    val days = d.toDays()
    val h = d.toHours() % 24
    val m = d.toMinutes() % 60
    return when {
        days > 0 -> "${days}d ${h}h ${"%02d".format(m)}m"
        h > 0 -> "${h}h ${"%02d".format(m)}m"
        else -> "${m}m"
    }
}

/**
 * The health milestones, front and centre: a live smoke-free clock, the next milestone with a ring, its exact
 * date and time, and a timeline. Tap for every milestone with the date it was (or will be) reached.
 */
@Composable
fun MilestoneHero(start: java.time.ZonedDateTime, sinceQuit: Duration, onQuitNow: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmQuit by rememberSaveable { mutableStateOf(false) }
    val pal = LocalPalette.current
    val HeroTeal = pal.teal
    val HeroText = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val HeroMuted = pal.muted
    val bg = when {
        pal.isLight -> listOf(Color(0xFFE3F4F0), Color(0xFFFFFFFF))
        pal.night == Color(0xFF000000) -> listOf(Color(0xFF07211D), Color(0xFF0B0B10))
        else -> listOf(Color(0xFF12302B), Color(0xFF1A1A20))
    }
    val track = HeroText.copy(alpha = 0.10f)
    val next = Milestones.next(sinceQuit)
    val reached = Milestones.reached(sinceQuit)
    val started = !sinceQuit.isNegative
    val step by animateFloatAsState(Milestones.stepProgress(sinceQuit), tween(900), label = "ring")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p",
    )
    val whenFmt = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "chev")

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(bg))
            .clickable { open = !open }
            .animateContentSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Live clock
        Column {
            Text(if (started) "SMOKE-FREE FOR" else "QUIT DAY IN", color = HeroTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(
                longDuration(if (started) sinceQuit else sinceQuit.negated()),
                color = HeroText, fontSize = 36.sp, fontWeight = FontWeight.Bold,
            )
            if (!started) {
                Text(start.format(DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a")), color = HeroMuted, fontSize = 13.sp)
                androidx.compose.material3.OutlinedButton({ confirmQuit = true }, Modifier.padding(top = 10.dp)) {
                    Text("Quit now instead", color = HeroTeal)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(84.dp)) {
                    val w = 8.dp.toPx()
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), arc, style = Stroke(w))
                    if (started) drawArc(
                        Brush.sweepGradient(listOf(HeroTeal.copy(alpha = 0.6f), HeroTeal, HeroTeal)),
                        -90f, 360f * step, false, Offset(w / 2, w / 2), arc, style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${reached.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HeroText)
                    Text("of ${Milestones.all.size}", fontSize = 10.sp, color = HeroMuted)
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                if (next == null) {
                    Text("Every milestone reached", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = HeroTeal)
                } else {
                    Text("NEXT", color = HeroMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(next.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HeroText)
                    Text(
                        (if (started) "in ${Milestones.formatLeft(next.after - sinceQuit)} · " else "") +
                            start.plus(next.after).format(whenFmt),
                        color = HeroTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(next.text, fontSize = 13.sp, color = HeroText.copy(alpha = 0.8f))
                }
            }
        }

        // Timeline
        Column {
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val n = Milestones.all.size
                val gap = size.width / n
                val cy = size.height / 2
                val xs = (0 until n).map { gap * (it + 0.5f) }
                drawLine(track, Offset(xs.first(), cy), Offset(xs.last(), cy), 2.dp.toPx())
                if (reached.isNotEmpty()) {
                    val last = (reached.size - 1).coerceAtMost(n - 1)
                    val endX = xs[last] + if (next != null) gap * step else 0f
                    drawLine(HeroTeal, Offset(xs.first(), cy), Offset(endX.coerceAtMost(xs.last()), cy), 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
                Milestones.all.forEachIndexed { i, m ->
                    val done = m in reached
                    val isNext = m == next && started
                    if (isNext) drawCircle(HeroTeal.copy(alpha = 0.25f * pulse), 10.dp.toPx(), Offset(xs[i], cy))
                    drawCircle(
                        when { done -> HeroTeal; isNext -> HeroTeal.copy(alpha = 0.35f); else -> HeroText.copy(alpha = 0.18f) },
                        if (done || isNext) 5.5.dp.toPx() else 4.dp.toPx(), Offset(xs[i], cy),
                    )
                    if (isNext) drawCircle(HeroTeal, 5.5.dp.toPx(), Offset(xs[i], cy), style = Stroke(1.5.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Milestones.all.forEach { m ->
                    Text(
                        m.short, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp,
                        color = if (m in reached) HeroTeal else if (m == next) HeroText else HeroMuted,
                        fontWeight = if (m == next) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (open) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Milestones.all.forEach { m ->
                val done = m in reached
                Row(verticalAlignment = Alignment.Top) {
                    Text(if (done) "✓" else "○", color = if (done) HeroTeal else HeroMuted, fontSize = 15.sp, modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if (done) HeroTeal else HeroText)
                            Text(start.plus(m.after).format(DateTimeFormatter.ofPattern("d MMM")), color = HeroMuted, fontSize = 12.sp)
                        }
                        Text(m.text, fontSize = 13.sp, color = HeroMuted)
                    }
                }
            }
        }
        Text(
            "⌄", color = HeroMuted, fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().rotate(chevron),
        )
    }
    if (confirmQuit) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmQuit = false },
        title = { Text("Quit now?") },
        text = { Text("Your quit time becomes right now. Milestones, savings and badges count from this moment.") },
        confirmButton = { androidx.compose.material3.TextButton({ confirmQuit = false; onQuitNow() }) { Text("Quit now") } },
        dismissButton = { androidx.compose.material3.TextButton({ confirmQuit = false }) { Text("Cancel") } },
    )
}
