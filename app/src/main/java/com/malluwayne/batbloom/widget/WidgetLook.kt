package com.malluwayne.batbloom.widget

import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.scale
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.ui.ThemeMode

/** Widget colours for the theme chosen in Settings > Appearance (System follows the phone). */
data class WidgetLook(
    val kind: Kind,
    val bgTop: Int, val bgBottom: Int,
    val text: Int, val muted: Int, val soft: Int, val future: Int,
    val onFill: Int, val todayRing: Int,
    val studyFill: Int, val smokeFill: Int,
) {
    enum class Kind { Dark, Oled, Light }
    val light get() = kind == Kind.Light

    companion object {
        /**
         * Sends a widget update. From Android 17 the system rejects widget images that use too much memory; our
         * images are well under the limit, but if a phone ever refuses one, it's resent at half resolution.
         */
        fun update(manager: android.appwidget.AppWidgetManager, id: Int, v: android.widget.RemoteViews, artId: Int, art: android.graphics.Bitmap) {
            try {
                manager.updateAppWidget(id, v)
            } catch (_: IllegalArgumentException) {
                val small = art.scale((art.width / 2).coerceAtLeast(1), (art.height / 2).coerceAtLeast(1))
                v.setImageViewBitmap(artId, small)
                runCatching { manager.updateAppWidget(id, v) }
            }
        }

        val Dark = WidgetLook(
            Kind.Dark, 0xFF18181E.toInt(), 0xFF202027.toInt(),
            0xFFFFFFFF.toInt(), 0xFF9C9CA8.toInt(), 0xFFDADAE2.toInt(), 0x66FFFFFF,
            0xFF101014.toInt(), 0xFFE6E8F2.toInt(), 0xB9A3FF, 0x7FE3D0,
        )
        val Oled = Dark.copy(kind = Kind.Oled, bgTop = 0xFF000000.toInt(), bgBottom = 0xFF000000.toInt(), muted = 0xFF8C90A6.toInt())
        val Light = WidgetLook(
            Kind.Light, 0xFFFFFFFF.toInt(), 0xFFF3F1FA.toInt(),
            0xFF1A1B2E.toInt(), 0xFF5E6276.toInt(), 0xFF2E3045.toInt(), 0x551A1B2E,
            0xFFFFFFFF.toInt(), 0xFF1A1B2E.toInt(), 0x6A4FD6, 0x0D8A79,
        )

        fun of(mode: ThemeMode, systemNight: Boolean): WidgetLook = when (mode) {
            ThemeMode.Dark -> Dark
            ThemeMode.Oled -> Oled
            ThemeMode.Light -> Light
            ThemeMode.System -> if (systemNight) Dark else Light
        }

        fun current(context: Context): WidgetLook {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return of((context.applicationContext as BatbloomApp).prefs.theme.value, night)
        }
    }
}
