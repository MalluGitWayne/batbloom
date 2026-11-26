package com.malluwayne.batbloom

import androidx.test.platform.app.InstrumentationRegistry
import com.malluwayne.batbloom.widget.BloomWidget
import com.malluwayne.batbloom.widget.Calendars
import com.malluwayne.batbloom.widget.WidgetLook
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Draws the widget pictures in every look and saves them, so they can be checked by eye. */
class WidgetRenderTest {
    @Test fun renderAll() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(ctx.getExternalFilesDir(null), "widgets").apply { mkdirs() }
        val today = LocalDate.now()
        val study = Calendars.study(emptyList(), today, ZoneId.systemDefault(), 240).copy(
            level = mapOf(3 to 1f, 4 to 0.7f, 5 to 1f, 10 to 0.6f, 11 to 1f, today.dayOfMonth to 1f),
        )
        val smoke = Calendars.smokeFree(today.minusDays(5), today, ZoneId.systemDefault(), 10, 20, 340.0)
        for ((name, look) in listOf("dark" to WidgetLook.Dark, "oled" to WidgetLook.Oled, "light" to WidgetLook.Light)) {
            Calendars.draw(ctx, 330, 170, study, today, look).compress(android.graphics.Bitmap.CompressFormat.PNG, 90, File(out, "study_$name.png").outputStream())
            Calendars.draw(ctx, 330, 170, smoke, today, look).compress(android.graphics.Bitmap.CompressFormat.PNG, 90, File(out, "smoke_$name.png").outputStream())
            BloomWidget.paint(ctx, 330, 190, 24f, floatArrayOf(150f, 50f, 260f, 136f), "moonflower", 0.8f, false,
                listOf("bat_orchid" to false, "moonflower" to true), 60f, look,
            ).compress(android.graphics.Bitmap.CompressFormat.PNG, 90, File(out, "bloom_$name.png").outputStream())
        }
    }

    /**
     * The medications widget, inflated the way a home screen does it (RemoteViews.apply refuses views a widget
     * can't show), in the dark and light looks, at its smallest size.
     */
    @Test fun medsWidgetInflates() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(ctx.getExternalFilesDir(null), "widgets").apply { mkdirs() }
        val dm = ctx.resources.displayMetrics
        for ((name, look) in listOf("dark" to WidgetLook.Dark, "light" to WidgetLook.Light)) {
            val rv = android.widget.RemoteViews(ctx.packageName, R.layout.widget_meds)
            rv.setInt(R.id.m_root, "setBackgroundResource", if (look.light) R.drawable.widget_bg_light else R.drawable.widget_bg)
            rv.setTextViewText(R.id.m_title, "Late · 8:00 PM")
            rv.setTextViewText(R.id.m_name, "Metformin +1")
            rv.setTextViewText(R.id.m_count, "2 of 5 taken today")
            rv.setTextColor(R.id.m_name, look.text); rv.setTextColor(R.id.m_title, look.muted); rv.setTextColor(R.id.m_count, look.muted)
            val parent = android.widget.FrameLayout(ctx)
            val root = rv.apply(ctx, parent)
            val w = (120 * dm.density).toInt()
            val h = (120 * dm.density).toInt()
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
            val took = root.findViewById<android.widget.TextView>(R.id.m_took)
            assert(took.bottom <= h) { "the button doesn't fit at 120dp" }
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bmp))
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, File(out, "meds_$name.png").outputStream())
        }
    }

    /** The dose button with a long label at a narrow widget width: it must stay on one line, ending in "…". */
    @Test fun doseButtonLongLabel() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(ctx.getExternalFilesDir(null), "widgets").apply { mkdirs() }
        val dm = ctx.resources.displayMetrics
        for ((dp, label) in listOf(250 to "✓  Took Vitamin D3 Cholecalciferol +2", 180 to "✓  Took Metformin +1")) {
            val root = android.view.LayoutInflater.from(ctx).inflate(R.layout.widget_bloom, null)
            val dose = root.findViewById<android.widget.TextView>(R.id.w_dose)
            dose.text = label
            dose.visibility = android.view.View.VISIBLE
            val w = (dp * dm.density).toInt()
            val h = (190 * dm.density).toInt()
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            root.layout(0, 0, w, h)
            assert(dose.lineCount == 1) { "label wrapped to ${dose.lineCount} lines at ${dp}dp" }
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bmp))
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, File(out, "dose_${dp}dp.png").outputStream())
        }
    }
}
