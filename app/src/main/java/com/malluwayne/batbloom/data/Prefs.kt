package com.malluwayne.batbloom.data

import android.content.Context
import com.malluwayne.batbloom.focus.PomodoroPlan
import com.malluwayne.batbloom.stats.SmokingProfile
import com.malluwayne.batbloom.stats.Treat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.malluwayne.batbloom.quit.DoseSchedule
import java.time.LocalDate
import java.time.LocalTime

/** Small settings kept in SharedPreferences, exposed as flows for the UI. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("batbloom", Context.MODE_PRIVATE)

    private val _selectedPlant = MutableStateFlow(sp.getString("plant", "moonflower")!!)
    val selectedPlant: StateFlow<String> = _selectedPlant
    fun setSelectedPlant(id: String) { sp.edit().putString("plant", id).apply(); _selectedPlant.value = id }

    private val _minutes = MutableStateFlow(sp.getInt("minutes", 25))
    val minutes: StateFlow<Int> = _minutes
    fun setMinutes(m: Int) { sp.edit().putInt("minutes", m).apply(); _minutes.value = m }

    private val _tag = MutableStateFlow(sp.getString("tag", Tags.FMGE)!!)
    val tag: StateFlow<String> = _tag
    fun setTag(t: String) { sp.edit().putString("tag", t).apply(); _tag.value = t }

    private val _subject = MutableStateFlow(sp.getString("subject", "Anatomy")!!)
    val subject: StateFlow<String> = _subject
    fun setSubject(s: String) { sp.edit().putString("subject", s).apply(); _subject.value = s }

    private val _quitDate = MutableStateFlow(date("quit_date", "2026-11-28"))
    val quitDate: StateFlow<LocalDate?> = _quitDate
    fun setQuitDate(d: LocalDate?) {
        putDate("quit_date", d); _quitDate.value = d
        runCatching { com.malluwayne.batbloom.guard.Reminders.scheduleMilestone(com.malluwayne.batbloom.BatbloomApp.instance) }
    }

    private val _examDate = MutableStateFlow(date("exam_date", null))
    val examDate: StateFlow<LocalDate?> = _examDate
    fun setExamDate(d: LocalDate?) { putDate("exam_date", d); _examDate.value = d }

    /** Daily FMGE study goal in minutes. */
    private val _dailyGoal = MutableStateFlow(sp.getInt("daily_goal", 240))
    val dailyGoal: StateFlow<Int> = _dailyGoal
    fun setDailyGoal(m: Int) { sp.edit().putInt("daily_goal", m).apply(); _dailyGoal.value = m }

    private val _stopwatch = MutableStateFlow(sp.getBoolean("stopwatch", false))
    val stopwatch: StateFlow<Boolean> = _stopwatch
    fun setStopwatch(on: Boolean) { sp.edit().putBoolean("stopwatch", on).apply(); _stopwatch.value = on; if (on) setPomodoro(false) }

    private val _pomodoro = MutableStateFlow(sp.getBoolean("pomodoro", false))
    val pomodoro: StateFlow<Boolean> = _pomodoro
    fun setPomodoro(on: Boolean) {
        sp.edit().putBoolean("pomodoro", on).apply(); _pomodoro.value = on
        if (on && _stopwatch.value) { sp.edit().putBoolean("stopwatch", false).apply(); _stopwatch.value = false }
    }

    private val _plan = MutableStateFlow(
        PomodoroPlan(sp.getInt("p_focus", 25), sp.getInt("p_short", 5), sp.getInt("p_long", 15), sp.getInt("p_rounds", 4)),
    )
    val plan: StateFlow<PomodoroPlan> = _plan
    fun setPlan(p: PomodoroPlan) {
        sp.edit().putInt("p_focus", p.focusMinutes).putInt("p_short", p.shortBreak).putInt("p_long", p.longBreak)
            .putInt("p_rounds", p.rounds).apply()
        _plan.value = p
    }

    private val _customTags = MutableStateFlow(sp.getStringSet("custom_tags", emptySet())!!.sorted())
    val customTags: StateFlow<List<String>> = _customTags
    fun addTag(t: String) {
        val clean = t.trim().take(20)
        if (clean.isEmpty() || clean in Tags.all || clean in _customTags.value) return
        val next = (_customTags.value + clean).sorted()
        sp.edit().putStringSet("custom_tags", next.toSet()).apply(); _customTags.value = next
    }
    fun removeTag(t: String) {
        val next = _customTags.value - t
        sp.edit().putStringSet("custom_tags", next.toSet()).apply(); _customTags.value = next
        if (_tag.value == t) setTag(Tags.STUDY)
    }

    /** Smoking numbers for the smoke-free card. */
    private val _smoking = MutableStateFlow(
        SmokingProfile(sp.getInt("cigs_day", 10), sp.getInt("pack_size", 20), sp.getFloat("pack_price", 340f).toDouble()),
    )
    val smoking: StateFlow<SmokingProfile> = _smoking
    fun setSmoking(s: SmokingProfile) {
        sp.edit().putInt("cigs_day", s.perDay).putInt("pack_size", s.packSize).putFloat("pack_price", s.packPrice.toFloat()).apply()
        _smoking.value = s
    }

    private val _quitReason = MutableStateFlow(sp.getString("quit_reason", "")!!)
    val quitReason: StateFlow<String> = _quitReason
    fun setQuitReason(s: String) { sp.edit().putString("quit_reason", s).apply(); _quitReason.value = s }

    private val _treat = MutableStateFlow(Treat(sp.getString("treat_name", "")!!, sp.getFloat("treat_price", 0f).toDouble()))
    val treat: StateFlow<Treat> = _treat
    fun setTreat(t: Treat) {
        sp.edit().putString("treat_name", t.name).putFloat("treat_price", t.price.toFloat()).apply(); _treat.value = t
    }

    // --- Varenicline course (defaults: the user's prescribed plan) ---

    private fun readSchedule(): DoseSchedule {
        val d = DoseSchedule()
        fun dbl(k: String, v: Double) = sp.getFloat(k, v.toFloat()).toDouble()
        return DoseSchedule(
            start = date("course_start", d.start.toString()) ?: d.start,
            courseDays = sp.getInt("course_days", d.courseDays),
            phase1End = sp.getInt("p1_end", d.phase1End),
            phase2End = sp.getInt("p2_end", d.phase2End),
            mg = listOf(
                dbl("mg1a", d.mg[0].first) to dbl("mg1p", d.mg[0].second),
                dbl("mg2a", d.mg[1].first) to dbl("mg2p", d.mg[1].second),
                dbl("mg3a", d.mg[2].first) to dbl("mg3p", d.mg[2].second),
            ),
            amTime = LocalTime.ofSecondOfDay(sp.getInt("am_time", d.amTime.toSecondOfDay()).toLong()),
            pmTime = LocalTime.ofSecondOfDay(sp.getInt("pm_time", d.pmTime.toSecondOfDay()).toLong()),
        )
    }
    private val _schedule = MutableStateFlow(readSchedule())
    val schedule: StateFlow<DoseSchedule> = _schedule
    fun setSchedule(s: DoseSchedule) {
        sp.edit()
            .putString("course_start", s.start.toString()).putInt("course_days", s.courseDays)
            .putInt("p1_end", s.phase1End).putInt("p2_end", s.phase2End)
            .putFloat("mg1a", s.mg[0].first.toFloat()).putFloat("mg1p", s.mg[0].second.toFloat())
            .putFloat("mg2a", s.mg[1].first.toFloat()).putFloat("mg2p", s.mg[1].second.toFloat())
            .putFloat("mg3a", s.mg[2].first.toFloat()).putFloat("mg3p", s.mg[2].second.toFloat())
            .putInt("am_time", s.amTime.toSecondOfDay()).putInt("pm_time", s.pmTime.toSecondOfDay())
            .apply()
        _schedule.value = s
    }

    /** Tablets left; negative means "not entered yet". */
    private val _tablets = MutableStateFlow(sp.getFloat("tablets_left", -1f).toDouble())
    val tabletsLeft: StateFlow<Double> = _tablets
    fun setTabletsLeft(t: Double) { sp.edit().putFloat("tablets_left", t.toFloat()).apply(); _tablets.value = t }

    private val _strength = MutableStateFlow(sp.getFloat("tablet_mg", 0.5f).toDouble())
    val tabletStrength: StateFlow<Double> = _strength
    fun setTabletStrength(mg: Double) { sp.edit().putFloat("tablet_mg", mg.toFloat()).apply(); _strength.value = mg }

    private val _reminders = MutableStateFlow(sp.getBoolean("reminders", true))
    val reminders: StateFlow<Boolean> = _reminders
    fun setReminders(on: Boolean) { sp.edit().putBoolean("reminders", on).apply(); _reminders.value = on }

    private val _snooze = MutableStateFlow(sp.getInt("snooze_min", 15))
    val snoozeMinutes: StateFlow<Int> = _snooze
    fun setSnoozeMinutes(m: Int) { sp.edit().putInt("snooze_min", m).apply(); _snooze.value = m }

    /** A missed dose is skipped when the next one is this close. */
    private val _closeHours = MutableStateFlow(sp.getInt("close_hours", 4))
    val closeHours: StateFlow<Int> = _closeHours
    fun setCloseHours(h: Int) { sp.edit().putInt("close_hours", h).apply(); _closeHours.value = h }

    private val _smokingSetup = MutableStateFlow(sp.getBoolean("smoking_setup", false))
    val smokingSetupDone: StateFlow<Boolean> = _smokingSetup
    fun setSmokingSetupDone() { sp.edit().putBoolean("smoking_setup", true).apply(); _smokingSetup.value = true }

    /** Last low-stock warning, so it is sent once a day at most. */
    var lastStockWarning: String
        get() = sp.getString("stock_warn", "")!!
        set(v) { sp.edit().putString("stock_warn", v).apply() }

    // --- Time Guard and reminders ---

    /** Daily limits in minutes per app package. */
    private val _guard = MutableStateFlow(com.malluwayne.batbloom.guard.TimeGuard.parse(sp.getString("guard", "")!!))
    val guard: StateFlow<Map<String, Int>> = _guard
    fun setGuard(m: Map<String, Int>) {
        sp.edit().putString("guard", com.malluwayne.batbloom.guard.TimeGuard.format(m)).apply(); _guard.value = m
    }
    fun guardReminded(day: String, pkg: String): Int = sp.getInt("gl_${day}_$pkg", 0)
    fun setGuardReminded(day: String, pkg: String, level: Int) {
        // Old days' entries are dropped so this doesn't grow forever.
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("gl_") && !it.startsWith("gl_${day}_") }.forEach { e.remove(it) }
        e.putInt("gl_${day}_$pkg", level).apply()
    }

    private val _fmgeReminder = MutableStateFlow(sp.getBoolean("fmge_rem", true))
    val fmgeReminder: StateFlow<Boolean> = _fmgeReminder
    fun setFmgeReminder(on: Boolean) { sp.edit().putBoolean("fmge_rem", on).apply(); _fmgeReminder.value = on }

    private val _fmgeTime = MutableStateFlow(LocalTime.ofSecondOfDay(sp.getInt("fmge_time", 9 * 3600).toLong()))
    val fmgeTime: StateFlow<LocalTime> = _fmgeTime
    fun setFmgeTime(t: LocalTime) { sp.edit().putInt("fmge_time", t.toSecondOfDay()).apply(); _fmgeTime.value = t }

    private fun date(key: String, default: String?): LocalDate? {
        val s = if (sp.contains(key)) sp.getString(key, null) else default
        return s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    private fun putDate(key: String, d: LocalDate?) { sp.edit().putString(key, d?.toString() ?: "").apply() }
}
