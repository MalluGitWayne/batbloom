package com.malluwayne.batbloom.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.malluwayne.batbloom.quit.Badge
import com.malluwayne.batbloom.quit.Glyph
import com.malluwayne.batbloom.quit.Tier
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Light, mid and dark tones of each tier's metal. */
private fun metal(t: Tier): List<Color> = when (t) {
    Tier.BRONZE -> listOf(Color(0xFFFFD1A6), Color(0xFFD08A52), Color(0xFF7A4321))
    Tier.SILVER -> listOf(Color(0xFFFFFFFF), Color(0xFFB9C3CF), Color(0xFF5D6874))
    Tier.GOLD -> listOf(Color(0xFFFFF4C2), Color(0xFFF2B92E), Color(0xFF8C5A06))
    Tier.PLATINUM -> listOf(Color(0xFFF2FBFF), Color(0xFFB7D0FF), Color(0xFF8C7BE0))
}

/** The colour that stands for a tier (labels, glows, progress). */
fun tierColor(t: Tier): Color = when (t) {
    Tier.BRONZE -> Color(0xFFE09A62)
    Tier.SILVER -> Color(0xFFC6D0DC)
    Tier.GOLD -> Color(0xFFF5C542)
    Tier.PLATINUM -> Color(0xFFB9C8FF)
}

/** [tierColor] made readable on the current surface (darker in the light theme). */
@Composable
fun tierText(t: Tier): Color = if (androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f) tierColor(t) else when (t) {
    Tier.BRONZE -> Color(0xFFA8622E)
    Tier.SILVER -> Color(0xFF6B7685)
    Tier.GOLD -> Color(0xFFA87A00)
    Tier.PLATINUM -> Color(0xFF5C63C9)
}

private val LockedLight = Color(0xFF4A4E5E)
private val LockedDark = Color(0xFF22242E)
private val Face = listOf(Color(0xFF2E3360), Color(0xFF14162C))

/**
 * An achievement emblem: a shape and metal by tier (bronze medal, silver shield, gold laurel hexagon,
 * platinum bat-winged star) around a night-sky face with the badge's picture.
 * Locked ones are dark, with a lock and an optional [progress] ring (0..1).
 * [animate] sweeps a shine across earned emblems.
 */
@Composable
fun Emblem(b: Badge, earned: Boolean, size: Dp, progress: Float? = null, animate: Boolean = false) {
    val shine = if (animate && earned) {
        val tr = rememberInfiniteTransition(label = "shine")
        val v by tr.animateFloat(
            -0.6f, 1.6f,
            infiniteRepeatable(keyframes { durationMillis = 3200; -0.6f at 0 using LinearEasing; 1.6f at 1300; 1.6f at 3200 }, RepeatMode.Restart),
            label = "shineX",
        )
        v
    } else 0.28f
    val accent = Color(b.accent)
    Canvas(Modifier.size(size)) { drawEmblem(b.tier, b.glyph, accent, earned, progress, shine, animate && earned) }
}

