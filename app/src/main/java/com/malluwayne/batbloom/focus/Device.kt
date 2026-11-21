package com.malluwayne.batbloom.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.telecom.TelecomManager

object Device {
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Apps that never fail a session: home screens, phone calls, system UI. */
    fun systemAllowed(context: Context): Set<String> {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        // Settings registers a fallback home screen for boot; it must not count as a launcher.
        val launchers = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }
        val dialer = runCatching { (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage }.getOrNull()
        return (launchers + listOfNotNull(dialer) + listOf(
            "android",
            "com.android.systemui",
            "com.android.incallui",
            "com.android.server.telecom",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
        )).toSet()
    }

    fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Tracks which app is on screen from the usage event stream (needs Usage access). */
class ForegroundAppReader(context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var current: String? = context.packageName
    private var lastQuery = System.currentTimeMillis()
    private val event = UsageEvents.Event()

    fun poll(now: Long): String? {
        // Overlap the window a little: events are sometimes written a moment late.
        val events = runCatching { usm.queryEvents(lastQuery - 5_000, now) }.getOrNull() ?: return current
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) current = event.packageName
        }
        lastQuery = now
        return current
    }
}
