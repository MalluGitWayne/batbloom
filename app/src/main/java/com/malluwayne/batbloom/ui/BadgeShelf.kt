@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malluwayne.batbloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.quit.Badge
import com.malluwayne.batbloom.quit.Badges
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.stats.Stats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A medal: ribbon, a disc in the badge's colour with a shine, and its mark. Locked medals are grey outlines. */
@Composable
fun Medal(b: Badge, earned: Boolean, size: Dp) {
    val c = Color(b.color)
    val muted = Muted
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val r = w * 0.36f
            val cx = w / 2
            val cy = w * 0.58f
            // ribbon
            if (earned) {
                val ribbon = c.copy(alpha = 0.75f)
                drawPath(Path().apply { moveTo(cx - r * 0.75f, 0f); lineTo(cx - r * 0.05f, 0f); lineTo(cx + r * 0.25f, cy - r * 0.6f); lineTo(cx - r * 0.25f, cy - r * 0.45f); close() }, ribbon)
                drawPath(Path().apply { moveTo(cx + r * 0.75f, 0f); lineTo(cx + r * 0.05f, 0f); lineTo(cx - r * 0.25f, cy - r * 0.6f); lineTo(cx + r * 0.25f, cy - r * 0.45f); close() }, ribbon.copy(alpha = 0.55f))
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f), Color.Transparent), Offset(cx, cy), r * 1.6f), r * 1.6f, Offset(cx, cy))
                drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f).compositeOver(c, 0.35f), c, c.darken(0.35f)), Offset(cx - r, cy - r), Offset(cx + r, cy + r)), r, Offset(cx, cy))
                drawCircle(c.darken(0.45f), r * 0.82f, Offset(cx, cy), style = Stroke(r * 0.07f))
            } else {
                drawCircle(outline, r, Offset(cx, cy), style = Stroke(r * 0.12f))
            }
        }
        Text(
            b.mark, modifier = Modifier.padding(top = size * 0.16f),
            color = if (earned) c.darken(0.7f) else muted, fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (b.mark.length >= 3) 0.2f else 0.26f).sp,
        )
    }
}

private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
private fun Color.compositeOver(base: Color, amount: Float) = Color(
    base.red + (red - base.red) * amount, base.green + (green - base.green) * amount, base.blue + (blue - base.blue) * amount, 1f,
)

/** All badges as medals, earned ones in colour. Tap one for what it is and when. */
@Composable
fun BadgeShelf(earnedAt: Map<String, Long>, schedule: DoseSchedule, quit: LocalDate?, sessions: List<SessionEntity>) {
    var open by remember { mutableStateOf<Badge?>(null) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val st = remember(sessions, today) { Stats.compute(sessions, today, zone) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Badges", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${earnedAt.keys.count { k -> Badges.all.any { it.id == k } }} of ${Badges.all.size}", color = Muted, fontSize = 13.sp)
        }
        for (group in listOf("Quit", "Focus")) {
            Text(group, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Badges.all.filter { it.group == group }.forEach { b ->
                    val earned = b.id in earnedAt
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(66.dp).clickable { open = b },
                    ) {
                        Medal(b, earned, 56.dp)
                        Text(
                            b.title.replace(" smoke-free", ""), fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 12.sp,
                            color = if (earned) MaterialTheme.colorScheme.onSurface else Muted,
                        )
                    }
                }
            }
        }
    }
    open?.let { b ->
        val earned = earnedAt[b.id]
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton({ open = null }) { Text("OK") } },
            title = null,
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Medal(b, earned != null, 110.dp)
                    Text(b.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(b.text, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    val due = Badges.dueDate(b.id, schedule, quit)
                    val target = Badges.target(b.id)
                    val line = when {
                        earned != null -> "Earned " + fmt.format(Instant.ofEpochMilli(earned).atZone(zone))
                        due != null -> "Unlocks " + fmt.format(due)
                        else -> null
                    }
                    line?.let { Text(it, color = if (earned != null) Color(b.color) else Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                    if (earned == null && target != null) {
                        val have = if (b.id.startsWith("streak")) st.streakDays else st.completed
                        Text("$have of $target", color = Muted, modifier = Modifier.padding(top = 12.dp))
                        LinearProgressIndicator({ (have.toFloat() / target).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp), color = Color(b.color))
                    }
                }
            },
        )
    }
}
