package com.malluwayne.batbloom.plants

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val Soil = Color(0xFF2A2238)
private val SoilRim = Color(0xFF4A3D63)
private val Wilt = Color(0xFF6B5E52)

/** A plant drawn in code. [growth] runs 0..1 over the session; [withered] shows the failed version. */
@Composable
fun PlantArt(
    spec: PlantSpec,
    growth: Float,
    withered: Boolean,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val g by animateFloatAsState(growth, tween(900), label = "growth")
    if (animate) {
        val t = rememberInfiniteTransition(label = "plant")
        val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "sway")
        val pulse by t.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
        Canvas(modifier) { drawPlant(spec, g, withered, if (withered) 0f else sin(phase), pulse) }
    } else {
        Canvas(modifier) { drawPlant(spec, growth, withered, 0f, 1f) }
    }
}

private fun Color.wilted(w: Boolean) = if (w) lerp(this, Wilt, 0.72f) else this

private fun bezier(p0: Offset, c: Offset, p1: Offset, t: Float): Offset {
    val u = 1 - t
    return p0 * (u * u) + c * (2 * u * t) + p1 * (t * t)
}

fun DrawScope.drawPlant(spec: PlantSpec, growth: Float, withered: Boolean, sway: Float, glow: Float) {
    // Draw in a bottom-centred square so the plant keeps its shape in wide or tall boxes.
    val side = min(size.width, size.height)
    val dx = (size.width - side) / 2
    inset(dx, size.height - side, dx, 0f) { drawPlantSquare(spec, growth, withered, sway, glow) }
}

private fun DrawScope.drawPlantSquare(spec: PlantSpec, growth: Float, withered: Boolean, sway: Float, glow: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val groundY = h * 0.86f
    val g = growth.coerceIn(0f, 1f)

    // Soil mound
    drawOval(Soil, Offset(cx - w * 0.32f, groundY - h * 0.045f), Size(w * 0.64f, h * 0.11f))
    drawOval(SoilRim, Offset(cx - w * 0.32f, groundY - h * 0.045f), Size(w * 0.64f, h * 0.11f), style = Stroke(w * 0.008f))

    if (g < 0.10f) {
        val seed = Offset(cx, groundY - h * 0.03f)
        if (!withered) drawCircle(
            Brush.radialGradient(listOf(spec.glow.copy(alpha = 0.5f * glow), Color.Transparent), seed, w * 0.1f),
            w * 0.1f, seed,
        )
        val s = 0.6f + g * 4f
        drawOval(Color(0xFF8A6B4A).wilted(withered), Offset(seed.x - w * 0.025f * s, seed.y - w * 0.018f * s), Size(w * 0.05f * s, w * 0.036f * s))
        return
    }
    val t = ((g - 0.10f) / 0.90f).coerceIn(0f, 1f)
    when (spec.kind) {
        PlantKind.Flower -> drawFlower(spec, t, g >= 1f, withered, sway, glow, cx, groundY)
        PlantKind.Cactus -> drawCactus(spec, t, withered, sway, glow, cx, groundY)
        PlantKind.Mushroom -> drawMushroom(spec, t, withered, sway, glow, cx, groundY)
    }
}

private fun DrawScope.drawLeaf(at: Offset, length: Float, angle: Float, color: Color) {
    rotate(angle, at) {
        val p = Path().apply {
            moveTo(at.x, at.y)
            quadraticTo(at.x - length * 0.28f, at.y - length * 0.5f, at.x, at.y - length)
            quadraticTo(at.x + length * 0.28f, at.y - length * 0.5f, at.x, at.y)
            close()
        }
        drawPath(p, color)
        drawLine(lerp(color, Color.Black, 0.25f), at, Offset(at.x, at.y - length * 0.85f), length * 0.04f)
    }
}

