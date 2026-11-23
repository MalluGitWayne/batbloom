package com.malluwayne.batbloom.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.malluwayne.batbloom.quit.Badge
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Renders an earned achievement as a 1080×1350 card and opens the share sheet (WhatsApp, etc.). */
object AchievementShare {
    private const val W = 1080
    private const val H = 1350

    fun render(b: Badge, earnedAt: Long): ImageBitmap {
        val img = ImageBitmap(W, H)
        val tc = tierColor(b.tier)
        val accent = Color(b.accent)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(W.toFloat(), H.toFloat())) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF241E58), Color(0xFF12132E), Color(0xFF07081A))))
            val c = Offset(W / 2f, 560f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent), c, 520f), 520f, c)
            // rays
            repeat(12) { i ->
                val a0 = Math.toRadians(i * 30.0).toFloat()
                val a1 = Math.toRadians(i * 30.0 + 11).toFloat()
                val r = 640f
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y); lineTo(c.x + kotlin.math.cos(a0) * r, c.y + kotlin.math.sin(a0) * r)
                    lineTo(c.x + kotlin.math.cos(a1) * r, c.y + kotlin.math.sin(a1) * r); close()
                }, Brush.radialGradient(listOf(tc.copy(alpha = 0.22f), Color.Transparent), c, r))
            }
            // a few stars
            listOf(120f to 140f, 930f to 210f, 180f to 860f, 960f to 760f, 520f to 90f, 70f to 520f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.6f), 4f, Offset(x, y))
            }
            drawEmblem(b.tier, b.glyph, accent, true, null, 0.28f, false, c, 700f)

            val nc = drawContext.canvas.nativeCanvas
            fun paint(size: Float, color: Color, bold: Boolean, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color.toArgb(); textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                letterSpacing = spacing
            }
            nc.drawText("ACHIEVEMENT UNLOCKED", W / 2f, 1010f, paint(34f, tc, true, 0.2f))
            nc.drawText(b.title, W / 2f, 1095f, paint(74f, Color.White, true))
            val date = DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochMilli(earnedAt).atZone(ZoneId.systemDefault()))
            nc.drawText("${b.tier.label} · ${b.tier.points} pts · $date", W / 2f, 1160f, paint(36f, Color(0xFFCBD2F0), false))
            nc.drawText(b.text, W / 2f, 1220f, paint(32f, Color(0xFF9AA3C7), false).also { p ->
                // shrink long descriptions to fit
                while (p.measureText(b.text) > W - 120 && p.textSize > 20f) p.textSize -= 1f
            })
            nc.drawText("🦇 Batbloom", W / 2f, 1310f, paint(30f, Color(0xFF7F87AD), true, 0.05f))
        }
        return img
    }

    fun share(context: Context, b: Badge, earnedAt: Long) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "achievement.png")
            file.outputStream().use { render(b, earnedAt).asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "I just unlocked \"${b.title}\" (${b.tier.label}) in Batbloom!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share achievement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            android.widget.Toast.makeText(context, "Couldn't share: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
