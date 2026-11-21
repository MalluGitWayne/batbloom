package com.malluwayne.batbloom.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.data.Tags
import com.malluwayne.batbloom.focus.FocusEngine
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Home-screen widget: live countdown or today's progress, quit/exam countdowns, and quick-start buttons. */
class BloomWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
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

        private suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BloomWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as BatbloomApp
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val since = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val s = Stats.compute(app.repo.finishedSince(since), today, zone)
            val goal = app.prefs.dailyGoal.value

            val v = RemoteViews(context.packageName, R.layout.widget_bloom)
            val st = FocusEngine.state.value
            val (endAt, title) = when (st) {
                is FocusEngine.State.Running ->
                    st.endAt to "Growing ${PlantCatalog.byId(st.plantId).name} · ${listOfNotNull(st.tag, st.subject).joinToString(" · ")}"
                is FocusEngine.State.Break -> st.endAt to "Break · round ${st.nextRound} next"
                else -> null to null
            }
            if (endAt != null) {
                v.setViewVisibility(R.id.w_timer, View.VISIBLE)
                v.setViewVisibility(R.id.w_main, View.GONE)
                val base = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis())
                v.setChronometer(R.id.w_timer, base, null, true)
                v.setChronometerCountDown(R.id.w_timer, true)
                v.setTextViewText(R.id.w_title, title)
            } else {
                v.setViewVisibility(R.id.w_timer, View.GONE)
                v.setViewVisibility(R.id.w_main, View.VISIBLE)
                v.setChronometer(R.id.w_timer, SystemClock.elapsedRealtime(), null, false)
                v.setTextViewText(R.id.w_title, "Batbloom · today")
                v.setTextViewText(R.id.w_main, "${Rules.formatMinutes(s.todayMinutes)} focused")
            }
            val lines = buildList {
                if (goal > 0) add("${Tags.FMGE} ${Rules.formatMinutes(s.fmgeTodayMinutes)} / ${Rules.formatMinutes(goal)}")
                app.prefs.examDate.value?.let { add(Stats.examLine(it, today)) }
                app.prefs.quitDate.value?.let { add(Stats.quitLine(it, today)) }
            }
            v.setTextViewText(R.id.w_sub, lines.joinToString("  ·  "))
            v.setProgressBar(R.id.w_progress, 100, if (goal > 0) (s.fmgeTodayMinutes * 100 / goal).coerceAtMost(100) else 0, false)
            v.setOnClickPendingIntent(R.id.w_root, launch(context, MainActivity.ACTION_OPEN, 10))
            v.setOnClickPendingIntent(R.id.w_craving, launch(context, MainActivity.ACTION_CRAVING, 11))
            v.setOnClickPendingIntent(R.id.w_focus, launch(context, MainActivity.ACTION_FOCUS, 12))
            manager.updateAppWidget(ids, v)
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
            val v = RemoteViews(context.packageName, R.layout.widget_craving)
            val running = FocusEngine.isActive
            v.setTextViewText(R.id.wc_label, if (running) "Growing…" else "Craving\n10 min")
            v.setOnClickPendingIntent(R.id.wc_root, BloomWidget.launch(context, MainActivity.ACTION_CRAVING, 13))
            manager.updateAppWidget(ids, v)
        }
    }
}
