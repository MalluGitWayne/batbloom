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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Deep = Color(0xFF0F2A2A)
private val Deeper = Color(0xFF101A33)

/**
 * The health milestones, front and centre: the next one with a ring and a countdown, and a timeline of all nine.
 * Tap to see the full list.
 */
@Composable
fun MilestoneHero(quitDay: LocalDate, sinceQuit: Duration) {
    var open by rememberSaveable { mutableStateOf(false) }
    val next = Milestones.next(sinceQuit)
    val reached = Milestones.reached(sinceQuit)
    val step by animateFloatAsState(Milestones.stepProgress(sinceQuit), tween(900), label = "ring")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "p",
    )
    val started = !sinceQuit.isNegative

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Deep, Deeper)))
            .clickable { open = !open }
            .animateContentSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("HEALTH MILESTONES", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ring: progress toward the next milestone
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val w = 9.dp.toPx()
                    val inset = w / 2
                    val arcSize = Size(size.width - w, size.height - w)
                    drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                    if (started) drawArc(
                        Brush.sweepGradient(listOf(Teal.copy(alpha = 0.6f), Teal, Color(0xFFB8FFF0))),
                        -90f, 360f * step, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${reached.size}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("of ${Milestones.all.size}", fontSize = 11.sp, color = Muted)
                }
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                when {
                    !started -> {
                        Text("Starts on quit day", color = Muted, fontSize = 13.sp)
                        Text(quitDay.format(DateTimeFormatter.ofPattern("EEE d MMM")), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Your body starts recovering 20 minutes after your last cigarette.", fontSize = 13.sp, color = Muted)
                    }
                    next == null -> {
                        Text("Every milestone reached", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Teal)
                        Text("A full year smoke-free. Remarkable.", fontSize = 13.sp, color = Muted)
                    }
                    else -> {
                        Text("Next · in ${Milestones.formatLeft(next.after - sinceQuit)}", color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(next.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(next.text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }

        // Timeline of all nine
        Column {
            Box(Modifier.fillMaxWidth().height(22.dp)) {
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    val n = Milestones.all.size
                    val gap = size.width / n
                    val cy = size.height / 2
                    val xs = (0 until n).map { gap * (it + 0.5f) }
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(xs.first(), cy), Offset(xs.last(), cy), 2.dp.toPx())
                    if (reached.isNotEmpty()) {
                        val endIdx = (reached.size - 1).coerceAtMost(n - 1)
                        val partial = if (next != null && reached.size < n) step else 0f
                        val endX = xs[endIdx] + if (reached.size < n) gap * partial else 0f
                        drawLine(Teal, Offset(xs.first(), cy), Offset(endX, cy), 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                    Milestones.all.forEachIndexed { i, m ->
                        val done = m in reached
                        val isNext = m == next && started
                        if (isNext) drawCircle(Teal.copy(alpha = 0.25f * pulse), 10.dp.toPx(), Offset(xs[i], cy))
                        drawCircle(
                            when { done -> Teal; isNext -> Color(0xFF1C4A46); else -> Color(0xFF2A3050) },
                            if (done || isNext) 5.5.dp.toPx() else 4.dp.toPx(), Offset(xs[i], cy),
                        )
                        if (isNext) drawCircle(Teal, 5.5.dp.toPx(), Offset(xs[i], cy), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Milestones.all.forEach { m ->
                    Text(
                        m.short, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp,
                        color = if (m in reached) Teal else if (m == next) Color.White else Muted,
                        fontWeight = if (m == next) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Milestones.all.forEach { m ->
                    val done = m in reached
                    Row(verticalAlignment = Alignment.Top) {
                        Text(if (done) "✓" else "○", color = if (done) Teal else Muted, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(m.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if (done) Teal else MaterialTheme.colorScheme.onSurface)
                                if (!done && started) Text("${(Milestones.progress(m, sinceQuit) * 100).roundToInt()}%", color = Muted, fontSize = 12.sp)
                            }
                            Text(m.text, fontSize = 13.sp, color = Muted)
                        }
                    }
                }
                Text("Approximate, based on commonly cited public-health guidance. Everyone's body is different.", color = Muted, fontSize = 11.sp)
            }
        } else {
            Text("Tap to see all milestones", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
