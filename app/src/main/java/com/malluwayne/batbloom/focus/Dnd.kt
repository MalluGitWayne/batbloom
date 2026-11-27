package com.malluwayne.batbloom.focus

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit

/**
 * Do Not Disturb during focus sessions (Settings > Focus). Turns on Priority mode when a session starts and puts the
 * phone back the way it was when it ends. Reminders and alarms stay allowed, so medication reminders still come.
 * What was there before is saved, so it is restored even if the app was closed mid-session.
 */
object Dnd {
    private const val SP = "dnd"

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    /** Android lets the app change Do Not Disturb only after you allow it once in system settings. */
    fun hasAccess(c: Context): Boolean = nm(c).isNotificationPolicyAccessGranted

    fun enabled(c: Context): Boolean = c.getSharedPreferences(SP, Context.MODE_PRIVATE).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) = c.getSharedPreferences(SP, Context.MODE_PRIVATE).edit { putBoolean("on", on) }

    /** Whether Batbloom has Do Not Disturb on right now (for this session). */
    fun active(c: Context): Boolean = c.getSharedPreferences(SP, Context.MODE_PRIVATE).getBoolean("active", false)

    /** At the start of a session. Does nothing if switched off, not allowed, or DND is already on by the user. */
    fun start(c: Context) {
        if (!enabled(c) || !hasAccess(c)) return
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        if (sp.getBoolean("active", false)) return
        val nm = nm(c)
        val filter = nm.currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) return // already silenced: leave the user's setting alone
        val p = nm.notificationPolicy
        runCatching {
            sp.edit(commit = true) {
                putBoolean("active", true)
                putInt("filter", filter)
                putInt("cats", p.priorityCategories); putInt("calls", p.priorityCallSenders); putInt("msgs", p.priorityMessageSenders)
            }
            // Keep the user's own exceptions, and always let reminders and alarms through.
            // (Before Android 9, alarms always get through Priority mode, so the flag isn't needed there.)
            val alarms = if (android.os.Build.VERSION.SDK_INT >= 28) NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS else 0
            val cats = p.priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS or alarms
            nm.notificationPolicy = NotificationManager.Policy(cats, p.priorityCallSenders, p.priorityMessageSenders)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }.onFailure {
            android.util.Log.w("Batbloom", "Couldn't turn on Do Not Disturb", it)
            sp.edit { putBoolean("active", false) }
        }
    }

    /** At the end of a session (or on app start after a crash). Only undoes what Batbloom itself turned on. */
    fun restore(c: Context) {
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        if (!sp.getBoolean("active", false)) return
        if (hasAccess(c)) runCatching {
            val nm = nm(c)
            nm.notificationPolicy = NotificationManager.Policy(sp.getInt("cats", 0), sp.getInt("calls", 0), sp.getInt("msgs", 0))
            // If the user changed DND themselves during the session, keep their choice.
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                nm.setInterruptionFilter(sp.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL))
            }
        }
        sp.edit { putBoolean("active", false) }
    }
}
