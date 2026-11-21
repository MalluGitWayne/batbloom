package com.malluwayne.batbloom.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.data.Status
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt

/** Month calendar of bloom days (like a workout-history widget), with the month's totals beside it. */
class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = async(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = async(context)

    private fun async(context: Context) {
        val pending = goAsync()
        (context.applicationContext as BatbloomApp).appScope.launch {
            try { update(context) } finally { pending.finish() }
        }
    }

    /** What one month looks like: minutes per day and the totals. Pure, so it is unit tested. */
    data class Month(
        val month: YearMonth,
        val minutesByDay: Map<Int, Int>,
        val bloomDays: Int,
        val totalMinutes: Int,
        val streak: Int,
    )

    companion object {
        fun month(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId): Month {
            val ym = YearMonth.from(today)
            val done = sessions.filter { it.status == Status.DONE }
            val inMonth = done.filter { YearMonth.from(Stats.dayOf(it.startedAt, zone)) == ym }
            val byDay = inMonth.groupBy { Stats.dayOf(it.startedAt, zone).dayOfMonth }.mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
            return Month(ym, byDay, byDay.size, inMonth.sumOf { it.plannedMinutes }, Stats.compute(sessions, today, zone).streakDays)
        }

        private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

        suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as BatbloomApp
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val m = month(app.repo.sessions.first(), today, zone)
            val goal = app.prefs.dailyGoal.value
            for (id in ids) {
                val (wDp, hDp) = BloomWidget.sizeDp(manager, id, 250, 150)
                val v = RemoteViews(context.packageName, R.layout.widget_calendar)
                v.setImageViewBitmap(R.id.wcal_art, draw(context, wDp, hDp, m, today, goal))
                v.setOnClickPendingIntent(R.id.wcal_root, BloomWidget.launch(context, MainActivity.ACTION_STATS, 15))
                manager.updateAppWidget(id, v)
            }
        }

        private fun draw(context: Context, wDp: Int, hDp: Int, m: Month, today: LocalDate, goal: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            val s = min(density, sqrt(700_000f / (wDp * hDp).coerceAtLeast(1)))
            val w = (wDp * s).toInt().coerceAtLeast(1)
            val h = (hDp * s).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            fun dp(v: Float) = v * s
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Card
            val r = dp(24f)
            val card = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
            c.clipPath(card)
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF141A38.toInt(), 0xFF221B3D.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null

            val pad = dp(14f)
            val wide = wDp >= 230
            val gridW = if (wide) w * 0.60f else w - 2 * pad
            // Month title
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = dp(16f)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = m.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            c.drawText(title, pad, pad + dp(14f), paint)

            // Weekday letters, Sunday first
            val top = pad + dp(26f)
            val weeks = run {
                val first = m.month.atDay(1)
                val offset = first.dayOfWeek.value % 7 // Sunday = 0
                (offset + m.month.lengthOfMonth() + 6) / 7
            }
            val cellW = (gridW - pad) / 7f
            val cellH = min(cellW, (h - top - pad - dp(14f)) / weeks)
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(10.5f)
            paint.color = 0xFF9AA0BC.toInt()
            val order = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
            order.forEachIndexed { i, d ->
                c.drawText(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), pad + cellW * (i + 0.5f), top + dp(8f), paint)
            }

            // Days
            val gridTop = top + dp(14f)
            val offset = m.month.atDay(1).dayOfWeek.value % 7
            val radius = min(cellW, cellH) * 0.42f
            paint.textSize = min(dp(12f), cellH * 0.48f)
            for (day in 1..m.month.lengthOfMonth()) {
                val idx = offset + day - 1
                val cx = pad + cellW * (idx % 7 + 0.5f)
                val cy = gridTop + cellH * (idx / 7 + 0.5f)
                val minutes = m.minutesByDay[day] ?: 0
                val isToday = m.month.atDay(day) == today
                val future = m.month.atDay(day).isAfter(today)
                if (minutes > 0) {
                    val f = if (goal > 0) (minutes.toFloat() / goal).coerceIn(0f, 1f) else 1f
                    paint.style = Paint.Style.FILL
                    paint.color = 0xB9A3FF or (((0.45f + 0.55f * f) * 255).toInt() shl 24)
                    c.drawCircle(cx, cy, radius, paint)
                }
                if (isToday) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(1.6f)
                    paint.color = 0xFF7FE3D0.toInt()
                    c.drawCircle(cx, cy, radius, paint)
                    paint.style = Paint.Style.FILL
                }
                paint.color = when {
                    minutes > 0 -> 0xFF1B1036.toInt()
                    future -> 0x66FFFFFF
                    else -> 0xFFE6E8F2.toInt()
                }
                paint.typeface = if (minutes > 0 || isToday) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                c.drawText("$day", cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
            }

            // Narrow: the month's totals as a footer under the grid, if there's room.
            val gridBottom = gridTop + cellH * weeks
            if (!wide && h - gridBottom > dp(22f)) {
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = dp(12f)
                paint.color = 0xFFC9B8FF.toInt()
                c.drawText("${plural(m.bloomDays, "bloom day")} · ${Rules.formatMinutes(m.totalMinutes)}", pad, gridBottom + dp(18f), paint)
                if (h - gridBottom > dp(40f)) {
                    paint.typeface = Typeface.DEFAULT
                    paint.color = 0xFF9AA0BC.toInt()
                    c.drawText("Streak ${plural(m.streak, "day")}", pad, gridBottom + dp(34f), paint)
                }
            }
            // Totals on the right
            if (wide) {
                paint.textAlign = Paint.Align.LEFT
                val x = gridW + dp(8f)
                val rows = listOf(
                    "${m.bloomDays}" to (if (m.bloomDays == 1) "Bloom day" else "Bloom days"),
                    Rules.formatMinutes(m.totalMinutes) to "Focused",
                    "${m.streak}" to "Day streak",
                )
                val block = (h - 2 * pad) / rows.size
                rows.forEachIndexed { i, (value, label) ->
                    val y = pad + block * i + block * 0.45f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = dp(20f)
                    paint.color = 0xFFFFFFFF.toInt()
                    c.drawText(value, x, y, paint)
                    paint.typeface = Typeface.DEFAULT
                    paint.textSize = dp(11.5f)
                    paint.color = 0xFF9AA0BC.toInt()
                    c.drawText(label, x, y + dp(15f), paint)
                }
                // Little crescent, top right
                val mc = floatArrayOf(w - pad - dp(8f), pad + dp(8f))
                paint.color = 0xFFFFF3C4.toInt()
                val moon = Path().apply { addCircle(mc[0], mc[1], dp(8f), Path.Direction.CW) }
                val bite = Path().apply { addCircle(mc[0] + dp(3.6f), mc[1] - dp(1.6f), dp(7f), Path.Direction.CW) }
                moon.op(bite, Path.Op.DIFFERENCE)
                c.drawPath(moon, paint)
            }
            return bmp
        }
    }
}
