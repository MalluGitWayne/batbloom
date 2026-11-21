package com.malluwayne.batbloom.guard

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.focus.Device
import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.stats.Revision
import com.malluwayne.batbloom.stats.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time Guard checks (every 15 minutes) and the daily FMGE study reminder. */
object Reminders {
    private const val CH_GUARD = "guard"
    private const val CH_STUDY = "study"
    const val ACTION_GUARD = "batbloom.GUARD_CHECK"
    const val ACTION_FMGE = "batbloom.FMGE_REMINDER"
    const val ACTION_MILESTONE = "batbloom.MILESTONE"
    const val ACTION_BACKUP = "batbloom.AUTO_BACKUP"
    private const val CH_MILESTONE = "milestones"
    private const val NOTIF_STUDY = 40

    private fun app(c: Context) = c.applicationContext as BatbloomApp

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_GUARD, "Time Guard", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When an app passes its daily limit"
        })
        nm.createNotificationChannel(NotificationChannel(CH_MILESTONE, "Health milestones", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When you reach a smoke-free health milestone"
        })
        nm.createNotificationChannel(NotificationChannel(CH_STUDY, "Study reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Your daily FMGE reminder with today's revision"
        })
    }

    private fun pi(c: Context, action: String, rc: Int) = PendingIntent.getBroadcast(
        c, rc, Intent(c, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets (or clears) both alarms from the current settings. */
    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val prefs = app(c).prefs
        // Time Guard: every 15 minutes while any app is guarded.
        val guard = pi(c, ACTION_GUARD, 200)
        if (prefs.guard.value.isEmpty()) am.cancel(guard)
        else am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, AlarmManager.INTERVAL_FIFTEEN_MINUTES, guard)
        scheduleMilestone(c)
        // Weekly backup: checked daily (cheap), writes only when a week has passed.
        val backup = pi(c, ACTION_BACKUP, 206)
        if (com.malluwayne.batbloom.data.AutoBackup.enabled(c)) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_DAY, backup)
        } else am.cancel(backup)
        // FMGE reminder: once a day at the chosen time.
        val study = pi(c, ACTION_FMGE, 201)
        if (!prefs.fmgeReminder.value) { am.cancel(study); return }
        val now = ZonedDateTime.now()
        var at = now.toLocalDate().atTime(prefs.fmgeTime.value).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), study)
    }

    /** An exact alarm at the next health milestone after the quit day starts. */
    fun scheduleMilestone(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pi(c, ACTION_MILESTONE, 204)
        val start = app(c).prefs.quitStart(ZoneId.systemDefault())
        if (start == null) { am.cancel(pi); return }
        val since = java.time.Duration.between(start, ZonedDateTime.now())
        val next = com.malluwayne.batbloom.quit.Milestones.next(since)
        if (next == null) { am.cancel(pi); return }
        val at = start.plus(next.after).toInstant().toEpochMilli()
        if (com.malluwayne.batbloom.quit.DoseAlarms.canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Tells you about the milestone just reached (the latest one, if the phone was off for a while). */
    fun notifyMilestone(c: Context) {
        val quit = app(c).prefs.quitDate.value ?: return
        val start = app(c).prefs.quitStart(ZoneId.systemDefault()) ?: return
        val since = java.time.Duration.between(start, ZonedDateTime.now())
        val m = com.malluwayne.batbloom.quit.Milestones.reached(since).lastOrNull() ?: return
        val sp = c.getSharedPreferences("batbloom", Context.MODE_PRIVATE)
        if (sp.getString("milestone_told", "") == "$quit-${m.short}") return
        sp.edit().putString("milestone_told", "$quit-${m.short}").apply()
        val open = PendingIntent.getActivity(
            c, 205, Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_QUIT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CH_MILESTONE)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle("${m.title} smoke-free!")
            .setContentText(m.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${m.text} (Approximate; everyone is different.) Keep going."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(41, n) }
    }

    /**
     * Minutes each guarded app has been on screen today, added up from the open/close events (Android's own
     * daily totals can lag by hours). An app that is open right now counts up to this moment.
     */
    fun usageToday(c: Context, pkgs: Collection<String>): Map<String, Int> {
        if (!Device.hasUsageAccess(c) || pkgs.isEmpty()) return emptyMap()
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(start, now) }.getOrNull() ?: return emptyMap()
        val e = android.app.usage.UsageEvents.Event()
        val openSince = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        @Suppress("DEPRECATION")
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (pkg !in pkgs) continue
            when (e.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> openSince.putIfAbsent(pkg, e.timeStamp)
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> openSince.remove(pkg)?.let { total[pkg] = (total[pkg] ?: 0) + (e.timeStamp - it) }
            }
        }
        openSince.forEach { (pkg, t) -> total[pkg] = (total[pkg] ?: 0) + (now - t) }
        return pkgs.associateWith { ((total[it] ?: 0L) / 60_000).toInt() }
    }

    fun checkGuard(c: Context) {
        val prefs = app(c).prefs
        val limits = prefs.guard.value
        val day = LocalDate.now().toString()
        usageToday(c, limits.keys).forEach { (pkg, used) ->
            val limit = limits[pkg] ?: return@forEach
            val level = TimeGuard.level(used, limit)
            if (!TimeGuard.shouldRemind(level, prefs.guardReminded(day, pkg))) return@forEach
            prefs.setGuardReminded(day, pkg, level)
            val name = Device.appLabel(c, pkg)
            val open = PendingIntent.getActivity(
                c, 202, Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_FOCUS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(c, CH_GUARD)
                .setSmallIcon(R.drawable.ic_stat_bloom)
                .setContentTitle(if (level == 1) "$name: daily limit reached" else "$name: well past your limit")
                .setContentText("${Rules.formatMinutes(used)} today (limit ${Rules.formatMinutes(limit)}). Plant something instead?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(0, "Start focusing", open)
                .build()
            runCatching { NotificationManagerCompat.from(c).notify(300 + (pkg.hashCode() and 0xFFF), n) }
        }
    }

    suspend fun notifyStudy(c: Context) {
        val a = app(c)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val sessions = a.repo.sessions.first()
        val due = Revision.due(sessions, today, zone)
        val s = Stats.compute(sessions, today, zone)
        val goal = a.prefs.dailyGoal.value
        val exam = a.prefs.examDate.value?.let { Stats.examLine(it, today) }
        val text = buildString {
            append("Goal today: ${Rules.formatMinutes(goal)}")
            if (s.fmgeTodayMinutes > 0) append(" (${Rules.formatMinutes(s.fmgeTodayMinutes)} done)")
            exam?.let { append(" · $it") }
            if (due.isNotEmpty()) append("\nRevise today: " + due.take(4).joinToString(", ") { it.subject })
        }
        val open = PendingIntent.getActivity(
            c, 203, Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_FMGE)
                .putExtra(MainActivity.EXTRA_SUBJECT, due.firstOrNull()?.subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CH_STUDY)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle(if (due.isNotEmpty()) "FMGE: ${due.size} subject${if (due.size == 1) "" else "s"} to revise" else "Time for FMGE")
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Plan a session", open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(NOTIF_STUDY, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BatbloomApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_GUARD -> Reminders.checkGuard(context)
                    Reminders.ACTION_FMGE -> { Reminders.notifyStudy(context); Reminders.scheduleAll(context) }
                    Reminders.ACTION_BACKUP -> com.malluwayne.batbloom.data.AutoBackup.runIfDue(context)
                    Reminders.ACTION_MILESTONE -> {
                        Reminders.notifyMilestone(context)
                        Reminders.scheduleMilestone(context)
                        com.malluwayne.batbloom.widget.BloomWidget.refresh(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