private fun petalPath(shape: PetalShape, len: Float, wd: Float): Path = Path().apply {
    when (shape) {
        PetalShape.Round -> {
            moveTo(0f, 0f)
            cubicTo(-wd, -len * 0.3f, -wd * 0.8f, -len, 0f, -len)
            cubicTo(wd * 0.8f, -len, wd, -len * 0.3f, 0f, 0f)
        }
        PetalShape.Pointed -> {
            moveTo(0f, 0f)
            quadraticTo(-wd, -len * 0.45f, 0f, -len)
            quadraticTo(wd, -len * 0.45f, 0f, 0f)
        }
        PetalShape.Star -> {
            moveTo(0f, 0f)
            quadraticTo(-wd * 0.55f, -len * 0.4f, 0f, -len)
            quadraticTo(wd * 0.55f, -len * 0.4f, 0f, 0f)
        }
        PetalShape.Wing -> {
            // A wing: sharp leading edge, scalloped trailing edge.
            moveTo(0f, 0f)
            lineTo(-wd * 0.25f, -len)
            quadraticTo(0f, -len * 0.72f, wd * 0.35f, -len * 0.9f)
            quadraticTo(wd * 0.45f, -len * 0.6f, wd * 0.95f, -len * 0.68f)
            quadraticTo(wd * 0.55f, -len * 0.3f, 0f, 0f)
        }
    }
    close()
}

private fun DrawScope.drawFlower(
    spec: PlantSpec, t: Float, full: Boolean, withered: Boolean, sway: Float, glow: Float, cx: Float, groundY: Float,
) {
    val w = size.width
    val h = size.height
    val droop = if (withered) 1f else 0f
    val stemH = h * (0.12f + 0.50f * t)
    val bend = sway * w * 0.035f + droop * w * 0.16f
    val top = Offset(cx + bend, groundY - stemH * (1 - droop * 0.3f))
    val ctrl = Offset(cx - bend * 0.3f, groundY - stemH * 0.6f)
    val base = Offset(cx, groundY - h * 0.02f)
    val stemColor = spec.stem.wilted(withered)
    drawPath(
        Path().apply { moveTo(base.x, base.y); quadraticTo(ctrl.x, ctrl.y, top.x, top.y) },
        stemColor, style = Stroke(w * 0.024f, cap = StrokeCap.Round),
    )

    // Leaves appear one by one as the plant grows.
    val leafFracs = listOf(0.18f, 0.36f, 0.52f, 0.66f)
    leafFracs.forEachIndexed { i, f ->
        val appear = ((t - i * 0.15f) / 0.25f).coerceIn(0f, 1f)
        if (appear <= 0f) return@forEachIndexed
        val at = bezier(base, ctrl, top, f)
        val side = if (i % 2 == 0) -1f else 1f
        val angle = side * (55f + droop * 50f) + sway * 4f
        drawLeaf(at, w * (0.15f - i * 0.018f) * appear, angle, spec.leaf.wilted(withered))
    }

    if (t < 0.45f) {
        // Sprout tip
        drawCircle(spec.leaf.wilted(withered), w * 0.018f, top)
        return
    }
    val bud = ((t - 0.45f) / 0.3f).coerceIn(0f, 1f)
    val open = if (full) 1f else ((t - 0.72f) / 0.28f).coerceIn(0f, 1f)
    val petal = spec.petal.wilted(withered)
    val inner = spec.petalInner.wilted(withered)

    if (!withered && open > 0f) {
        val r = w * 0.30f
        drawCircle(Brush.radialGradient(listOf(spec.glow.copy(alpha = 0.45f * open * glow), Color.Transparent), top, r), r, top)
    }
    if (withered) {
        // A few limp petals hanging down, the rest fallen on the soil.
        val n = (spec.petals / 2).coerceAtLeast(2)
        val len = w * 0.10f
        for (i in 0 until n) {
            val a = 160f + (i - (n - 1) / 2f) * 22f
            rotate(a, top) { translate(top.x, top.y) { drawPath(petalPath(spec.shape, len, len * 0.4f), petal) } }
        }
        for (i in 0 until 2) {
            val p = Offset(cx + w * (0.12f + i * 0.08f) * if (i == 0) 1f else -1f, groundY - h * 0.01f)
            rotate(80f + i * 30f, p) { translate(p.x, p.y) { drawPath(petalPath(spec.shape, w * 0.07f, w * 0.03f), petal) } }
        }
        drawCircle(spec.center.wilted(true), w * 0.022f, top)
        return
    }
    if (open <= 0.02f) {
        val bw = w * 0.05f * (0.4f + bud * 0.6f)
        val bh = w * 0.09f * (0.4f + bud * 0.6f)
        rotate(sway * 4f, top) {
            drawOval(petal, Offset(top.x - bw / 2, top.y - bh), Size(bw, bh))
            drawOval(spec.leaf, Offset(top.x - bw / 2, top.y - bh * 0.35f), Size(bw, bh * 0.4f))
        }
        return
    }
    val len = w * (0.06f + 0.13f * open)
    val wd = len * when (spec.shape) { PetalShape.Wing -> 0.9f; PetalShape.Star -> 0.5f; else -> 0.55f }
    val n = spec.petals
    // Petals start bunched upward and fan out as they open.
    for (i in 0 until n) {
        val spread = 360f / n * open + 30f * (1 - open)
        val a = (i - (n - 1) / 2f) * spread + sway * 3f
        rotate(a, top) {
            translate(top.x, top.y) {
                drawPath(petalPath(spec.shape, len, wd), petal)
                drawPath(petalPath(spec.shape, len * 0.62f, wd * 0.55f), inner)
            }
        }
    }
    drawCircle(spec.center, w * (0.018f + 0.014f * open), top)
    drawCircle(Color.White.copy(alpha = 0.6f), w * 0.007f, top + Offset(-w * 0.006f, -w * 0.006f))
}

