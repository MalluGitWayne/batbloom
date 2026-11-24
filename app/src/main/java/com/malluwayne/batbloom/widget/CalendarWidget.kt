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
    /** Days to flag with a red ring, current month. */
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
        fun lv(m: Int) = if (goal > 0) (0.6f + 0.4f * (m.toFloat() / goal).coerceIn(0f, 1f)) else 1f
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

    /** Smoke-free consistency: every day since the quit day is filled. */
    fun smokeFree(
        quit: LocalDate?, today: LocalDate, zone: ZoneId,
        perDay: Int, packSize: Int, packPrice: Double,
        quitStart: java.time.ZonedDateTime? = quit?.atStartOfDay(zone),
    ): CalendarData {
        val ym = YearMonth.from(today)
        val name = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        if (quit == null) return CalendarData(ym, "Smoke-free", emptyMap(), fill = 0x7FE3D0, figures = listOf("\u2013" to "set a quit day"))
        val level = (1..ym.lengthOfMonth()).mapNotNull { day ->
            val date = ym.atDay(day)
            if (date.isBefore(quit) || date.isAfter(today)) null else day to 1f
        }.toMap()
        val started = !today.isBefore(quit)
        val daysFree = if (started) java.time.temporal.ChronoUnit.DAYS.between(quit, today).toInt() + 1 else 0
        // Clean days in a row, counting back from today.
        var d = today
        var clean = 0
        while (started && !d.isBefore(quit)) { clean++; d = d.minusDays(1) }
        val p = com.malluwayne.batbloom.quit.QuitMath.progress(
            (quitStart ?: quit.atStartOfDay(zone)), java.time.ZonedDateTime.now(zone), perDay, packSize, packPrice,
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
            generateSequence(quit) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.associateWith { 1f }
        return CalendarData(
            ym, "Smoke-free", level,
            special = if (YearMonth.from(quit) == ym) quit.dayOfMonth else null,
            levelByDate = byDate, specialDate = quit,
            fill = 0x7FE3D0, figures = figures,
        )
    }

    /**
     * The month grid, laid out like a workout-history widget: month name, a compact grid whose text grows with the
     * widget, and the figures beside it. The whole block is centred, so a taller widget just gets more air around it.
     */
    fun draw(context: Context, wDp: Int, hDp: Int, m: CalendarData, today: LocalDate, look: WidgetLook = WidgetLook.Dark): Bitmap {
        val density = context.resources.displayMetrics.density
        val s = min(density, sqrt(700_000f / (wDp * hDp).coerceAtLeast(1)))
        val w = (wDp * s).toInt().coerceAtLeast(1)
        val h = (hDp * s).toInt().coerceAtLeast(1)
        val bmp = androidx.core.graphics.createBitmap(w, h)
        val c = Canvas(bmp)
        fun dp(v: Float) = v * s
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val r = dp(24f)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) })
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), look.bgTop, look.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        val fill = if (m.fill == 0x7FE3D0) look.smokeFill else look.studyFill
        val pad = dp(16f)
        val wide = wDp >= 230
        val ym = m.month
        val offset = ym.atDay(1).dayOfWeek.value % 7 // Sunday first
        val weeks = (offset + ym.lengthOfMonth() + 6) / 7
        val gridW = if (wide) (w - 2 * pad) * 0.60f else w - 2 * pad
        val footerH = if (wide) 0f else dp(40f)
        val titleH = dp(30f)
        val headH = dp(18f)
        // Cells are a little wider than tall (like a printed calendar); never taller than the space allows.
        val cellW = gridW / 7f
        val cellH = min(cellW * 0.95f, (h - 2 * pad - titleH - headH - footerH) / weeks).coerceAtLeast(dp(10f))
        val blockH = titleH + headH + cellH * weeks
        val top = ((h - footerH - blockH) / 2f).coerceAtLeast(pad)
        val left = pad

        // Month name, and what this calendar tracks on the right
        paint.textAlign = Paint.Align.LEFT
        paint.color = look.text; paint.typeface = bold
        paint.textSize = min(dp(20f), cellH * 0.95f).coerceAtLeast(dp(14f))
        c.drawText(ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), left, top + dp(20f), paint)
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = bold; paint.textSize = dp(12f)
        paint.color = 0xFF000000.toInt() or fill
        c.drawText(m.title.uppercase(), w - pad, top + dp(18f), paint)

        // Weekday letters
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT
        paint.color = look.muted
        val dayText = min(dp(19f), cellH * 0.42f).coerceAtLeast(dp(10f))
        paint.textSize = dayText * 0.85f
        val order = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }
        order.forEachIndexed { i, d -> c.drawText(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), left + cellW * (i + 0.5f), top + titleH + dp(10f), paint) }

        // Days
        val gridTop = top + titleH + headH
        val radius = min(cellW, cellH) * 0.42f
        for (day in 1..ym.lengthOfMonth()) {
            val idx = offset + day - 1
            val cx = left + cellW * (idx % 7 + 0.5f)
            val cy = gridTop + cellH * (idx / 7 + 0.5f)
            val lv = m.level[day] ?: 0f
            val date = ym.atDay(day)
            paint.style = Paint.Style.FILL
            if (lv > 0f) {
                paint.color = fill or ((lv.coerceIn(0f, 1f) * 255).toInt() shl 24)
                c.drawCircle(cx, cy, radius, paint)
            }
            fun ring(color: Int) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1.6f); paint.color = color
                c.drawCircle(cx, cy, radius, paint); paint.style = Paint.Style.FILL
            }
            when {
                day in m.flagged -> ring(0xFFFF8A80.toInt())
                day == m.special -> ring(0xFFFFD27F.toInt())
                date == today && lv <= 0f -> ring(look.todayRing)
            }
            paint.textSize = dayText
            paint.color = when {
                lv > 0f -> look.onFill
                date.isAfter(today) -> look.future
                date == today -> look.text
                else -> look.soft
            }
            paint.typeface = if (lv > 0f || date == today) bold else Typeface.DEFAULT
            c.drawText("$day", cx, cy - (paint.descent() + paint.ascent()) / 2, paint)
        }

        // Figures
        paint.textAlign = Paint.Align.LEFT
        if (wide) {
            val x = left + gridW + dp(18f)
            val valueSize = min(dp(26f), cellH * 1.1f).coerceAtLeast(dp(17f))
            val labelSize = (valueSize * 0.5f).coerceIn(dp(11f), dp(14f))
            val itemH = valueSize + labelSize + dp(14f)
            val figuresH = itemH * m.figures.size
            var y = top + titleH + ((blockH - titleH) - figuresH) / 2f + valueSize
            m.figures.forEach { (value, label) ->
                paint.typeface = bold; paint.textSize = valueSize; paint.color = look.text
                while (paint.measureText(value) > w - pad - x && paint.textSize > dp(12f)) paint.textSize -= dp(0.5f)
                c.drawText(value, x, y, paint)
                paint.typeface = Typeface.DEFAULT; paint.textSize = labelSize; paint.color = look.muted
                c.drawText(label, x, y + labelSize + dp(3f), paint)
                y += itemH
            }
        } else {
            val y = top + blockH + dp(22f)
            val half = (w - 2 * pad) / 2f
            m.figures.take(2).forEachIndexed { i, (value, label) ->
                val x = pad + half * i
                paint.typeface = bold; paint.color = look.text; paint.textSize = dp(15f)
                while (paint.measureText(value) > half - dp(6f) && paint.textSize > dp(9f)) paint.textSize -= dp(0.5f)
                c.drawText(value, x, y, paint)
                paint.typeface = Typeface.DEFAULT; paint.color = look.muted; paint.textSize = dp(10.5f)
                while (paint.measureText(label) > half - dp(6f) && paint.textSize > dp(8f)) paint.textSize -= dp(0.5f)
                c.drawText(label, x, y + dp(14f), paint)
            }
        }
        return bmp
    }
}

