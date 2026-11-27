package com.malluwayne.batbloom.focus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.malluwayne.batbloom.BatbloomApp
import com.malluwayne.batbloom.MainActivity
import com.malluwayne.batbloom.R
import com.malluwayne.batbloom.data.SessionEntity
import com.malluwayne.batbloom.focus.FocusEngine.State
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.widget.BloomWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs a focus session (or a Pomodoro cycle) in the foreground: shows the countdown notification, watches
 * which app is on screen and ends each round as bloomed (time is up) or wilted (left for another app, gave up).
 */
class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The timer runs on the phone's uptime, anchored to the wall clock once: changing the time in Settings can't
    // finish a session early or stretch it. Times stay in milliseconds since 1970 for the database and display.
    private val anchorWall = System.currentTimeMillis()
    private val anchorElapsed = android.os.SystemClock.elapsedRealtime()
    private fun now() = anchorWall + (android.os.SystemClock.elapsedRealtime() - anchorElapsed)
    private var loop: Job? = null
    private var guard = LeaveGuard()
    private var reader: ForegroundAppReader? = null
    private var systemAllowed: Set<String> = emptySet()
    private var userAllowed: Set<String> = emptySet()
    private val app get() = application as BatbloomApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        FocusEngine.alarmTick = { scope.launch { tick() } }
        scope.launch { app.repo.allowedApps.collect { userAllowed = it } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val req = FocusEngine.pending
                FocusEngine.pending = null
                when {
                    FocusEngine.isActive -> showOngoing()
                    req != null -> begin(req)
                    else -> { showPlaceholder(); stop() }
                }
            }
            ACTION_STOP -> (FocusEngine.state.value as? State.Running)?.let { stopStopwatch(it) } ?: stop()
            // From the session notification: let notifications through again, keep focusing.
            ACTION_DND_OFF -> { Dnd.restore(this); if (FocusEngine.state.value is State.Running || FocusEngine.state.value is State.Break) showOngoing() else stop() }
            ACTION_GIVE_UP -> when (val s = FocusEngine.state.value) {
                is State.Running -> roundOver(s, success = false, reason = "gave up")
                is State.Break -> finishAll(State.Finished(s.blooms > 0, s.request, s.request.plan?.focusMinutes ?: 0, null, s.blooms, s.request.plan?.rounds))
                else -> stop()
            }
            else -> if (!FocusEngine.isActive) stop()
        }
        return START_NOT_STICKY
    }

    private fun begin(req: FocusEngine.Request) {
        Dnd.start(this)
        systemAllowed = Device.systemAllowed(this)
        reader = if (Device.hasUsageAccess(this)) ForegroundAppReader(this) else null
        startRound(req, if (req.plan != null) 1 else null, blooms = 0)
        if (loop == null) loop = scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    private fun startRound(req: FocusEngine.Request, round: Int?, blooms: Int) {
        val minutes = if (req.stopwatch) Rules.STOPWATCH_MAX_MINUTES else Rules.clampMinutes(req.plan?.focusMinutes ?: req.minutes)
        val start = now()
        val r = State.Running(-1, req, minutes, start, start + minutes * 60_000L, round, blooms)
        guard = LeaveGuard()
        FocusEngine.state.value = r
        showOngoing()
        scheduleAlarm(r.endAt)
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                app.repo.startSession(
                    SessionEntity(
                        startedAt = start, plannedMinutes = minutes, tag = req.tag, subject = req.subject,
                        plantId = req.plantId, round = round,
                    ),
                )
            }
            val now = FocusEngine.state.value
            if (now is State.Running && now.startAt == start) FocusEngine.state.value = now.copy(sessionId = id)
            // Given up in the instant before its row was written (a round lasts at least 10 minutes).
            else withContext(Dispatchers.IO) { app.repo.finishSession(id, false, now(), "gave up") }
            BloomWidget.refresh(this@FocusService)
        }
    }

    private var lastWidget = 0L

    private fun tick() {
        val now = now()
        // The widget's plant grows with the timer: redraw it once a minute.
        if (now - lastWidget >= 60_000) { lastWidget = now; BloomWidget.refresh(this) }
        when (val s = FocusEngine.state.value) {
            is State.Break -> if (now >= s.endAt) startRound(s.request, s.nextRound, s.blooms)
            is State.Running -> {
                if (now >= s.endAt) {
                    if (s.request.stopwatch) stopStopwatch(s) else roundOver(s, success = true, reason = null)
                    return
                }
                val rd = reader ?: return
                val pkg = rd.poll(now)
                val policy = AllowPolicy(packageName, systemAllowed, userAllowed)
                val interactive = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                when (val v = guard.update(pkg, pkg?.let(policy::isAllowed) ?: true, interactive, now)) {
                    Verdict.Ok -> if (s.warningSeconds != null) {
                        FocusEngine.state.value = s.copy(warningSeconds = null, warningApp = null)
                        NotificationManagerCompat.from(this).cancel(NOTIF_WARN)
                    }
                    is Verdict.Warning -> {
                        val label = Device.appLabel(this, v.pkg)
                        FocusEngine.state.value = s.copy(warningSeconds = v.secondsLeft, warningApp = label)
                        notifyWarning(s, v.secondsLeft, label)
                    }
                    is Verdict.Fail -> roundOver(s, success = false, reason = Device.appLabel(this, v.pkg))
                }
            }
            else -> Unit
        }
    }

    private fun stopStopwatch(r: State.Running) {
        val elapsed = (now().coerceAtMost(r.endAt)) - r.startAt
        val ok = Rules.stopwatchCounts(elapsed)
        roundOver(r, ok, if (ok) null else "too short", Rules.stopwatchMinutes(elapsed))
    }

    private fun roundOver(r: State.Running, success: Boolean, reason: String?, actualMinutes: Int? = null) {
        cancelAlarm()
        NotificationManagerCompat.from(this).cancel(NOTIF_WARN)
        val minutes = actualMinutes ?: r.minutes
        val endedAt = if (actualMinutes != null) now().coerceAtMost(r.endAt) else if (success) r.endAt else now()
        val blooms = r.blooms + if (success) 1 else 0
        if (r.sessionId >= 0) app.appScope.launch {
            app.repo.finishSession(r.sessionId, success, endedAt, reason, actualMinutes)
            BloomWidget.refresh(app)
        }
        val plan = r.request.plan
        val round = r.round
        if (success && plan != null && round != null && !plan.isLast(round)) {
            val now = now()
            val b = State.Break(r.request, now, now + plan.breakAfter(round) * 60_000L, round + 1, plan.isLongBreak(round), blooms)
            FocusEngine.state.value = b
            notifyResult("Round $round bloomed! Break time", "${plan.breakAfter(round)} min break, then round ${round + 1} of ${plan.rounds} starts by itself.")
            showOngoing()
            scheduleAlarm(b.endAt)
            BloomWidget.refresh(this)
            return
        }
        finishAll(State.Finished(success, r.request, minutes, reason, blooms, plan?.rounds))
    }

    private fun finishAll(f: State.Finished) {
        cancelAlarm()
        Dnd.restore(this)
        FocusEngine.state.value = f
        val name = PlantCatalog.byId(f.request.plantId).name
        val title = when {
            f.rounds != null && f.success -> "Pomodoro done: ${f.blooms}/${f.rounds} blooms"
            f.rounds != null -> "Pomodoro ended: ${f.blooms}/${f.rounds} blooms"
            f.success -> "Your $name bloomed!"
            else -> "Your $name wilted"
        }
        val text = when (f.reason) {
            null -> if (f.success) "${f.minutes} min of ${label(f.request)} done." else "Session ended."
            "gave up" -> "You gave up this session. Try a shorter one next time."
            "too short" -> "A stopwatch session needs at least ${Rules.MIN_MINUTES} minutes to grow a plant."
            else -> "You opened ${f.reason}. Add it to allowed apps if you need it while focusing."
        }
        notifyResult(title, text)
        BloomWidget.refresh(this)
        app.appScope.launch { com.malluwayne.batbloom.quit.DoseAlarms.checkBadges(app) }
        stop()
    }

    private fun stop() {
        loop?.cancel()
        loop = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // However the session ended, Do Not Disturb goes back to how it was.
        if (!FocusEngine.isActive) Dnd.restore(this)
        FocusEngine.alarmTick = null
        scope.cancel()
        super.onDestroy()
    }

    // --- Notifications ---

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun label(r: FocusEngine.Request) = listOfNotNull(r.tag, r.subject).joinToString(" · ")

    private fun showPlaceholder() {
        val n = NotificationCompat.Builder(this, CH_SESSION).setSmallIcon(R.drawable.ic_stat_bloom).setContentTitle("Batbloom").build()
        startFg(n)
    }

    private fun showOngoing() {
        val (title, text, endAt) = when (val s = FocusEngine.state.value) {
            is State.Running -> Triple(
                "Growing your ${PlantCatalog.byId(s.plantId).name}" + (s.round?.let { " · round $it/${s.request.plan?.rounds}" } ?: ""),
                "${label(s.request)} · " + if (s.request.stopwatch) "stopwatch, stop when you're done" else "stay with it",
                if (s.request.stopwatch) s.startAt else s.endAt,
            )
            is State.Break -> Triple(
                if (s.long) "Long break" else "Short break",
                "Round ${s.nextRound} of ${s.request.plan?.rounds} starts when the timer ends. Apps are free now.",
                s.endAt,
            )
            else -> return
        }
        val giveUp = PendingIntent.getService(
            this, 1, Intent(this, FocusService::class.java).setAction(ACTION_GIVE_UP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown((FocusEngine.state.value as? State.Running)?.request?.stopwatch != true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp())
            .addAction(0, if (FocusEngine.state.value is State.Break) "End cycle" else "Give up", giveUp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .apply {
                if (Dnd.active(this@FocusService)) addAction(
                    0, "Allow notifications",
                    PendingIntent.getService(this@FocusService, 5, Intent(this@FocusService, FocusService::class.java).setAction(ACTION_DND_OFF), PendingIntent.FLAG_IMMUTABLE),
                )
                if ((FocusEngine.state.value as? State.Running)?.request?.stopwatch == true) addAction(
                    0, "Stop",
                    PendingIntent.getService(this@FocusService, 4, Intent(this@FocusService, FocusService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
                )
            }
            .build()
        startFg(n)
    }

    private fun startFg(n: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_SESSION, n, type)
    }

    private fun notifyWarning(r: State.Running, seconds: Int, app: String) {
        val n = NotificationCompat.Builder(this, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle("Come back! Your ${PlantCatalog.byId(r.plantId).name} is wilting")
            .setContentText("Leave $app within ${seconds}s to keep your session.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        com.malluwayne.batbloom.Notify.post(this, NOTIF_WARN, n)
    }

    private fun notifyResult(title: String, text: String) {
        val n = NotificationCompat.Builder(this, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bloom)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        com.malluwayne.batbloom.Notify.post(this, NOTIF_RESULT, n)
    }

    // --- End-of-timer alarm: wakes the phone so the bloom (or break end) happens on time with the screen off ---

    private fun alarmIntent() = PendingIntent.getBroadcast(
        this, 2, Intent(this, SessionAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarm(at: Long) {
        // Exact when allowed, so a round blooms and the next one starts on time even in a pocket.
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // On the uptime clock too, so a changed wall clock doesn't move it.
        val atElapsed = anchorElapsed + (at - anchorWall)
        if (com.malluwayne.batbloom.quit.DoseAlarms.canExact(this)) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, alarmIntent())
        else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, alarmIntent())
    }

    private fun cancelAlarm() {
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent())
    }

    companion object {
        const val ACTION_START = "batbloom.START"
        const val ACTION_GIVE_UP = "batbloom.GIVE_UP"
        const val ACTION_STOP = "batbloom.STOP"
        const val ACTION_DND_OFF = "batbloom.DND_OFF"
        const val CH_SESSION = "session"
        const val CH_ALERTS = "alerts"
        const val NOTIF_SESSION = 1
        const val NOTIF_WARN = 2
        const val NOTIF_RESULT = 3

        fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The countdown while a plant is growing"
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERTS, "Session alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Warnings when you leave, and when a plant blooms or wilts"
                },
            )
        }
    }
}

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusEngine.alarmTick?.invoke()
    }
}