private fun DrawScope.drawEmblem(tier: Tier, glyph: Glyph, accent: Color, earned: Boolean, progress: Float?, shine: Float, moving: Boolean) {
    val w = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val r = w * 0.38f
    val m = metal(tier)

    if (earned) {
        drawCircle(Brush.radialGradient(listOf(tierColor(tier).copy(alpha = 0.45f), accent.copy(alpha = 0.12f), Color.Transparent), c, r * 1.4f), r * 1.4f, c)
    }

    // Ornaments behind the shape
    when (tier) {
        Tier.GOLD -> laurel(c, r, if (earned) m else listOf(LockedLight, LockedLight, LockedDark))
        Tier.PLATINUM -> {
            val wing = batPath(Offset(c.x, c.y + r * 0.05f), r * 1.32f)
            drawPath(wing, if (earned) Brush.verticalGradient(listOf(m[2], Color(0xFF3A2F7A)), c.y - r, c.y + r) else Brush.verticalGradient(listOf(LockedLight, LockedDark)))
        }
        else -> {}
    }

    val outer = shape(tier, c, r)
    val inner = shape(tier, c, r * 0.78f)
    if (earned) {
        drawPath(outer, Brush.linearGradient(listOf(m[0], m[1], m[2], m[1]), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
        if (tier == Tier.BRONZE) beads(c, r * 0.89f, m[2].copy(alpha = 0.55f), r * 0.035f)
        drawPath(inner, Brush.radialGradient(Face, Offset(c.x - r * 0.2f, c.y - r * 0.3f), r * 1.1f))
        drawPath(inner, m[2], style = Stroke(r * 0.05f))
        drawPath(inner, m[0].copy(alpha = 0.35f), style = Stroke(r * 0.015f))
        // tiny stars on the face
        listOf(Offset(-0.42f, -0.38f), Offset(0.45f, -0.28f), Offset(-0.3f, 0.45f), Offset(0.38f, 0.42f)).forEach { o ->
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.022f, c + o * r)
        }
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent), c, r * 0.62f), r * 0.62f, c)
        glyph(glyph, c, r * 0.42f, accent, Color(0xFF14162C), Color(0xFFFFE08A), Color(0xFFFF6B6B))
        // shine band
        clipPath(outer) {
            val x = c.x - r + shine * 2 * r
            val band = Path().apply {
                moveTo(x - r * 0.25f, c.y - r * 1.2f); lineTo(x + r * 0.05f, c.y - r * 1.2f)
                lineTo(x - r * 0.45f, c.y + r * 1.2f); lineTo(x - r * 0.75f, c.y + r * 1.2f); close()
            }
            drawPath(band, Color.White.copy(alpha = if (moving) 0.32f else 0.16f))
        }
        if (tier == Tier.GOLD || tier == Tier.PLATINUM) {
            sparkle(c + Offset(r * 0.95f, -r * 0.85f), r * 0.16f, Color.White)
            sparkle(c + Offset(-r * 1.0f, r * 0.7f), r * 0.1f, Color.White.copy(alpha = 0.8f))
        }
    } else {
        drawPath(outer, Brush.linearGradient(listOf(LockedLight, LockedDark), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)))
        drawPath(inner, Color(0xFF15161E))
        drawPath(inner, Color(0xFF2E3140), style = Stroke(r * 0.04f))
        glyph(glyph, c + Offset(0f, -r * 0.06f), r * 0.36f, Color(0xFF3E4252), Color(0xFF15161E), Color(0xFF4A4E5E), Color(0xFF4A4E5E))
        lock(c + Offset(0f, r * 0.62f), r * 0.2f)
        if (progress != null) {
            val rr = r * 1.17f
            val tl = Offset(c.x - rr, c.y - rr)
            val sz = Size(rr * 2, rr * 2)
            if (progress > 0f) drawArc(accent.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = Stroke(r * 0.07f))
            if (progress > 0f) drawArc(accent, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, sz, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
        }
    }
}

private fun Color.compositeOverOpaque(base: Color) = Color(
    red * alpha + base.red * (1 - alpha), green * alpha + base.green * (1 - alpha), blue * alpha + base.blue * (1 - alpha), 1f,
)

private fun shape(t: Tier, c: Offset, r: Float): Path = Path().apply {
    when (t) {
        Tier.BRONZE -> addOval(androidx.compose.ui.geometry.Rect(c, r))
        Tier.SILVER -> {
            moveTo(c.x - r * 0.84f, c.y - r * 0.74f)
            quadraticTo(c.x - r * 0.4f, c.y - r * 0.78f, c.x, c.y - r * 1.0f)
            quadraticTo(c.x + r * 0.4f, c.y - r * 0.78f, c.x + r * 0.84f, c.y - r * 0.74f)
            lineTo(c.x + r * 0.84f, c.y + r * 0.02f)
            cubicTo(c.x + r * 0.84f, c.y + r * 0.55f, c.x + r * 0.38f, c.y + r * 0.86f, c.x, c.y + r * 1.02f)
            cubicTo(c.x - r * 0.38f, c.y + r * 0.86f, c.x - r * 0.84f, c.y + r * 0.55f, c.x - r * 0.84f, c.y + r * 0.02f)
            close()
        }
        Tier.GOLD -> polygon(c, r * 0.98f, 6, -90f, 1f)
        Tier.PLATINUM -> polygon(c, r * 1.02f, 8, -90f, 0.8f)
    }
}