private fun calendarUpdate(
    context: Context, provider: Class<*>, build: suspend (BatbloomApp, LocalDate, ZoneId) -> CalendarData, action: String, rc: Int,
): kotlinx.coroutines.Job {
    val app = context.applicationContext as BatbloomApp
    return app.appScope.launch {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return@launch
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val data = build(app, today, zone)
        for (id in ids) {
            val (wDp, hDp) = BloomWidget.sizeDp(manager, id, 250, 150)
            val v = RemoteViews(context.packageName, R.layout.widget_calendar)
            v.setImageViewBitmap(R.id.wcal_art, Calendars.draw(context, wDp, hDp, data, today, WidgetLook.current(context)))
            v.setOnClickPendingIntent(R.id.wcal_root, BloomWidget.launch(context, action, rc))
            manager.updateAppWidget(id, v)
        }
    }
}

/** Study calendar: the days you studied this month, like a workout-history widget. */
class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = keepAlive(update(context))
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = keepAlive(update(context))

    /** Holds the broadcast open until the picture is drawn, so Android doesn't stop the app half-way. */
    private fun keepAlive(job: kotlinx.coroutines.Job) {
        val pending = goAsync()
        job.invokeOnCompletion { pending.finish() }
    }

    companion object {
        fun update(context: Context) = calendarUpdate(context, CalendarWidget::class.java, { app, today, zone ->
            Calendars.study(app.repo.sessions.first(), today, zone, app.prefs.dailyGoal.value)
        }, MainActivity.ACTION_STATS, 15)
    }
}

/** Smoke-free calendar: every day since the quit day. */
class SmokeFreeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = keepAlive(update(context))
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = keepAlive(update(context))

    /** Holds the broadcast open until the picture is drawn, so Android doesn't stop the app half-way. */
    private fun keepAlive(job: kotlinx.coroutines.Job) {
        val pending = goAsync()
        job.invokeOnCompletion { pending.finish() }
    }

    companion object {
        fun update(context: Context) = calendarUpdate(context, SmokeFreeWidget::class.java, { app, today, zone ->
            val sm = app.prefs.smoking.value
            Calendars.smokeFree(app.prefs.quitDate.value, today, zone, sm.perDay, sm.packSize, sm.packPrice, app.prefs.quitStart(zone))
        }, MainActivity.ACTION_QUIT, 16)
    }
}
