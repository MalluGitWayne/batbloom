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
}