/** A regular polygon (inner = 1) or a star with [n] points (inner < 1). */
private fun Path.polygon(c: Offset, r: Float, n: Int, startDeg: Float, inner: Float) {
    val pts = if (inner >= 1f) n else n * 2
    for (i in 0 until pts) {
        val a = (startDeg + 360f * i / pts) * PI.toFloat() / 180f
        val rr = if (inner < 1f && i % 2 == 1) r * inner else r
        val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun DrawScope.beads(c: Offset, r: Float, color: Color, dot: Float) {
    repeat(28) { i ->
        val a = i * 2 * PI.toFloat() / 28
        drawCircle(color, dot, Offset(c.x + cos(a) * r, c.y + sin(a) * r))
    }
}

private fun DrawScope.laurel(c: Offset, r: Float, m: List<Color>) {
    for (side in listOf(-1f, 1f)) {
        for (i in 0 until 6) {
            // φ runs from near the bottom (40°) up the side (140°); leaves point along the circle, tilted out.
            val phi = 40f + i * 20f
            val a = phi * PI.toFloat() / 180f
            val p = Offset(c.x + side * sin(a) * r * 1.1f, c.y + cos(a) * r * 1.1f)
            val leafAngle = side * (90f - phi + 18f)
            rotate(leafAngle, p) {
                drawOval(Brush.linearGradient(listOf(m[0], m[1], m[2]), Offset(p.x - r * 0.1f, p.y), Offset(p.x + r * 0.1f, p.y)),
                    Offset(p.x - r * 0.09f, p.y - r * 0.2f), Size(r * 0.18f, r * 0.4f))
            }
        }
    }
}

private fun DrawScope.sparkle(p: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(p.x, p.y - s); quadraticTo(p.x, p.y, p.x + s, p.y); quadraticTo(p.x, p.y, p.x, p.y + s)
        quadraticTo(p.x, p.y, p.x - s, p.y); quadraticTo(p.x, p.y, p.x, p.y - s); close()
    }
    drawPath(path, color)
}

private fun DrawScope.lock(p: Offset, s: Float) {
    val body = Color(0xFF6B7083)
    drawArc(body, 180f, 180f, false, Offset(p.x - s * 0.55f, p.y - s * 1.1f), Size(s * 1.1f, s * 1.1f), style = Stroke(s * 0.25f))
    drawRoundRect(body, Offset(p.x - s * 0.8f, p.y - s * 0.55f), Size(s * 1.6f, s * 1.2f), CornerRadius(s * 0.25f))
    drawCircle(Color(0xFF15161E), s * 0.18f, Offset(p.x, p.y + s * 0.02f))
}

/** Bat silhouette centred on [c], half-width [s]. */
private fun batPath(c: Offset, s: Float): Path {
    val right = listOf(
        0f to -0.12f, 0.1f to -0.18f, 0.14f to -0.38f, 0.2f to -0.12f, 0.42f to -0.28f, 1f to -0.34f,
        0.86f to 0.02f, 0.7f to -0.02f, 0.58f to 0.22f, 0.4f to 0.08f, 0.22f to 0.32f, 0f to 0.18f,
    )
    val pts = right + right.drop(1).dropLast(1).reversed().map { (x, y) -> -x to y }
    return Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(c.x + x * s, c.y + y * s) else lineTo(c.x + x * s, c.y + y * s) }
        close()
    }
}

