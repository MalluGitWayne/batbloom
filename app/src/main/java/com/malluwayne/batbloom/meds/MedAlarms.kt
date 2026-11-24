package com.malluwayne.batbloom.meds

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.data.DoseStatus
import com.malluwayne.batbloom.data.MedEntity
import com.malluwayne.batbloom.data.MedLogEntity
import com.malluwayne.batbloom.quit.DoseAlarms
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Logging doses: keeps the stock in step and clears the reminder once a time's doses are all logged. */
object Meds {
    private fun app(context: Context) = context.applicationContext as BatbloomApp
    private fun dao(context: Context) = app(context).db.meds()

    suspend fun save(context: Context, m: MedEntity): Long {
        val id = if (m.id == 0L) dao(context).insert(m) else { dao(context).update(m); m.id }
        MedAlarms.scheduleNext(context)
        return id
    }

    suspend fun delete(context: Context, id: Long) {
        dao(context).deleteLogsFor(id); dao(context).delete(id)
        MedAlarms.scheduleNext(context)
    }

    suspend fun setArchived(context: Context, m: MedEntity, archived: Boolean) {
        dao(context).update(m.copy(archived = archived))
        MedAlarms.scheduleNext(context)
    }

    private suspend fun adjustStock(context: Context, medId: Long, change: Double) {
        if (change == 0.0) return
        val m = dao(context).get(medId) ?: return
        if (m.stock >= 0) dao(context).setStock(medId, (m.stock + change).coerceAtLeast(0.0))
    }

    /** Logs a scheduled dose as Taken or Skipped (replacing what was logged before). */
    suspend fun mark(context: Context, dose: MedDose, status: String, at: Long = System.currentTimeMillis()) {
        val before = dao(context).logFor(dose.med.id, dose.slot)
        dao(context).log(MedLogEntity(before?.id ?: 0, dose.med.id, dose.slot, status, dose.qty, at))
        val wasTaken = before?.status == DoseStatus.TAKEN
        val isTaken = status == DoseStatus.TAKEN
        adjustStock(context, dose.med.id, if (isTaken && !wasTaken) -dose.qty else if (wasTaken && !isTaken) before!!.quantity else 0.0)
        MedAlarms.clearIfDone(context, dose.at)
    }

    /** Removes a log entry (scheduled or as-needed), giving back the stock if it was taken. */
    suspend fun undo(context: Context, logId: Long) {
        val l = dao(context).logById(logId) ?: return
        dao(context).deleteLog(logId)
        if (l.status == DoseStatus.TAKEN) adjustStock(context, l.medId, l.quantity)
    }

    suspend fun logAsNeeded(context: Context, m: MedEntity, qty: Double, at: Long = System.currentTimeMillis()) {
        dao(context).log(MedLogEntity(0, m.id, null, DoseStatus.TAKEN, qty, at))
        adjustStock(context, m.id, -qty)
    }

    suspend fun setTime(context: Context, logId: Long, at: Long) = dao(context).setLogTime(logId, at)

    /** Scheduled doses at [at] that are not logged yet. */
    suspend fun openAt(context: Context, at: LocalDateTime): List<MedDose> {
        val doses = MedSchedule.dosesOn(dao(context).allNow(), at.toLocalDate()).filter { it.at == at }
        if (doses.isEmpty()) return emptyList()
        val logged = dao(context).logsForSlots(doses.map { it.slot }.distinct()).map { it.medId to it.slot }.toSet()
        return doses.filter { (it.med.id to it.slot) !in logged }
    }
}

/**
 * Medication reminders: one exact alarm for the next scheduled time across all medications. It posts one
 * notification for everything due then (Taken / Skip / Snooze), and a follow-up 30 minutes later if still open.
 */
