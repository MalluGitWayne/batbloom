package com.malluwayne.batbloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.meds.MedForm

/** The Meds tab icon: a capsule. */
val PillIcon: ImageVector = ImageVector.Builder("Pill", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        // capsule tilted 45°, drawn as two rounded halves
        moveTo(4.6f, 13.2f); lineTo(13.2f, 4.6f)
        arcTo(4.6f, 4.6f, 0f, false, true, 19.4f, 10.8f)
        lineTo(10.8f, 19.4f)
        arcTo(4.6f, 4.6f, 0f, false, true, 4.6f, 13.2f)
        close()
    }
}.build()

/** Shapes a tablet can have. */
val TabletShapes = listOf("Round", "Oval", "Oblong")

/** Colours to pick for a pill (Health-app style: bright, plain colours). */
val PillColors = listOf(
    0xFFFFFFFF, 0xFFE8E4DA, 0xFFFFD54F, 0xFFFFA25C, 0xFFFF6B6B, 0xFFFF8FB1, 0xFFC792EA, 0xFF7FB8E3,
    0xFF4FC3F7, 0xFF7FE3D0, 0xFF8FE39A, 0xFF9C7B5B, 0xFF8C93A8, 0xFF3A3F55,
)

/** Background colours for the round tile behind the pill. */
val PillBackgrounds = listOf(
    0xFF2E3360, 0xFF203A43, 0xFF3B2B4F, 0xFF4A2C2A, 0xFF1F3B2D, 0xFF3A3A3A, 0xFFE6F4F2, 0xFFFDEFD9, 0xFFEDE7FF, 0xFFFFE4EC,
)

@Composable
fun PillArt(m: MedEntity, size: Dp, modifier: Modifier = Modifier) =
    PillArt(MedForm.of(m.form), m.shape, Color(m.color1), Color(m.color2), Color(m.background), size, modifier, m.name)

/** A medication's picture: its form drawn in its colours on a round tile. */
@Composable
fun PillArt(form: MedForm, shape: String, c1: Color, c2: Color, bg: Color, size: Dp, modifier: Modifier = Modifier, label: String? = null) {
    Canvas(modifier.size(size).semantics { contentDescription = label ?: form.label }) {
        val w = this.size.minDimension
        val c = center
        drawCircle(bg, w / 2, c)
        drawCircle(Color.White.copy(alpha = 0.06f), w / 2 * 0.96f, c, style = Stroke(w * 0.02f))
        drawForm(form, shape, c1, c2, c, w * 0.30f)
    }
}

