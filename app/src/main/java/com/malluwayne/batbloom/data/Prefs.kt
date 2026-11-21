package com.malluwayne.batbloom.data

import android.content.Context
import com.malluwayne.batbloom.focus.PomodoroPlan
import com.malluwayne.batbloom.stats.SmokingProfile
import com.malluwayne.batbloom.stats.Treat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

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

    private val _subject = MutableStateFlow(sp.getString("subject", Tags.fmgeSubjects.first())!!)
    val subject: StateFlow<String> = _subject
    fun setSubject(s: String) { sp.edit().putString("subject", s).apply(); _subject.value = s }

    private val _quitDate = MutableStateFlow(date("quit_date", "2026-11-28"))
    val quitDate: StateFlow<LocalDate?> = _quitDate
    fun setQuitDate(d: LocalDate?) { putDate("quit_date", d); _quitDate.value = d }

    private val _examDate = MutableStateFlow(date("exam_date", null))
    val examDate: StateFlow<LocalDate?> = _examDate
    fun setExamDate(d: LocalDate?) { putDate("exam_date", d); _examDate.value = d }

    /** Daily FMGE study goal in minutes. */
    private val _dailyGoal = MutableStateFlow(sp.getInt("daily_goal", 240))
    val dailyGoal: StateFlow<Int> = _dailyGoal
    fun setDailyGoal(m: Int) { sp.edit().putInt("daily_goal", m).apply(); _dailyGoal.value = m }

    private val _pomodoro = MutableStateFlow(sp.getBoolean("pomodoro", false))
    val pomodoro: StateFlow<Boolean> = _pomodoro
    fun setPomodoro(on: Boolean) { sp.edit().putBoolean("pomodoro", on).apply(); _pomodoro.value = on }

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

    private fun date(key: String, default: String?): LocalDate? {
        val s = if (sp.contains(key)) sp.getString(key, null) else default
        return s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    private fun putDate(key: String, d: LocalDate?) { sp.edit().putString(key, d?.toString() ?: "").apply() }
}