private fun DrawScope.drawCactus(
    spec: PlantSpec, t: Float, withered: Boolean, sway: Float, glow: Float, cx: Float, groundY: Float,
) {
    val w = size.width
    val h = size.height
    val body = spec.stem.wilted(withered)
    val light = spec.leaf.wilted(withered)
    val bodyW = w * (0.10f + 0.06f * t)
    val bodyH = h * (0.10f + 0.40f * t) * (if (withered) 0.8f else 1f)
    val tilt = if (withered) 14f else sway * 1.5f
    rotate(tilt, Offset(cx, groundY)) {
        val left = cx - bodyW / 2
        val topY = groundY - h * 0.02f - bodyH
        drawRoundRect(body, Offset(left, topY), Size(bodyW, bodyH), CornerRadius(bodyW / 2))
        drawRoundRect(light, Offset(left + bodyW * 0.38f, topY + bodyW * 0.2f), Size(bodyW * 0.24f, bodyH - bodyW * 0.4f), CornerRadius(bodyW))
        val arms = ((t - 0.35f) / 0.35f).coerceIn(0f, 1f)
        if (arms > 0f) {
            val aw = bodyW * 0.55f
            val ah = bodyH * 0.38f * arms
            val y = topY + bodyH * 0.35f
            val armDrop = if (withered) ah * 0.6f else 0f
            drawRoundRect(body, Offset(left - aw * 1.1f, y - ah + armDrop), Size(aw, ah), CornerRadius(aw / 2))
            drawRoundRect(body, Offset(left - aw * 1.1f, y - aw / 2 + armDrop), Size(aw * 1.4f, aw * 0.8f), CornerRadius(aw / 2))
            val y2 = topY + bodyH * 0.5f
            drawRoundRect(body, Offset(left + bodyW + aw * 0.1f, y2 - ah * 0.8f + armDrop), Size(aw, ah * 0.8f), CornerRadius(aw / 2))
            drawRoundRect(body, Offset(left + bodyW - aw * 0.4f, y2 - aw / 2 + armDrop), Size(aw * 1.4f, aw * 0.8f), CornerRadius(aw / 2))
        }
        // Spines
        val rnd = Random(7)
        repeat((6 + 14 * t).toInt()) {
            val px = left + rnd.nextFloat() * bodyW
            val py = topY + bodyW * 0.3f + rnd.nextFloat() * (bodyH - bodyW * 0.5f)
            drawCircle(Color(0xFFE8F0D8).copy(alpha = if (withered) 0.3f else 0.8f), w * 0.005f, Offset(px, py))
        }
        val bloom = ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (bloom > 0f && !withered) {
            val c = Offset(cx, topY + bodyW * 0.1f)
            val r = w * 0.22f
            drawCircle(Brush.radialGradient(listOf(spec.glow.copy(alpha = 0.45f * bloom * glow), Color.Transparent), c, r), r, c)
            val len = w * 0.075f * bloom
            for (i in 0 until spec.petals) {
                rotate(-90f + i * (180f / (spec.petals - 1)), c) {
                    translate(c.x, c.y) {
                        drawPath(petalPath(PetalShape.Pointed, len, len * 0.5f), spec.petal)
                        drawPath(petalPath(PetalShape.Pointed, len * 0.6f, len * 0.3f), spec.petalInner)
                    }
                }
            }
            drawCircle(spec.center, w * 0.016f, c)
        }
    }
}

