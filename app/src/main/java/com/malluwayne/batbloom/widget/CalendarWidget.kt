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

/** What one calendar month shows: how full each day is, days to flag, and the numbers beside it. */
data class CalendarData(
    val month: YearMonth,
    val title: String,
    /** 0..1 per day; > 0 is drawn filled (stronger = fuller). Days of the current month (tests read these). */
    val level: Map<Int, Float>,
    /** Days to flag with a red ring (slips), current month. */
    val flagged: Set<Int> = emptySet(),
    /** A day to ring in gold (the quit day), current month. */
    val special: Int? = null,
    /** The same marks by date, for the rolling grid that can span months. */
    val levelByDate: Map<LocalDate, Float> = emptyMap(),
    val flaggedDates: Set<LocalDate> = emptySet(),
    val specialDate: LocalDate? = null,
    val fill: Int,
    /** Value and label pairs shown beside the grid (or as a footer when narrow). */
    val figures: List<Pair<String, String>>,
)

object Calendars {
    /** Study consistency: days with study time (cravings don't count), shaded by the daily goal. */
    fun study(sessions: List<SessionEntity>, today: LocalDate, zone: ZoneId, goal: Int): CalendarData {
        val ym = YearMonth.from(today)
        val study = sessions.filter { it.status == Status.DONE && it.tag != com.malluwayne.batbloom.data.Tags.CRAVING }
        val inMonth = study.filter { YearMonth.from(Stats.dayOf(it.startedAt, zone)) == ym }
        val byDay = inMonth.groupBy { Stats.dayOf(it.startedAt, zone).dayOfMonth }.mapValues { (_, l) -> l.sumOf { it.plannedMinutes } }
        // Streak of study days, counting back from today (or yesterday).
        val days = study.map { Stats.dayOf(it.startedAt, zone) }.toSet()
        var d = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (d in days) { streak++; d = d.minusDays(1) }
        fun lv(m: Int) = if (goal > 0) (0.35f + 0.65f * (m.toFloat() / goal).coerceIn(0f, 1f)) else 1f
        val allByDate = study.groupBy { Stats.dayOf(it.startedAt, zone) }.mapValues { (_, l) -> lv(l.sumOf { it.plannedMinutes }) }
        return CalendarData(
            ym, "Study",
            byDay.mapValues { (_, m) -> lv(m) },
            levelByDate = allByDate,
            fill = 0xB9A3FF,
            figures = listOf(
                "${byDay.size}" to (if (byDay.size == 1) "study day" else "study days"),
                Rules.formatMinutes(inMonth.sumOf { it.plannedMinutes }) to "this month",
                "$streak" to "day streak",
            ),
        )
    }

