package com.malluwayne.batbloom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A thin progress bar on a quiet neutral track. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(modifier.fillMaxWidth().height(height).background(track, RoundedCornerShape(50))) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, RoundedCornerShape(50)))
    }
}

/**
 * A bar chart: slim rounded bars, the selected one (default [selected]) full colour with its value above the chart,
 * the rest faded. Tap a bar to read it. [reference] draws a dashed line (an average or a goal) labelled [referenceLabel].
 * [labels] are the axis labels; an empty string hides one. [names] are the full names shown when a bar is selected.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    names: List<String>,
    color: Color,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    selected: Int = values.lastIndex,
    reference: Float? = null,
    referenceLabel: String? = null,
    height: Dp = 132.dp,
) {
    if (values.isEmpty()) return
    var sel by remember(values.size, selected) { mutableIntStateOf(selected.coerceIn(0, values.lastIndex)) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(reference ?: 0f).coerceAtLeast(1f) * 1.12f
    val muted = Muted
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val stub = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val desc = names.zip(values).joinToString(", ") { (n, v) -> "$n ${format(v)}" }
    Column(modifier.fillMaxWidth()) {
        // The selected bar's value, where the eye starts.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(format(values[sel]), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("  " + names.getOrElse(sel) { "" }, color = muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Canvas(
            Modifier.fillMaxWidth().height(height).padding(top = 8.dp)
                .semantics { contentDescription = desc }
                .pointerInput(values.size) {
                    detectTapGestures { o -> sel = (o.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex) }
                },
        ) {
            val gap = size.width / values.size
            val bw = (gap * if (values.size > 14) 0.62f else 0.46f).coerceAtMost(28.dp.toPx())
            val r = (bw / 2).coerceAtMost(6.dp.toPx())
            val h = size.height
            drawLine(base, Offset(0f, h), Offset(size.width, h), 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = i * gap + (gap - bw) / 2
                if (v <= 0f) {
                    drawRoundRect(stub, Offset(x, h - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
                    return@forEachIndexed
                }
                val bh = (v / max * h * grow.value).coerceAtLeast(3.dp.toPx())
                // Rounded top, square bottom.
                val path = Path().apply {
                    addRoundRect(RoundRect(x, h - bh, x + bw, h, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r)))
                }
                drawPath(path, if (i == sel) color else color.copy(alpha = 0.32f))
            }
            reference?.takeIf { it > 0f }?.let { ref ->
                val y = h - ref / max * h
                drawLine(muted.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, maxLines = 1,
                    color = if (i == sel) MaterialTheme.colorScheme.onSurface else muted,
                    fontWeight = if (i == sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        if (reference != null && referenceLabel != null) Text(
            "- - $referenceLabel", color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A line chart of percentages (mock tests): soft fill, points, the latest labelled, and a dashed [pass] line. */
@Composable
fun LineChart(values: List<Int>, names: List<String>, pass: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    if (values.size < 2) return
    val muted = Muted
    val ok = Teal
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val lo = (minOf(values.min(), pass) - 8).coerceAtLeast(0)
    val hi = (maxOf(values.max(), pass) + 8).coerceAtMost(100)
    val desc = "Scores: " + names.zip(values).joinToString(", ") { (n, v) -> "$n $v%" } + ". Pass mark $pass%."
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = desc }) {
            val pad = 14.dp.toPx()
            val y = { p: Int -> pad + (size.height - 2 * pad) * (1f - (p - lo).toFloat() / (hi - lo).coerceAtLeast(1)) }
            val step = (size.width - 2 * pad) / (values.size - 1)
            val pts = values.mapIndexed { i, v -> Offset(pad + i * step, y(v)) }
            // Pass line
            drawLine(muted.copy(alpha = 0.6f), Offset(0f, y(pass)), Offset(size.width, y(pass)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val shown = (pts.size * grow.value).toInt().coerceIn(1, pts.size)
            val line = Path().apply { pts.take(shown).forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val area = Path().apply { addPath(line); lineTo(pts[shown - 1].x, size.height); lineTo(pts[0].x, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), 0f, size.height))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            pts.take(shown).forEachIndexed { i, p ->
                val last = i == pts.lastIndex
                drawCircle(if (values[i] >= pass) ok else color, if (last) 5.dp.toPx() else 3.5.dp.toPx(), p)
                drawCircle(surface, if (last) 2.dp.toPx() else 1.5.dp.toPx(), p)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(names.first(), color = muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("- - pass $pass%", color = muted, fontSize = 11.sp)
            Text(names.last(), color = muted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}
