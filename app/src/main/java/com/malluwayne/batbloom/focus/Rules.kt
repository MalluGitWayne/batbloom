package com.malluwayne.batbloom.focus

import kotlin.math.ceil

/** Pure rules for a focus session: durations, rewards and growth. No Android code, so it is unit tested. */
object Rules {
    const val MIN_MINUTES = 10
    const val MAX_MINUTES = 120
    const val CRAVING_MINUTES = 10
    const val GRACE_MS = 10_000L

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** 1 coin per minute, plus 5 bonus for every full half hour. Failed sessions earn nothing. */
    fun coinsFor(minutes: Int, success: Boolean): Int =
        if (!success) 0 else minutes + (minutes / 30) * 5

    /** Fraction of the session done, 0..1. */
    fun progress(startAt: Long, endAt: Long, now: Long): Float {
        if (endAt <= startAt) return 1f
        return ((now - startAt).toFloat() / (endAt - startAt)).coerceIn(0f, 1f)
    }

    fun canUnlock(balance: Int, cost: Int, alreadyOwned: Boolean): Boolean = !alreadyOwned && balance >= cost

    /** "1h 05m", "25m", "0m". */
    fun formatMinutes(total: Int): String {
        val h = total / 60
        val m = total % 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
    }
}

/** Pomodoro: focus rounds with a short break between them and a long break every [longEvery] rounds. */
data class PomodoroPlan(
    val focusMinutes: Int = 25,
    val shortBreak: Int = 5,
    val longBreak: Int = 15,
    val rounds: Int = 4,
    val longEvery: Int = 4,
) {
    fun isLast(round: Int) = round >= rounds
    /** Break after [round] (1-based), in minutes. */
    fun breakAfter(round: Int): Int = if (round % longEvery == 0) longBreak else shortBreak
    fun isLongBreak(round: Int) = round % longEvery == 0
    /** Whole cycle length, breaks included (no break after the last round). */
    fun totalMinutes(): Int = focusMinutes * rounds + (1 until rounds).sumOf { breakAfter(it) }
}

enum class Stage(val label: String) { Seed("Seed"), Sprout("Sprout"), Sapling("Sapling"), Bud("Bud"), Bloom("Bloom") }

fun stageFor(growth: Float): Stage = when {
    growth < 0.10f -> Stage.Seed
    growth < 0.35f -> Stage.Sprout
    growth < 0.65f -> Stage.Sapling
    growth < 1.00f -> Stage.Bud
    else -> Stage.Bloom
}

/** Decides which packages may be on screen during a session without failing it. */
class AllowPolicy(
    private val ownPackage: String,
    private val systemAllowed: Set<String>,
    private val userAllowed: Set<String>,
) {
    fun isAllowed(pkg: String): Boolean =
        pkg == ownPackage || pkg in systemAllowed || pkg in userAllowed
}

sealed interface Verdict {
    data object Ok : Verdict
    data class Warning(val secondsLeft: Int, val pkg: String) : Verdict
    data class Fail(val pkg: String) : Verdict
}

/**
 * Leaving for a non-allowed app starts a grace period; coming back (or going to an allowed app) within it
 * cancels the warning, otherwise the session fails. Screen off never counts as leaving.
 */
class LeaveGuard(private val graceMs: Long = Rules.GRACE_MS) {
    private var awaySince: Long? = null

    fun update(foreground: String?, allowed: Boolean, interactive: Boolean, now: Long): Verdict {
        if (!interactive || foreground == null || allowed) {
            awaySince = null
            return Verdict.Ok
        }
        val since = awaySince ?: now.also { awaySince = it }
        val left = graceMs - (now - since)
        return if (left <= 0) Verdict.Fail(foreground)
        else Verdict.Warning(ceil(left / 1000.0).toInt(), foreground)
    }
}