object MedAlarms {
    const val CH_MEDS = "meds"
    const val ACTION_DUE = "batbloom.MED_DUE"
    const val ACTION_TAKEN = "batbloom.MED_TAKEN"
    const val ACTION_SKIP = "batbloom.MED_SKIP"
    const val ACTION_SNOOZE = "batbloom.MED_SNOOZE"
    const val EXTRA_AT = "at"
    const val EXTRA_FOLLOW_UP = "followUp"
    private const val RC_NEXT = 500
    private const val RC_SNOOZE = 501
    private const val RC_FOLLOW = 502
    private const val FOLLOW_UP_MIN = 30L

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH_MEDS, "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for the medications you added"
            },
        )
    }

    private fun app(context: Context) = context.applicationContext as BatbloomApp

    private fun pi(context: Context, action: String, at: String?, rc: Int, followUp: Boolean = false) = PendingIntent.getBroadcast(
        context, rc,
        Intent(context, MedReceiver::class.java).setAction(action).apply {
            at?.let { putExtra(EXTRA_AT, it) }
            putExtra(EXTRA_FOLLOW_UP, followUp)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setAlarm(context: Context, atMs: Long, p: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (DoseAlarms.canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
    }

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun notifId(at: LocalDateTime) = 3000 + (at.toString().hashCode() and 0xFFF)

    suspend fun scheduleNext(context: Context) {
        val meds = app(context).db.meds().allNow()
        val next = MedSchedule.nextTime(meds, LocalDateTime.now())
        val am = context.getSystemService(AlarmManager::class.java)
        if (next == null || !app(context).prefs.reminders.value) {
            am.cancel(pi(context, ACTION_DUE, null, RC_NEXT)); return
        }
        setAlarm(context, millis(next), pi(context, ACTION_DUE, next.toString(), RC_NEXT))
    }

    suspend fun notifyDue(context: Context, at: LocalDateTime, followUp: Boolean) {
        val open = Meds.openAt(context, at)
        if (open.isEmpty()) return
        val time = DateTimeFormatter.ofPattern("h:mm a").format(at)
        val names = open.joinToString(", ") { it.med.name }
        val lines = open.joinToString("\n") { d ->
            listOf(d.med.name, MedSchedule.strengthLabel(d.med), MedSchedule.qtyLabel(d.med, d.qty)).filter { it.isNotBlank() }.joinToString(" · ")
        }
        val openApp = PendingIntent.getActivity(
            context, 32, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_MEDS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val key = at.toString()
        val rc = notifId(at)
        val n = NotificationCompat.Builder(context, CH_MEDS)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle(if (followUp) "Reminder: $time medications" else if (open.size == 1) "Time for ${open[0].med.name}" else "Time for your $time medications")
            .setContentText(if (open.size == 1) lines else names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .addAction(0, if (open.size == 1) "Taken" else "All taken", pi(context, ACTION_TAKEN, key, rc + 1))
            .addAction(0, "Skip", pi(context, ACTION_SKIP, key, rc + 2))
            .addAction(0, "Snooze ${app(context).prefs.snoozeMinutes.value} min", pi(context, ACTION_SNOOZE, key, rc + 3))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(rc, n) }
        if (!followUp) setAlarm(context, System.currentTimeMillis() + FOLLOW_UP_MIN * 60_000, pi(context, ACTION_DUE, key, RC_FOLLOW, followUp = true))
    }

    /** Takes the notification away once nothing at [at] is left to log. */
    suspend fun clearIfDone(context: Context, at: LocalDateTime) {
        if (Meds.openAt(context, at).isEmpty()) NotificationManagerCompat.from(context).cancel(notifId(at))
    }

    suspend fun markAll(context: Context, at: LocalDateTime, status: String) {
        Meds.openAt(context, at).forEach { Meds.mark(context, it, status) }
        NotificationManagerCompat.from(context).cancel(notifId(at))
    }

    fun snooze(context: Context, at: LocalDateTime) {
        NotificationManagerCompat.from(context).cancel(notifId(at))
        setAlarm(context, System.currentTimeMillis() + app(context).prefs.snoozeMinutes.value * 60_000L, pi(context, ACTION_DUE, at.toString(), RC_SNOOZE, followUp = true))
    }
}

class MedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as BatbloomApp
        val at = intent.getStringExtra(MedAlarms.EXTRA_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    MedAlarms.ACTION_DUE -> {
                        at?.let { MedAlarms.notifyDue(context, it, intent.getBooleanExtra(MedAlarms.EXTRA_FOLLOW_UP, false)) }
                        MedAlarms.scheduleNext(context)
                    }
                    MedAlarms.ACTION_TAKEN -> at?.let { MedAlarms.markAll(context, it, DoseStatus.TAKEN) }
                    MedAlarms.ACTION_SKIP -> at?.let { MedAlarms.markAll(context, it, DoseStatus.SKIPPED) }
                    MedAlarms.ACTION_SNOOZE -> at?.let { MedAlarms.snooze(context, it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
