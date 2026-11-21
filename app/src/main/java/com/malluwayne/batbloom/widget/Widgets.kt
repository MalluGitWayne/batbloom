package com.malluwayne.batbloom.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.plants.drawPlant
import com.malluwayne.batbloom.plants.drawSky
import com.malluwayne.batbloom.plants.drawFireflies
import kotlinx.coroutines.flow.first
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Home-screen widget: the night sky with a plant drawn at the widget's own size. During a session the plant grows
 * with the timer; otherwise "today's bloom" grows as you work toward your daily goal.
 */
class BloomWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = async(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = async(context)

    private fun async(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext as BatbloomApp
        app.appScope.launch {
            try { update(context) } finally { pending.finish() }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext as BatbloomApp
            app.appScope.launch { update(context) }
            CalendarWidget.update(context)
            SmokeFreeWidget.update(context)
        }

        internal fun launch(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Widget size in dp (portrait: the launcher reports min width and max height for it). */
        internal fun sizeDp(manager: AppWidgetManager, id: Int, defW: Int, defH: Int): Pair<Int, Int> {
            val o = manager.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: defW
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: defH
            return w to h
        }

        /**
         * Paints sky and plant into a bitmap. [plantBox] is where the plant goes, in dp: (left, top, right, bottom).
         * Kept under ~0.6 MP, so the widget update stays small.
         */
        internal fun paint(
            context: Context, wDp: Int, hDp: Int, radiusDp: Float,
            plantBox: FloatArray?, plantId: String?, growth: Float, withered: Boolean,
            garden: List<Pair<String, Boolean>> = emptyList(),
            miniDp: Float = 0f,
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val scale = min(density, sqrt(600_000f / (wDp * hDp).coerceAtLeast(1)))
            val wPx = (wDp * scale).toInt().coerceAtLeast(1)
            val hPx = (hDp * scale).toInt().coerceAtLeast(1)
            val bmp = ImageBitmap(wPx, hPx)
            CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(bmp), Size(wPx.toFloat(), hPx.toFloat())) {
                val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusDp * scale))) }
                clipPath(clip) {
                    val phase = (System.currentTimeMillis() / 60_000 % 7).toFloat()
                    drawSky(fly = 0.62f, twinkle = phase, moonAt = Offset(0.9f, 0.15f))
                    drawFireflies(phase)
                    if (plantBox != null && plantId != null) {
                        val (l, t, r, b) = plantBox.map { it * scale }
                        // A low hill for the garden to stand on.
                        val hillTop = b - (b - t) * 0.12f
                        drawOval(
                            Brush.verticalGradient(listOf(Color(0xFF2E2552), Color(0xFF17122E)), hillTop, size.height),
                            Offset(-size.width * 0.15f, hillTop), Size(size.width * 1.3f, (size.height - hillTop) * 2f),
                        )
                        // Recent plants in a row to the left of today's, newest nearest, sized to stay below the text.
                        val mini = (miniDp * scale).coerceAtMost((r - l) * 0.42f)
                        if (mini > 8f) garden.forEachIndexed { i, (id, wilted) ->
                            val k = i / 2 + 1
                            val x = if (i % 2 == 0) l + (r - l) * 0.12f - k * mini * 0.86f
                            else r - (r - l) * 0.12f + (k - 1) * mini * 0.86f
                            if (x < 4f * scale || x + mini > size.width - 4f * scale) return@forEachIndexed
                            inset(x, b - mini, size.width - x - mini, size.height - b) {
                                drawPlant(PlantCatalog.byId(id), 1f, wilted, 0f, 0.8f)
                            }
                        }
                        inset(l, t, size.width - r, size.height - b) {
                            drawPlant(PlantCatalog.byId(plantId), growth, withered, 0f, 1f)
                        }
                    }
                }
            }
            return bmp.asAndroidBitmap()
        }

        private suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BloomWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as BatbloomApp
            val prefs = app.prefs
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val since = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val s = Stats.compute(app.repo.finishedSince(since), today, zone)
            val goal = prefs.dailyGoal.value
            val st = FocusEngine.state.value
            val now = System.currentTimeMillis()

            val (endAt, title) = when (st) {
                is FocusEngine.State.Running ->
                    st.endAt to "Growing ${PlantCatalog.byId(st.plantId).name} · ${listOfNotNull(st.tag, st.subject).joinToString(" · ")}"
                is FocusEngine.State.Break -> st.endAt to "Break · round ${st.nextRound} next"
                else -> null to null
            }
            val (plantId, growth) = when (st) {
                is FocusEngine.State.Running -> st.plantId to
                    if (st.request.stopwatch) Rules.stopwatchGrowth(now - st.startAt) else Rules.progress(st.startAt, st.endAt, now)
                is FocusEngine.State.Break -> st.request.plantId to 1f
                // Today's bloom: a sapling to start with, in full flower when the daily goal is met.
                else -> prefs.selectedPlant.value to (0.62f + 0.38f * if (goal > 0) (s.todayMinutes.toFloat() / goal).coerceIn(0f, 1f) else 0f)
            }
            val doseDue = com.malluwayne.batbloom.quit.DoseAlarms.dueNow(context)
            val all = app.repo.sessions.first()
            val garden = all.take(6).map { it.plantId to (it.status != com.malluwayne.batbloom.data.Status.DONE) }
            val allStats = Stats.compute(all, today, zone)
            val due = com.malluwayne.batbloom.stats.Revision.due(all, today, zone)
            val lines = buildList {
                if (allStats.streakDays > 0 || s.completed > 0) {
                    add(listOfNotNull(
                        if (allStats.streakDays > 0) "Streak ${allStats.streakDays} day${if (allStats.streakDays == 1) "" else "s"}" else null,
                        "${s.completed} bloom${if (s.completed == 1) "" else "s"} today",
                    ).joinToString(" · "))
                }
                if (due.isNotEmpty()) add("Revise: " + due.take(3).joinToString(", ") { it.subject })
                if (goal > 0) add("FMGE ${Rules.formatMinutes(s.fmgeTodayMinutes)} of ${Rules.formatMinutes(goal)}")
                prefs.examDate.value?.let { add(Stats.examLine(it, today)) }
                prefs.quitDate.value?.let { q ->
                    val since = java.time.Duration.between(q.atStartOfDay(zone), java.time.ZonedDateTime.now(zone))
                    add(listOfNotNull(Stats.quitLine(q, today), com.malluwayne.batbloom.quit.Milestones.nextLine(since)).joinToString(" \u00B7 "))
                }
            }

            for (id in ids) {
                val (wDp, hDp) = sizeDp(manager, id, 250, 160)
                val v = RemoteViews(context.packageName, R.layout.widget_bloom)
                if (endAt != null) {
                    v.setViewVisibility(R.id.w_timer, View.VISIBLE)
                    v.setViewVisibility(R.id.w_main, View.GONE)
                    val up = (st as? FocusEngine.State.Running)?.request?.stopwatch == true
                    val base = if (up) SystemClock.elapsedRealtime() - (now - (st as FocusEngine.State.Running).startAt)
                    else SystemClock.elapsedRealtime() + (endAt - now)
                    v.setChronometer(R.id.w_timer, base, null, true)
                    v.setChronometerCountDown(R.id.w_timer, !up)
                    v.setTextViewText(R.id.w_title, title)
                } else {
                    v.setViewVisibility(R.id.w_timer, View.GONE)
                    v.setViewVisibility(R.id.w_main, View.VISIBLE)
                    v.setChronometer(R.id.w_timer, SystemClock.elapsedRealtime(), null, false)
                    v.setTextViewText(R.id.w_title, "Batbloom · today's bloom")
                    v.setTextViewText(R.id.w_main, "${Rules.formatMinutes(s.todayMinutes)} focused")
                }
                // Today's plant stands on the right, as tall as the space above the buttons allows; the text
                // keeps the left. Taller widgets also get the little garden of recent plants along the hill.
                val tall = hDp >= 170
                v.setTextViewText(R.id.w_sub, lines.joinToString(if (tall) "\n" else "  ·  "))
                val bottom = hDp - 54f - (if (doseDue != null) 44f else 0f)
                // Expanded (tall): today's plant stands big in the middle under the text, the garden around it.
                // Otherwise it stands on the right beside the text.
                val expanded = hDp >= 250
                val textTop = 14f + 18f + 40f + lines.size * 17f + 10f
                val side = if (expanded) (bottom - textTop).coerceIn(56f, wDp * 0.62f) else (bottom - 16f).coerceIn(56f, wDp * 0.46f)
                val cx = if (expanded) wDp / 2f else wDp * 0.72f
                val box = floatArrayOf(cx - side / 2, bottom - side, cx + side / 2, bottom)
                // The text block's height (title, big number, the lines below) decides how tall the small plants can be.
                val textBottom = 14f + 18f + 40f + (if (tall) lines.size else 1) * 17f + 6f
                val mini = if (expanded) (side * 0.42f).coerceAtMost(80f) else (bottom - textBottom).coerceIn(0f, 64f)
                // A dose that is due now gets its own button: one tap marks it taken.
                if (doseDue != null) {
                    v.setViewVisibility(R.id.w_dose, View.VISIBLE)
                    v.setTextViewText(R.id.w_dose, "\u2713  Took ${doseDue.label.lowercase()} ${com.malluwayne.batbloom.quit.Doses.formatMg(doseDue.mg)}")
                    v.setOnClickPendingIntent(R.id.w_dose, com.malluwayne.batbloom.quit.DoseAlarms.takenIntent(context, doseDue.key))
                } else v.setViewVisibility(R.id.w_dose, View.GONE)
                // While a session runs, one button that opens it; otherwise quick-start buttons.
                v.setTextViewText(
                    R.id.w_focus,
                    when {
                        endAt != null -> "Open session"
                        prefs.stopwatch.value -> "Stopwatch"
                        prefs.pomodoro.value -> "Pomodoro ${prefs.plan.value.rounds}\u00D7${prefs.plan.value.focusMinutes}"
                        else -> "Focus ${prefs.minutes.value}m"
                    },
                )
                v.setOnClickPendingIntent(
                    R.id.w_focus,
                    launch(context, if (endAt != null) MainActivity.ACTION_OPEN else MainActivity.ACTION_FOCUS, if (endAt != null) 14 else 12),
                )
                v.setImageViewBitmap(R.id.w_art, paint(context, wDp, hDp, 24f, box, plantId, growth, false, garden, miniDp = mini))
                v.setOnClickPendingIntent(R.id.w_root, launch(context, MainActivity.ACTION_OPEN, 10))
                manager.updateAppWidget(id, v)
            }
        }
    }
}
