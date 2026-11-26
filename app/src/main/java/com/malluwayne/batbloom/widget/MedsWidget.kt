package com.malluwayne.batbloom.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.meds.MedAlarms
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** A small widget for medications alone: the next dose, today's count, and one tap to log it. */
class MedsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = keepAlive(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = keepAlive(context)

    private fun keepAlive(context: Context) {
        val pending = goAsync()
        update(context).invokeOnCompletion { pending.finish() }
    }

    companion object {
        private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

        fun update(context: Context) = (context.applicationContext as BatbloomApp).appScope.launch {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MedsWidget::class.java))
            if (ids.isEmpty()) return@launch
            val dose = MedAlarms.widgetDose(context)
            val (taken, due) = MedAlarms.todayCounts(context)
            val look = WidgetLook.current(context)
            val now = LocalDateTime.now()
            for (id in ids) {
                val v = RemoteViews(context.packageName, R.layout.widget_meds)
                v.setInt(R.id.m_root, "setBackgroundResource", if (look.light) R.drawable.widget_bg_light else R.drawable.widget_bg)
                v.setTextColor(R.id.m_name, look.text)
                v.setTextColor(R.id.m_title, look.muted)
                v.setTextColor(R.id.m_count, look.muted)
                v.setOnClickPendingIntent(R.id.m_root, BloomWidget.launch(context, MainActivity.ACTION_MEDS, 17))
                if (dose != null) {
                    val day = if (dose.at.toLocalDate() == LocalDate.now()) "" else "Yesterday "
                    val late = now.isAfter(dose.at.plusMinutes(60))
                    v.setTextViewText(R.id.m_title, (if (late) "Late · " else "Next · ") + day + timeFmt.format(dose.at))
                    v.setTextViewText(R.id.m_name, dose.name)
                    v.setViewVisibility(R.id.m_took, View.VISIBLE)
                    v.setOnClickPendingIntent(R.id.m_took, dose.intent)
                } else {
                    v.setTextViewText(R.id.m_title, "Medications")
                    v.setTextViewText(R.id.m_name, if (due > 0) "All taken ✓" else "Nothing due today")
                    v.setViewVisibility(R.id.m_took, View.GONE)
                }
                v.setTextViewText(R.id.m_count, if (due > 0) "$taken of $due taken today" else "")
                manager.updateAppWidget(id, v)
            }
        }
    }
}
