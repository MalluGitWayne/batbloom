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
import androidx.compose.ui.graphics.Canvas
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
            CravingWidget.update(context)
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
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val scale = min(density, sqrt(600_000f / (wDp * hDp).coerceAtLeast(1)))
            val wPx = (wDp * scale).toInt().coerceAtLeast(1)
            val hPx = (hDp * scale).toInt().coerceAtLeast(1)
            val bmp = ImageBitmap(wPx, hPx)
            CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(bmp), Size(wPx.toFloat(), hPx.toFloat())) {
                val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusDp * scale))) }
                clipPath(clip) {
                    drawSky(fly = 0.62f, twinkle = (System.currentTimeMillis() / 60_000 % 7).toFloat(), moonAt = Offset(0.86f, 0.2f))
                    if (plantBox != null && plantId != null) {
                        val (l, t, r, b) = plantBox.map { it * scale }
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
                else -> prefs.selectedPlant.value to if (goal > 0) (s.todayMinutes.toFloat() / goal).coerceIn(0f, 1f) else 0f
            }
            val lines = buildList {
                if (goal > 0) add("FMGE ${Rules.formatMinutes(s.fmgeTodayMinutes)} of ${Rules.formatMinutes(goal)}")
                prefs.examDate.value?.let { add(Stats.examLine(it, today)) }
                prefs.quitDate.value?.let { add(Stats.quitLine(it, today)) }
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
                // Tall widget: plant in the open sky between the text and the buttons. Short: on the right.
                val tall = hDp >= 200
                v.setTextViewText(R.id.w_sub, lines.joinToString(if (tall) "\n" else "  ·  "))
                val box = if (tall) {
                    val side = min(wDp * 0.62f, hDp - 150f - 60f).coerceAtLeast(60f)
                    floatArrayOf(wDp / 2f - side / 2, hDp - 60f - side, wDp / 2f + side / 2, hDp - 60f)
                } else {
                    val side = (hDp - 60f).coerceIn(50f, 110f)
                    floatArrayOf(wDp - side - 6f, hDp - 56f - side, wDp - 6f, hDp - 56f)
                }
                // While a session runs, one button that opens it; otherwise quick-start buttons.
                v.setViewVisibility(R.id.w_craving, if (endAt != null) View.GONE else View.VISIBLE)
                v.setTextViewText(R.id.w_focus, if (endAt != null) "Open session" else "Focus")
                v.setOnClickPendingIntent(
                    R.id.w_focus,
                    launch(context, if (endAt != null) MainActivity.ACTION_OPEN else MainActivity.ACTION_FOCUS, if (endAt != null) 14 else 12),
                )
                v.setImageViewBitmap(R.id.w_art, paint(context, wDp, hDp, 24f, box, plantId, growth, false))
                v.setOnClickPendingIntent(R.id.w_root, launch(context, MainActivity.ACTION_OPEN, 10))
                v.setOnClickPendingIntent(R.id.w_craving, launch(context, MainActivity.ACTION_CRAVING, 11))
                manager.updateAppWidget(id, v)
            }
        }
    }
}

/** Small 1x1 "craving" button: one tap starts a 10-minute session. */
class CravingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CravingWidget::class.java))
            if (ids.isEmpty()) return
            val running = FocusEngine.isActive
            val plant = (context.applicationContext as BatbloomApp).prefs.selectedPlant.value
            for (id in ids) {
                val (wDp, hDp) = BloomWidget.sizeDp(manager, id, 70, 70)
                val v = RemoteViews(context.packageName, R.layout.widget_craving)
                v.setTextViewText(R.id.wc_label, if (running) "Growing…" else "Craving")
                val side = min(wDp, hDp) * 0.62f
                val box = floatArrayOf(wDp / 2f - side / 2, 4f, wDp / 2f + side / 2, 4f + side)
                v.setImageViewBitmap(R.id.wc_art, BloomWidget.paint(context, wDp, hDp, 20f, box, plant, 1f, false))
                v.setOnClickPendingIntent(R.id.wc_root, BloomWidget.launch(context, MainActivity.ACTION_CRAVING, 13))
                manager.updateAppWidget(id, v)
            }
        }
    }
}
