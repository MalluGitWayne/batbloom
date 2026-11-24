package com.malluwayne.batbloom

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.malluwayne.batbloom.focus.FocusEngine

/** Long-press shortcuts on the app icon. Only for tabs that are switched on (Settings > Tabs). */
object Shortcuts {
    fun update(context: Context) {
        val prefs = (context.applicationContext as BatbloomApp).prefs
        fun intent(action: String) = Intent(context, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val list = buildList {
            add(ShortcutInfoCompat.Builder(context, "focus").setShortLabel("Start focus").setLongLabel("Start a focus session")
                .setIcon(icon).setIntent(intent(MainActivity.ACTION_FOCUS)).setRank(0).build())
            if (prefs.quitTab.value) add(ShortcutInfoCompat.Builder(context, "breathe").setShortLabel("Craving: breathe")
                .setLongLabel("Breathe through a craving").setIcon(icon).setIntent(intent(MainActivity.ACTION_BREATHE)).setRank(1).build())
            if (prefs.medsTab.value) add(ShortcutInfoCompat.Builder(context, "dose").setShortLabel("Log dose")
                .setLongLabel("Log a medication dose").setIcon(icon).setIntent(intent(MainActivity.ACTION_MEDS)).setRank(2).build())
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}

/** Quick Settings tile: starts a focus session (or opens the one running). */
class FocusTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val running = FocusEngine.isActive
        tile.label = if (running) "Focusing" else "Focus"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (running) "Open session" else "Start a session"
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val action = if (FocusEngine.isActive) MainActivity.ACTION_OPEN_FOCUS else MainActivity.ACTION_FOCUS
        val intent = Intent(this, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 40, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