private fun DrawScope.drawMushroom(
    spec: PlantSpec, t: Float, withered: Boolean, sway: Float, glow: Float, cx: Float, groundY: Float,
) {
    val w = size.width
    val h = size.height
    fun shroom(x: Float, scale: Float, phase: Float) {
        val stalk = spec.stem.wilted(withered)
        val cap = spec.petal.wilted(withered)
        val sh = h * 0.30f * scale
        val sw = w * 0.05f * scale
        val capW = w * 0.24f * scale
        val tilt = if (withered) 28f * (if (x < cx) -1f else 1f) else sway * 2f * phase
        val baseY = groundY - h * 0.02f
        rotate(tilt, Offset(x, baseY)) {
            drawRoundRect(stalk, Offset(x - sw / 2, baseY - sh), Size(sw, sh), CornerRadius(sw / 2))
            val capTop = baseY - sh - capW * 0.32f
            if (!withered) {
                val r = capW * 1.1f
                val c = Offset(x, baseY - sh)
                drawCircle(Brush.radialGradient(listOf(spec.glow.copy(alpha = 0.5f * glow * scale), Color.Transparent), c, r), r, c)
            }
            drawArc(cap, 180f, 180f, true, Offset(x - capW / 2, capTop), Size(capW, capW * 0.75f))
            drawArc(spec.petalInner.wilted(withered).copy(alpha = 0.7f), 200f, 60f, true, Offset(x - capW * 0.4f, capTop + capW * 0.05f), Size(capW * 0.8f, capW * 0.6f))
            val dots = listOf(Offset(-0.22f, 0.18f), Offset(0.05f, 0.08f), Offset(0.25f, 0.2f))
            dots.forEach { d ->
                drawCircle(spec.center.copy(alpha = if (withered) 0.3f else 0.9f), capW * 0.05f, Offset(x + d.x * capW, capTop + d.y * capW))
            }
        }
    }
    val main = 0.35f + 0.65f * t
    if (t > 0.55f) shroom(cx - w * 0.17f, 0.5f * ((t - 0.55f) / 0.45f).coerceIn(0.2f, 1f), -1f)
    if (t > 0.75f) shroom(cx + w * 0.16f, 0.42f * ((t - 0.75f) / 0.25f).coerceIn(0.2f, 1f), 0.7f)
    shroom(cx, min(1f, main), 1f)
}

/** Night sky with stars, a crescent moon and a couple of bats crossing. */
@Composable
fun NightSky(modifier: Modifier = Modifier, animate: Boolean = true) {
    val fly = if (animate) {
        val t = rememberInfiniteTransition(label = "sky")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "bats").value
    } else 0.3f
    val twinkle = if (animate) {
        val t = rememberInfiniteTransition(label = "twinkle")
        t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "tw").value
    } else 0f
    Canvas(modifier) {
        drawSky(fly, twinkle)
        if (animate) drawFireflies(twinkle)
    }
}

private val skyStars = Random(42).let { r -> List(46) { Triple(r.nextFloat(), r.nextFloat() * 0.75f, r.nextFloat()) } }

private val fireflies = Random(7).let { r -> List(9) { floatArrayOf(r.nextFloat(), 0.45f + r.nextFloat() * 0.4f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat()) } }