    /** Smoke-free consistency: every day since the quit day is filled, slip days are ringed in red. */
    fun smokeFree(
        quit: LocalDate?, slips: List<com.malluwayne.batbloom.data.SlipEntity>, today: LocalDate, zone: ZoneId,
        perDay: Int, packSize: Int, packPrice: Double,
    ): CalendarData {
        val ym = YearMonth.from(today)
        val name = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        if (quit == null) return CalendarData(ym, "Smoke-free", emptyMap(), fill = 0x7FE3D0, figures = listOf("\u2013" to "set a quit day"))
        val slipDays = slips.map { Stats.dayOf(it.at, zone) }.toSet()
        val level = (1..ym.lengthOfMonth()).mapNotNull { day ->
            val date = ym.atDay(day)
            if (date.isBefore(quit) || date.isAfter(today) || date in slipDays) null else day to 1f
        }.toMap()
        val flagged = slipDays.filter { YearMonth.from(it) == ym }.map { it.dayOfMonth }.toSet()
        val started = !today.isBefore(quit)
        val daysFree = if (started) java.time.temporal.ChronoUnit.DAYS.between(quit, today).toInt() + 1 else 0
        // Clean days in a row, counting back from today.
        var d = today
        var clean = 0
        while (started && !d.isBefore(quit) && d !in slipDays) { clean++; d = d.minusDays(1) }
        val p = com.malluwayne.batbloom.quit.QuitMath.progress(
            quit.atStartOfDay(zone), java.time.ZonedDateTime.now(zone), perDay, packSize, packPrice, slips.sumOf { it.count },
        )
        val figures = if (!started) listOf(
            "${java.time.temporal.ChronoUnit.DAYS.between(today, quit)}" to "days to quit day",
            quit.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) to "quit day",
        ) else listOf(
            "$daysFree" to (if (daysFree == 1) "day smoke-free" else "days smoke-free"),
            "$clean" to "clean in a row",
            "\u20B9" + "%,.0f".format(p.moneySaved) to "saved",
        )
        val byDate = if (!started) emptyMap() else
            generateSequence(quit) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.filter { it !in slipDays }.associateWith { 1f }
        return CalendarData(
            ym, "Smoke-free", level, flagged,
            special = if (YearMonth.from(quit) == ym) quit.dayOfMonth else null,
            levelByDate = byDate, flaggedDates = slipDays, specialDate = quit,
            fill = 0x7FE3D0, figures = figures,
        )
    }

    fun draw(context: Context, wDp: Int, hDp: Int, m: CalendarData, today: LocalDate): Bitmap {
        val density = context.resources.displayMetrics.density
        val s = min(density, sqrt(700_000f / (wDp * hDp).coerceAtLeast(1)))
        val w = (wDp * s).toInt().coerceAtLeast(1)
        val h = (hDp * s).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun dp(v: Float) = v * s
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val r = dp(24f)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) })
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF141A38.toInt(), 0xFF221B3D.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        val pad = dp(14f)
        val wide = wDp >= 230
        val gridW = if (wide) w * 0.62f else w - 2 * pad
        val cellW = (gridW - pad) / 7f
        val top = pad + dp(24f)
        val gridTop = top + dp(14f)
        val footer = if (wide) 0f else dp(38f)
        // As many weeks as fit at a comfortable cell size (4 to 12), ending with this week.
        val avail = h - gridTop - pad - footer
        val weeks = (avail / (cellW * 0.92f)).toInt().coerceIn(4, 12)
        val cellH = min(cellW, avail / weeks)
        val endOfWeek = today.plusDays((6 - today.dayOfWeek.value % 7).toLong()) // Saturday
        val first = endOfWeek.minusDays(weeks * 7L - 1)

        // Title: name and the months shown
        val monthsShown = listOf(first, endOfWeek).map { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }.distinct()
        paint.color = 0xFFFFFFFF.toInt(); paint.textSize = dp(14f); paint.typeface = bold
        c.drawText(m.title, pad, pad + dp(12f), paint)
        val tw = paint.measureText(m.title)
        paint.typeface = Typeface.DEFAULT; paint.color = 0xFF9AA0BC.toInt(); paint.textSize = dp(12f)
        c.drawText("  " + monthsShown.joinToString(" \u2013 "), pad + tw, pad + dp(12f), paint)

        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(10f)
        val order = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
        order.forEachIndexed { i, d -> c.drawText(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), pad + cellW * (i + 0.5f), top + dp(8f), paint) }

        val radius = min(cellW, cellH) * 0.42f
        for (i in 0 until weeks * 7) {
            val date = first.plusDays(i.toLong())
            val cx = pad + cellW * (i % 7 + 0.5f)
            val cy = gridTop + cellH * (i / 7 + 0.5f)
            val lv = m.levelByDate[date] ?: 0f
            paint.style = Paint.Style.FILL
            if (lv > 0f) {
                paint.color = m.fill or ((lv.coerceIn(0f, 1f) * 255).toInt() shl 24)
                c.drawCircle(cx, cy, radius, paint)
            }
            fun ring(color: Int) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1.6f); paint.color = color
                c.drawCircle(cx, cy, radius, paint); paint.style = Paint.Style.FILL
            }
            when {
                date in m.flaggedDates -> ring(0xFFFF8A80.toInt())
                date == m.specialDate -> ring(0xFFFFD27F.toInt())
                date == today -> ring(0xFFE6E8F2.toInt())
            }
            // The 1st of a month shows the month's name, so the window reads across months.
            val label = if (date.dayOfMonth == 1) date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else "${date.dayOfMonth}"
            paint.textSize = min(dp(11.5f), cellH * 0.44f) * if (label.length > 2) 0.78f else 1f
            paint.color = when {
                lv > 0f -> 0xFF101428.toInt()
                date.isAfter(today) -> 0x40FFFFFF
                date.dayOfMonth == 1 -> 0xFFC9B8FF.toInt()
                else -> 0xFFB4B9D0.toInt()
            }
            paint.typeface = if (lv > 0f || date == today || date.dayOfMonth == 1) bold else Typeface.DEFAULT
            c.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        if (wide) {
            val x = gridW + dp(10f)
            val block = (h - 2 * pad) / m.figures.size.coerceAtLeast(1)
            m.figures.forEachIndexed { i, (value, label) ->
                val y = pad + block * i + block * 0.5f
                paint.typeface = bold; paint.textSize = dp(19f); paint.color = 0xFFFFFFFF.toInt()
                c.drawText(value, x, y, paint)
                paint.typeface = Typeface.DEFAULT; paint.textSize = dp(11f); paint.color = 0xFF9AA0BC.toInt()
                c.drawText(label, x, y + dp(14f), paint)
            }
        } else {
            // Narrow: two figures side by side under the grid, each sized to fit its half.
            val y = gridTop + cellH * weeks + dp(16f)
            val half = (w - 2 * pad) / 2f
            m.figures.take(2).forEachIndexed { i, (value, label) ->
                val x = pad + half * i
                paint.typeface = bold; paint.color = 0xFFFFFFFF.toInt(); paint.textSize = dp(15f)
                while (paint.measureText(value) > half - dp(6f) && paint.textSize > dp(9f)) paint.textSize -= dp(0.5f)
                c.drawText(value, x, y, paint)
                paint.typeface = Typeface.DEFAULT; paint.color = 0xFF9AA0BC.toInt(); paint.textSize = dp(10.5f)
                while (paint.measureText(label) > half - dp(6f) && paint.textSize > dp(8f)) paint.textSize -= dp(0.5f)
                c.drawText(label, x, y + dp(14f), paint)
            }
        }
        return bmp
    }
}