/** Draws a badge picture centred on [c], about 2*[s] across, in [col]; [bg] is the face colour for cut-outs. */
private fun DrawScope.glyph(g: Glyph, c: Offset, s: Float, col: Color, bg: Color, hi: Color, red: Color) {
    fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    val stroke = Stroke(s * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (g) {
        Glyph.PILL -> rotate(-40f, c) {
            drawRoundRect(col, p(-0.95f, -0.4f), Size(s * 1.9f, s * 0.8f), CornerRadius(s * 0.4f))
            clipRect(left = c.x) { drawRoundRect(Color.White.copy(alpha = 0.55f), p(-0.95f, -0.4f), Size(s * 1.9f, s * 0.8f), CornerRadius(s * 0.4f)) }
            drawLine(bg.copy(alpha = 0.5f), p(0f, -0.4f), p(0f, 0.4f), s * 0.06f)
        }
        Glyph.NO_SMOKE -> {
            drawRect(col.copy(alpha = 0.9f), p(-0.6f, -0.12f), Size(s * 0.9f, s * 0.24f))
            drawRect(hi, p(0.3f, -0.12f), Size(s * 0.25f, s * 0.24f))
            drawCircle(red, s * 0.9f, c, style = Stroke(s * 0.16f))
            drawLine(red, p(-0.64f, -0.64f), p(0.64f, 0.64f), s * 0.16f)
        }
        Glyph.HEART -> drawPath(Path().apply {
            moveTo(c.x, c.y + s * 0.85f)
            cubicTo(c.x - s * 1.2f, c.y + s * 0.05f, c.x - s * 0.9f, c.y - s * 0.95f, c.x, c.y - s * 0.35f)
            cubicTo(c.x + s * 0.9f, c.y - s * 0.95f, c.x + s * 1.2f, c.y + s * 0.05f, c.x, c.y + s * 0.85f); close()
        }, col)
        Glyph.CALENDAR -> {
            drawRoundRect(col, p(-0.8f, -0.65f), Size(s * 1.6f, s * 1.45f), CornerRadius(s * 0.2f), style = Stroke(s * 0.13f))
            drawRoundRect(col, p(-0.8f, -0.65f), Size(s * 1.6f, s * 0.42f), CornerRadius(s * 0.2f))
            drawLine(col, p(-0.4f, -0.9f), p(-0.4f, -0.5f), s * 0.14f, StrokeCap.Round)
            drawLine(col, p(0.4f, -0.9f), p(0.4f, -0.5f), s * 0.14f, StrokeCap.Round)
            drawPath(Path().apply { moveTo(p(-0.38f, 0.22f).x, p(-0.38f, 0.22f).y); lineTo(p(-0.08f, 0.5f).x, p(-0.08f, 0.5f).y); lineTo(p(0.42f, -0.05f).x, p(0.42f, -0.05f).y) }, col, style = stroke)
        }
        Glyph.MOON -> {
            val a = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, s * 0.9f)) }
            val b = Path().apply { addOval(androidx.compose.ui.geometry.Rect(p(0.45f, -0.35f), s * 0.78f)) }
            drawPath(Path.combine(PathOperation.Difference, a, b), col)
            sparkle(p(0.55f, 0.35f), s * 0.22f, col)
        }
        Glyph.HALF -> {
            drawCircle(col, s * 0.85f, c, style = Stroke(s * 0.14f))
            drawArc(col, 90f, 180f, true, p(-0.85f, -0.85f), Size(s * 1.7f, s * 1.7f))
        }
        Glyph.TROPHY -> {
            drawPath(Path().apply {
                moveTo(p(-0.6f, -0.8f).x, p(-0.6f, -0.8f).y); lineTo(p(0.6f, -0.8f).x, p(0.6f, -0.8f).y)
                cubicTo(p(0.6f, 0.1f).x, p(0.6f, 0.1f).y, p(0.3f, 0.3f).x, p(0.3f, 0.3f).y, c.x, p(0f, 0.32f).y)
                cubicTo(p(-0.3f, 0.3f).x, p(-0.3f, 0.3f).y, p(-0.6f, 0.1f).x, p(-0.6f, 0.1f).y, p(-0.6f, -0.8f).x, p(-0.6f, -0.8f).y); close()
            }, col)
            drawArc(col, 90f, 180f, false, p(-0.95f, -0.65f), Size(s * 0.7f, s * 0.6f), style = Stroke(s * 0.12f))
            drawArc(col, -90f, 180f, false, p(0.25f, -0.65f), Size(s * 0.7f, s * 0.6f), style = Stroke(s * 0.12f))
            drawRect(col, p(-0.1f, 0.3f), Size(s * 0.2f, s * 0.3f))
            drawRoundRect(col, p(-0.45f, 0.6f), Size(s * 0.9f, s * 0.25f), CornerRadius(s * 0.06f))
            sparkle(p(-0.18f, -0.42f), s * 0.18f, Color.White.copy(alpha = 0.7f))
        }
        Glyph.STAR -> drawPath(Path().apply { polygon(p(0f, 0.05f), s * 0.95f, 5, -90f, 0.45f) }, col)
        Glyph.CROWN -> {
            drawPath(Path().apply {
                moveTo(p(-0.85f, 0.45f).x, p(-0.85f, 0.45f).y); lineTo(p(-0.9f, -0.45f).x, p(-0.9f, -0.45f).y)
                lineTo(p(-0.45f, -0.05f).x, p(-0.45f, -0.05f).y); lineTo(p(0f, -0.7f).x, p(0f, -0.7f).y)
                lineTo(p(0.45f, -0.05f).x, p(0.45f, -0.05f).y); lineTo(p(0.9f, -0.45f).x, p(0.9f, -0.45f).y)
                lineTo(p(0.85f, 0.45f).x, p(0.85f, 0.45f).y); close()
            }, col)
            drawRoundRect(col, p(-0.85f, 0.55f), Size(s * 1.7f, s * 0.25f), CornerRadius(s * 0.08f))
            listOf(-0.9f to -0.55f, 0f to -0.82f, 0.9f to -0.55f).forEach { (x, y) -> drawCircle(col, s * 0.12f, p(x, y)) }
            drawCircle(bg.copy(alpha = 0.6f), s * 0.1f, p(0f, 0.15f))
        }
        Glyph.FLOWER -> {
            repeat(6) { i ->
                rotate(i * 60f, c) { drawOval(col, p(-0.24f, -0.95f), Size(s * 0.48f, s * 0.8f)) }
            }
            drawCircle(hi, s * 0.28f, c)
        }
        Glyph.FLAME -> {
            drawPath(Path().apply {
                moveTo(c.x, p(0f, -0.95f).y)
                cubicTo(p(0.25f, -0.45f).x, p(0.25f, -0.45f).y, p(0.8f, -0.15f).x, p(0.8f, -0.15f).y, p(0.7f, 0.3f).x, p(0.7f, 0.3f).y)
                cubicTo(p(0.6f, 0.75f).x, p(0.6f, 0.75f).y, p(0.25f, 0.92f).x, p(0.25f, 0.92f).y, c.x, p(0f, 0.92f).y)
                cubicTo(p(-0.25f, 0.92f).x, p(-0.25f, 0.92f).y, p(-0.6f, 0.75f).x, p(-0.6f, 0.75f).y, p(-0.7f, 0.3f).x, p(-0.7f, 0.3f).y)
                cubicTo(p(-0.75f, -0.05f).x, p(-0.75f, -0.05f).y, p(-0.45f, -0.25f).x, p(-0.45f, -0.25f).y, p(-0.35f, -0.45f).x, p(-0.35f, -0.45f).y)
                cubicTo(p(-0.2f, -0.2f).x, p(-0.2f, -0.2f).y, p(-0.1f, -0.6f).x, p(-0.1f, -0.6f).y, c.x, p(0f, -0.95f).y); close()
            }, col)
            drawOval(hi, p(-0.28f, 0.1f), Size(s * 0.56f, s * 0.75f))
        }
        Glyph.HOURGLASS -> {
            drawLine(col, p(-0.6f, -0.85f), p(0.6f, -0.85f), s * 0.16f, StrokeCap.Round)
            drawLine(col, p(-0.6f, 0.85f), p(0.6f, 0.85f), s * 0.16f, StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(p(-0.48f, -0.75f).x, p(-0.48f, -0.75f).y); lineTo(p(0.48f, -0.75f).x, p(0.48f, -0.75f).y)
                lineTo(p(0.08f, 0f).x, c.y); lineTo(p(0.48f, 0.75f).x, p(0.48f, 0.75f).y); lineTo(p(-0.48f, 0.75f).x, p(-0.48f, 0.75f).y)
                lineTo(p(-0.08f, 0f).x, c.y); close()
            }, col, style = Stroke(s * 0.12f, join = StrokeJoin.Round))
            drawPath(Path().apply {
                moveTo(p(-0.3f, -0.45f).x, p(-0.3f, -0.45f).y); lineTo(p(0.3f, -0.45f).x, p(0.3f, -0.45f).y); lineTo(c.x, p(0f, -0.05f).y); close()
                moveTo(p(-0.38f, 0.68f).x, p(-0.38f, 0.68f).y); lineTo(p(0.38f, 0.68f).x, p(0.38f, 0.68f).y); lineTo(c.x, p(0f, 0.35f).y); close()
            }, col)
        }
        Glyph.BOLT -> drawPath(Path().apply {
            val pts = listOf(0.15f to -0.95f, -0.6f to 0.12f, -0.05f to 0.12f, -0.2f to 0.95f, 0.6f to -0.18f, 0.05f to -0.18f)
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(p(x, y).x, p(x, y).y) else lineTo(p(x, y).x, p(x, y).y) }; close()
        }, col)
        Glyph.BAT -> {
            drawPath(batPath(c, s * 1.05f), col)
            drawCircle(hi, s * 0.05f, p(-0.06f, -0.05f))
            drawCircle(hi, s * 0.05f, p(0.06f, -0.05f))
        }
        Glyph.SUN -> {
            drawCircle(col, s * 0.42f, c)
            repeat(8) { i ->
                val a = i * PI.toFloat() / 4
                drawLine(col, Offset(c.x + cos(a) * s * 0.62f, c.y + sin(a) * s * 0.62f), Offset(c.x + cos(a) * s * 0.92f, c.y + sin(a) * s * 0.92f), s * 0.14f, StrokeCap.Round)
            }
        }
        Glyph.BOOK -> {
            drawPath(Path().apply {
                moveTo(c.x, p(0f, -0.5f).y); quadraticTo(p(-0.45f, -0.75f).x, p(-0.45f, -0.75f).y, p(-0.95f, -0.6f).x, p(-0.95f, -0.6f).y)
                lineTo(p(-0.95f, 0.6f).x, p(-0.95f, 0.6f).y); quadraticTo(p(-0.45f, 0.45f).x, p(-0.45f, 0.45f).y, c.x, p(0f, 0.75f).y); close()
            }, col)
            drawPath(Path().apply {
                moveTo(c.x, p(0f, -0.5f).y); quadraticTo(p(0.45f, -0.75f).x, p(0.45f, -0.75f).y, p(0.95f, -0.6f).x, p(0.95f, -0.6f).y)
                lineTo(p(0.95f, 0.6f).x, p(0.95f, 0.6f).y); quadraticTo(p(0.45f, 0.45f).x, p(0.45f, 0.45f).y, c.x, p(0f, 0.75f).y); close()
            }, col.copy(alpha = 0.75f))
            drawLine(bg.copy(alpha = 0.7f), p(0f, -0.5f), p(0f, 0.75f), s * 0.06f)
            listOf(-0.25f, 0f, 0.25f).forEach { y -> drawLine(bg.copy(alpha = 0.35f), p(-0.75f, y - 0.12f), p(-0.2f, y - 0.05f), s * 0.05f) }
        }
        Glyph.TARGET -> {
            drawCircle(col, s * 0.92f, c)
            drawCircle(bg, s * 0.7f, c)
            drawCircle(col, s * 0.48f, c)
            drawCircle(bg, s * 0.27f, c)
            drawCircle(col, s * 0.12f, c)
        }
    }
}