private fun Color.shade(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
private fun outlineFor(c: Color) = c.shade(0.35f).copy(alpha = 0.55f)

private fun DrawScope.drawForm(form: MedForm, shape: String, c1: Color, c2: Color, c: Offset, s: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    val gloss = Color.White.copy(alpha = 0.35f)
    when (form) {
        MedForm.Tablet -> when (shape) {
            "Oval" -> rotate(-30f, c) {
                drawOval(Brush.verticalGradient(listOf(c1, c1.shade(0.12f)), c.y - s * 0.6f, c.y + s * 0.6f), p(-1f, -0.62f), Size(s * 2, s * 1.24f))
                drawOval(outlineFor(c1), p(-1f, -0.62f), Size(s * 2, s * 1.24f), style = Stroke(s * 0.06f))
                drawLine(outlineFor(c1), p(-0.6f, 0f), p(0.6f, 0f), s * 0.07f, StrokeCap.Round)
            }
            "Oblong" -> rotate(-30f, c) {
                drawRoundRect(Brush.verticalGradient(listOf(c1, c1.shade(0.12f)), c.y - s * 0.5f, c.y + s * 0.5f), p(-1.05f, -0.5f), Size(s * 2.1f, s * 1f), CornerRadius(s * 0.42f))
                drawRoundRect(outlineFor(c1), p(-1.05f, -0.5f), Size(s * 2.1f, s * 1f), CornerRadius(s * 0.42f), style = Stroke(s * 0.06f))
                drawLine(outlineFor(c1), p(0f, -0.32f), p(0f, 0.32f), s * 0.07f, StrokeCap.Round)
            }
            else -> {
                drawCircle(Brush.verticalGradient(listOf(c1, c1.shade(0.14f)), c.y - s, c.y + s), s * 0.95f, c)
                drawCircle(outlineFor(c1), s * 0.95f, c, style = Stroke(s * 0.06f))
                drawLine(outlineFor(c1), p(-0.55f, 0f), p(0.55f, 0f), s * 0.07f, StrokeCap.Round)
            }
        }
        MedForm.Capsule -> rotate(-40f, c) {
            val tl = p(-1.15f, -0.45f)
            val sz = Size(s * 2.3f, s * 0.9f)
            val r = CornerRadius(s * 0.45f)
            clipRect(right = c.x) { drawRoundRect(c1, tl, sz, r) }
            clipRect(left = c.x) { drawRoundRect(c2, tl, sz, r) }
            drawRoundRect(Color.Black.copy(alpha = 0.18f), tl, sz, r, style = Stroke(s * 0.05f))
            drawLine(Color.Black.copy(alpha = 0.2f), p(0f, -0.45f), p(0f, 0.45f), s * 0.05f)
            drawRoundRect(gloss, p(-0.95f, -0.32f), Size(s * 1.9f, s * 0.16f), CornerRadius(s * 0.08f))
        }
        MedForm.Liquid -> {
            drawRoundRect(c2, p(-0.32f, -1.05f), Size(s * 0.64f, s * 0.3f), CornerRadius(s * 0.06f))
            drawRect(c1.shade(0.2f), p(-0.22f, -0.78f), Size(s * 0.44f, s * 0.18f))
            drawRoundRect(c1, p(-0.62f, -0.62f), Size(s * 1.24f, s * 1.62f), CornerRadius(s * 0.22f))
            drawRoundRect(Color.White.copy(alpha = 0.85f), p(-0.45f, -0.15f), Size(s * 0.9f, s * 0.6f), CornerRadius(s * 0.06f))
            drawRoundRect(gloss, p(-0.5f, -0.5f), Size(s * 0.12f, s * 1.2f), CornerRadius(s * 0.06f))
        }
        MedForm.Drops -> {
            drawRoundRect(c2, p(-0.18f, -1.05f), Size(s * 0.36f, s * 0.45f), CornerRadius(s * 0.15f))
            drawRect(c2.shade(0.2f), p(-0.28f, -0.65f), Size(s * 0.56f, s * 0.15f))
            drawRoundRect(c1, p(-0.5f, -0.5f), Size(s * 1f, s * 1.45f), CornerRadius(s * 0.25f))
            drawPath(Path().apply {
                moveTo(p(0.62f, 0.25f).x, p(0.62f, 0.25f).y)
                cubicTo(p(0.95f, 0.65f).x, p(0.95f, 0.65f).y, p(0.62f, 0.95f).x, p(0.62f, 0.95f).y, p(0.62f, 0.95f).x, p(0.62f, 0.95f).y)
                cubicTo(p(0.3f, 0.95f).x, p(0.3f, 0.95f).y, p(0.3f, 0.65f).x, p(0.3f, 0.65f).y, p(0.62f, 0.25f).x, p(0.62f, 0.25f).y)
            }, c2)
        }
        MedForm.Spray -> {
            drawRoundRect(c2, p(-0.15f, -1.05f), Size(s * 0.5f, s * 0.22f), CornerRadius(s * 0.05f))
            drawRect(c2.shade(0.2f), p(-0.1f, -0.85f), Size(s * 0.2f, s * 0.25f))
            drawRoundRect(c1, p(-0.45f, -0.6f), Size(s * 0.9f, s * 1.6f), CornerRadius(s * 0.2f))
            repeat(3) { i -> drawCircle(c2.copy(alpha = 0.7f), s * 0.06f, p(0.55f + i * 0.17f, -0.95f + (i - 1) * 0.12f)) }
        }
        MedForm.Inhaler -> rotate(-15f, c) {
            drawRoundRect(c1, p(-0.4f, -1f), Size(s * 0.8f, s * 1.5f), CornerRadius(s * 0.2f))
            drawRoundRect(c2, p(-0.4f, 0.35f), Size(s * 1.3f, s * 0.55f), CornerRadius(s * 0.15f))
            drawRoundRect(Color.Black.copy(alpha = 0.25f), p(0.6f, 0.45f), Size(s * 0.25f, s * 0.35f), CornerRadius(s * 0.05f))
        }
        MedForm.Injection -> rotate(-45f, c) {
            drawRoundRect(c1, p(-0.35f, -0.7f), Size(s * 0.7f, s * 1.3f), CornerRadius(s * 0.1f))
            drawRect(c2, p(-0.35f, -0.1f), Size(s * 0.7f, s * 0.7f))
            drawLine(c1.shade(0.3f), p(0f, -0.7f), p(0f, -1.05f), s * 0.18f)
            drawLine(c1.shade(0.3f), p(-0.35f, -1.05f), p(0.35f, -1.05f), s * 0.14f, StrokeCap.Round)
            drawLine(Color(0xFFB0B5C3), p(0f, 0.6f), p(0f, 1.15f), s * 0.07f, StrokeCap.Round)
        }
        MedForm.Cream -> rotate(-30f, c) {
            drawPath(Path().apply {
                moveTo(p(-0.55f, -0.85f).x, p(-0.55f, -0.85f).y); lineTo(p(0.55f, -0.85f).x, p(0.55f, -0.85f).y)
                lineTo(p(0.32f, 0.7f).x, p(0.32f, 0.7f).y); lineTo(p(-0.32f, 0.7f).x, p(-0.32f, 0.7f).y); close()
            }, c1)
            drawRoundRect(c2, p(-0.22f, 0.68f), Size(s * 0.44f, s * 0.38f), CornerRadius(s * 0.06f))
            drawLine(c1.shade(0.25f), p(-0.55f, -0.85f), p(0.55f, -0.85f), s * 0.12f)
        }
        MedForm.Patch -> {
            drawRoundRect(c1, p(-0.85f, -0.85f), Size(s * 1.7f, s * 1.7f), CornerRadius(s * 0.35f))
            drawRoundRect(c2, p(-0.42f, -0.42f), Size(s * 0.84f, s * 0.84f), CornerRadius(s * 0.18f))
            listOf(-0.65f to -0.65f, 0.65f to -0.65f, -0.65f to 0.65f, 0.65f to 0.65f).forEach { (x, y) -> drawCircle(c1.shade(0.2f), s * 0.05f, p(x, y)) }
        }
        MedForm.Other -> {
            drawCircle(c1, s * 0.95f, c)
            drawLine(c2, p(-0.45f, 0f), p(0.45f, 0f), s * 0.22f, StrokeCap.Round)
            drawLine(c2, p(0f, -0.45f), p(0f, 0.45f), s * 0.22f, StrokeCap.Round)
        }
    }
}
