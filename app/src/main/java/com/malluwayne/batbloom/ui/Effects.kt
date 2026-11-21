package com.malluwayne.batbloom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Sparkles rising from the plant when it blooms. Plays once. */
@Composable
fun BloomBurst(modifier: Modifier, color: Color) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val sparks = remember { Random(3).let { r -> List(34) { floatArrayOf(r.nextFloat() * 2f - 1f, 0.4f + r.nextFloat() * 0.6f, r.nextFloat() * 0.35f, r.nextFloat()) } } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val origin = Offset(w / 2, h * 0.45f)
        sparks.forEach { (dx, speed, delayF, hue) ->
            val p = ((t.value - delayF) / (1f - delayF)).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) return@forEach
            val c = origin + Offset(dx * w * 0.42f * p, -h * 0.42f * speed * p + h * 0.12f * p * p)
            val a = (1f - p) * 0.95f
            val col = if (hue > 0.5f) color else Gold
            val r = w * 0.012f * (1.2f - p)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), c, r * 5), r * 5, c)
            drawCircle(Color.White.copy(alpha = a), r, c)
        }
        // A soft ring opening out
        val rp = (t.value / 0.5f).coerceAtMost(1f)
        if (rp < 1f) drawCircle(color.copy(alpha = (1f - rp) * 0.6f), w * 0.1f + w * 0.35f * rp, origin, style = Stroke(w * 0.01f))
    }
}

/** A quick glowing ring whenever [key] changes (used when the plant reaches a new stage). */
@Composable
fun StagePulse(key: Any, modifier: Modifier, color: Color) {
    val t = remember { Animatable(1f) }
    var first by remember { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        if (first++ == 0) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val c = Offset(size.width / 2, size.height * 0.55f)
        drawCircle(color.copy(alpha = (1f - p) * 0.5f), size.minDimension * (0.15f + 0.4f * p), c, style = Stroke(size.minDimension * 0.012f))
        repeat(8) { i ->
            val a = i * PI.toFloat() / 4
            val r = size.minDimension * (0.18f + 0.3f * p)
            drawCircle(Color.White.copy(alpha = (1f - p) * 0.8f), size.minDimension * 0.008f, c + Offset(cos(a) * r, sin(a) * r))
        }
    }
}

/** Counts a number up from 0 (for coins earned). */
@Composable
fun CountUpText(target: Int, prefix: String, style: TextStyle, color: Color, suffix: String = "") {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        val steps = 24
        for (i in 1..steps) { shown = target * i / steps; delay(28) }
    }
    Text("$prefix$shown$suffix", style = style, color = color)
}