private fun calendarUpdate(context: Context, provider: Class<*>, build: suspend (BatbloomApp, LocalDate, ZoneId) -> CalendarData, action: String, rc: Int) {
    val app = context.applicationContext as BatbloomApp
    app.appScope.launch {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return@launch
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val data = build(app, today, zone)
        for (id in ids) {
            val (wDp, hDp) = BloomWidget.sizeDp(manager, id, 250, 150)
            val v = RemoteViews(context.packageName, R.layout.widget_calendar)
            v.setImageViewBitmap(R.id.wcal_art, Calendars.draw(context, wDp, hDp, data, today))
            v.setOnClickPendingIntent(R.id.wcal_root, BloomWidget.launch(context, action, rc))
            manager.updateAppWidget(id, v)
        }
    }
}

/** Study calendar: the days you studied this month, like a workout-history widget. */
class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context)

    companion object {
        fun update(context: Context) = calendarUpdate(context, CalendarWidget::class.java, { app, today, zone ->
            Calendars.study(app.repo.sessions.first(), today, zone, app.prefs.dailyGoal.value)
        }, MainActivity.ACTION_STATS, 15)
    }
}

/** Smoke-free calendar: every clean day since the quit day, slips ringed in red. */
class SmokeFreeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context)

    companion object {
        fun update(context: Context) = calendarUpdate(context, SmokeFreeWidget::class.java, { app, today, zone ->
            val sm = app.prefs.smoking.value
            Calendars.smokeFree(app.prefs.quitDate.value, app.repo.slips.first(), today, zone, sm.perDay, sm.packSize, sm.packPrice)
        }, MainActivity.ACTION_QUIT, 16)
    }
}
