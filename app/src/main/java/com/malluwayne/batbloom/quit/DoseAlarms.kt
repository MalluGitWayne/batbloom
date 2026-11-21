package com.malluwayne.batbloom.quit

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.data.DoseStatus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Dose reminders: one exact alarm for the next dose at a time. When it fires we post the reminder (unless the
 * dose is already logged) and schedule the following one. Reboots and clock changes reschedule it.
 */
object DoseAlarms {
    const val CH_DOSES = "doses"
    private const val CH_QUIT = "quit"
    const val ACTION_DUE = "batbloom.DOSE_DUE"
    const val ACTION_TAKEN = "batbloom.DOSE_TAKEN"
    const val ACTION_SKIP = "batbloom.DOSE_SKIP"
    const val ACTION_SNOOZE = "batbloom.DOSE_SNOOZE"
    const val EXTRA_KEY = "key"
    private const val RC_NEXT = 100
    private const val RC_SNOOZE = 101
    private const val NOTIF_STOCK = 20
    private const val NOTIF_BADGE = 21

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_DOSES, "Dose reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders to take your tablets"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUIT, "Quit progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Low tablet stock and badges"
            },
        )
    }

    private fun app(context: Context) = context.applicationContext as BatbloomApp

    fun slotFor(context: Context, key: String): DoseSlot? {
        val schedule = app(context).prefs.schedule.value
        val date = runCatching { LocalDate.parse(key.substring(0, 10)) }.getOrNull() ?: return null
        val morning = key.endsWith("AM")
        return schedule.slotsOn(date).firstOrNull { it.morning == morning }
    }

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun receiverIntent(context: Context, action: String, key: String?, rc: Int) = PendingIntent.getBroadcast(
        context, rc,
        Intent(context, DoseReceiver::class.java).setAction(action).apply { key?.let { putExtra(EXTRA_KEY, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** (Re)schedules the alarm for the next dose; cancels it when reminders are off or the course is over. */
    fun scheduleNext(context: Context) {
        val prefs = app(context).prefs
        val am = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        val next = prefs.schedule.value.nextSlot(now)
        if (!prefs.reminders.value || next == null) {
            am.cancel(receiverIntent(context, ACTION_DUE, null, RC_NEXT))
            return
        }
        val at = prefs.schedule.value.timeOf(next, now.zone).toInstant().toEpochMilli()
        setAlarm(context, at, receiverIntent(context, ACTION_DUE, next.key, RC_NEXT))
    }

    /** Marks [key] taken when tapped (used by the widget's dose button). */
    fun takenIntent(context: Context, key: String): PendingIntent = receiverIntent(context, ACTION_TAKEN, key, 400 + (key.hashCode() and 0xFF))

    /** Today's dose that is due now (from 1 h before until 4 h after its time) and not yet logged. */
    suspend fun dueNow(context: Context): DoseSlot? {
        val a = app(context)
        val s = a.prefs.schedule.value
        val now = ZonedDateTime.now()
        return s.slotsOn(now.toLocalDate()).firstOrNull { slot ->
            val t = s.timeOf(slot, now.zone)
            !now.isBefore(t.minusHours(1)) && !now.isAfter(t.plusHours(4)) && a.repo.doseStatus(slot.key) == null
        }
    }

    fun notifyDose(context: Context, slot: DoseSlot) {
        val prefs = app(context).prefs
        val s = prefs.schedule.value
        val strength = prefs.tabletStrength.value
        val tablets = Doses.tablets(slot.mg, strength)
        val open = PendingIntent.getActivity(
            context, 30, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_QUIT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val rc = slot.key.hashCode()
        val n = NotificationCompat.Builder(context, CH_DOSES)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle("Varenicline ${Doses.formatMg(slot.mg)}: ${slot.label.lowercase()} dose")
            .setContentText("Day ${slot.day} of ${s.courseDays} · ${Doses.formatTablets(tablets)} of ${Doses.formatMg(strength)}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Day ${slot.day} of ${s.courseDays}: ${Doses.formatTablets(tablets)} of ${Doses.formatMg(strength)}. " +
                        "Take it after food with a full glass of water.",
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(false)
            .addAction(0, "Taken", receiverIntent(context, ACTION_TAKEN, slot.key, rc + 1))
            .addAction(0, "Skip", receiverIntent(context, ACTION_SKIP, slot.key, rc + 2))
            .addAction(0, "Snooze ${prefs.snoozeMinutes.value} min", receiverIntent(context, ACTION_SNOOZE, slot.key, rc + 3))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId(slot.key), n) }
    }

    private fun notifId(key: String) = 1000 + (key.hashCode() and 0xFFFF)

    fun cancelDoseNotification(context: Context, key: String) = NotificationManagerCompat.from(context).cancel(notifId(key))

    fun snooze(context: Context, key: String) {
        cancelDoseNotification(context, key)
        val at = System.currentTimeMillis() + app(context).prefs.snoozeMinutes.value * 60_000L
        setAlarm(context, at, receiverIntent(context, ACTION_DUE, key, RC_SNOOZE))
    }

    /** Logs a dose (from the notification or the app), updates the stock and warns if it is running low. */
    suspend fun mark(context: Context, slot: DoseSlot, status: String?) {
        val a = app(context)
        val strength = a.prefs.tabletStrength.value
        val change = a.repo.markDose(slot, status, strength)
        val left = a.prefs.tabletsLeft.value
        if (left >= 0 && change != 0.0) a.prefs.setTabletsLeft((left + change).coerceAtLeast(0.0))
        if (status != null) cancelDoseNotification(context, slot.key)
        checkStock(context)
        com.malluwayne.batbloom.widget.BloomWidget.refresh(context)
    }

    suspend fun checkStock(context: Context) {
        val a = app(context)
        val left = a.prefs.tabletsLeft.value
        if (left < 0) return
        val today = LocalDate.now()
        val s = a.prefs.schedule.value
        val logged = s.slotsOn(today).filter { a.repo.doseStatus(it.key) != null }.map { it.key }.toSet()
        val stock = Doses.stock(s, left, a.prefs.tabletStrength.value, today, logged)
        if (!Doses.lowStock(stock) || a.prefs.lastStockWarning == today.toString()) return
        a.prefs.lastStockWarning = today.toString()
        val n = NotificationCompat.Builder(context, CH_QUIT)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle("Tablets running low")
            .setContentText(
                "${Doses.formatTablets(left)} left: enough until " +
                    "${java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(stock.coveredUntil ?: today)}. Time for a refill.",
            )
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_STOCK, n) }
    }

    suspend fun checkBadges(context: Context) {
        val a = app(context)
        val new = a.repo.awardBadges(LocalDate.now(ZoneId.systemDefault()), a.prefs.schedule.value, a.prefs.quitDate.value)
        new.forEachIndexed { i, id ->
            val b = Badges.byId(id)
            val n = NotificationCompat.Builder(context, CH_QUIT)
                .setSmallIcon(R.drawable.ic_stat_bloom)
                .setContentTitle("Badge earned: ${b.title}")
                .setContentText(b.text)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_BADGE + i, n) }
        }
    }
}

class DoseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BatbloomApp
        val key = intent.getStringExtra(DoseAlarms.EXTRA_KEY)
        val pending = goAsync()
        app.appScope.launch {
            try {
                val slot = key?.let { DoseAlarms.slotFor(context, it) }
                when (intent.action) {
                    DoseAlarms.ACTION_DUE -> {
                        if (slot != null && app.repo.doseStatus(slot.key) == null) DoseAlarms.notifyDose(context, slot)
                        DoseAlarms.scheduleNext(context)
                        com.malluwayne.batbloom.widget.BloomWidget.refresh(context)
                        DoseAlarms.checkStock(context)
                        DoseAlarms.checkBadges(context)
                    }
                    DoseAlarms.ACTION_TAKEN -> slot?.let { DoseAlarms.mark(context, it, DoseStatus.TAKEN) }
                    DoseAlarms.ACTION_SKIP -> slot?.let { DoseAlarms.mark(context, it, DoseStatus.SKIPPED) }
                    DoseAlarms.ACTION_SNOOZE -> key?.let { DoseAlarms.snooze(context, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are cleared by a reboot, an app update or a clock change: set the next one again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DoseAlarms.ensureChannels(context)
        DoseAlarms.scheduleNext(context)
        com.malluwayne.batbloom.guard.Reminders.scheduleAll(context)
        com.malluwayne.batbloom.widget.BloomWidget.refresh(context)
    }
}