/** Small glowing fireflies drifting low over the garden. */
fun DrawScope.drawFireflies(phase: Float) {
    val w = size.width
    val h = size.height
    fireflies.forEach { (x, y, p, speed) ->
        val t = phase * speed + p
        val c = Offset((x + 0.04f * sin(t)) * w, (y + 0.03f * sin(t * 1.7f)) * h)
        val a = 0.25f + 0.55f * ((sin(t * 2.3f) + 1f) / 2f)
        val r = min(w, h) * 0.035f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4A8).copy(alpha = a * 0.6f), Color.Transparent), c, r), r, c)
        drawCircle(Color(0xFFFFF8C8).copy(alpha = a), r * 0.14f, c)
    }
}

/** The night sky: gradient, stars, crescent moon and two bats. Also used to paint the home-screen widget. */
fun DrawScope.drawSky(fly: Float = 0.3f, twinkle: Float = 0f, moonAt: Offset = Offset(0.80f, 0.16f), bats: Boolean = true) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070A18), Color(0xFF141A38), Color(0xFF221B3D))))
    skyStars.forEach { (x, y, s) ->
        val a = 0.35f + 0.5f * ((sin(twinkle + s * 9f) + 1f) / 2f)
        drawCircle(Color.White.copy(alpha = a), (1f + s * 2.2f) * (min(w, h) / 600f).coerceAtLeast(0.6f), Offset(x * w, y * h))
    }
    val moonC = Offset(w * moonAt.x, h * moonAt.y)
    val moonR = min(w, h) * 0.075f
    drawCircle(Brush.radialGradient(listOf(Color(0x33FFF3C4), Color.Transparent), moonC, moonR * 3f), moonR * 3f, moonC)
    val moon = Path().apply { addOval(Rect(moonC, moonR)) }
    val bite = Path().apply { addOval(Rect(moonC + Offset(moonR * 0.45f, -moonR * 0.2f), moonR * 0.9f)) }
    drawPath(Path.combine(PathOperation.Difference, moon, bite), Color(0xFFFFF3C4))
    if (bats) listOf(0f to 0.22f, 0.45f to 0.34f).forEachIndexed { i, (offset, y) ->
        val p = (fly + offset) % 1f
        val x = -0.1f * w + p * 1.2f * w
        val flap = sin(p * 60f + i) * 0.35f
        drawBat(Offset(x, h * y + sin(p * 12f) * h * 0.02f), min(w, h) * (0.035f - i * 0.008f), flap)
    }
}

fun DrawScope.drawBat(c: Offset, s: Float, flap: Float, color: Color = Color(0xFF05060E)) {
    val up = -s * (0.4f + flap)
    val p = Path().apply {
        moveTo(c.x, c.y - s * 0.15f)
        quadraticTo(c.x - s * 0.6f, c.y + up, c.x - s * 1.4f, c.y + up * 0.6f)
        quadraticTo(c.x - s * 1.1f, c.y + s * 0.05f, c.x - s * 0.95f, c.y + s * 0.3f)
        quadraticTo(c.x - s * 0.7f, c.y + s * 0.05f, c.x - s * 0.5f, c.y + s * 0.3f)
        quadraticTo(c.x - s * 0.3f, c.y + s * 0.1f, c.x, c.y + s * 0.35f)
        quadraticTo(c.x + s * 0.3f, c.y + s * 0.1f, c.x + s * 0.5f, c.y + s * 0.3f)
        quadraticTo(c.x + s * 0.7f, c.y + s * 0.05f, c.x + s * 0.95f, c.y + s * 0.3f)
        quadraticTo(c.x + s * 1.1f, c.y + s * 0.05f, c.x + s * 1.4f, c.y + up * 0.6f)
        quadraticTo(c.x + s * 0.6f, c.y + up, c.x, c.y - s * 0.15f)
        close()
    }
    drawPath(p, color)
    // ears
    drawPath(Path().apply {
        moveTo(c.x - s * 0.15f, c.y - s * 0.1f); lineTo(c.x - s * 0.12f, c.y - s * 0.4f); lineTo(c.x, c.y - s * 0.15f)
        lineTo(c.x + s * 0.12f, c.y - s * 0.4f); lineTo(c.x + s * 0.15f, c.y - s * 0.1f); close()
    }, color)
}
