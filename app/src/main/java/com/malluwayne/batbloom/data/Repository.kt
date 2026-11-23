package com.malluwayne.batbloom.data

import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.plants.PlantCatalog
import com.malluwayne.batbloom.quit.Badges
import com.malluwayne.batbloom.quit.DoseSchedule
import com.malluwayne.batbloom.quit.DoseSlot
import com.malluwayne.batbloom.quit.Doses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class Repository(private val db: AppDatabase) {
    private val quit = db.quit()

    val sessions: Flow<List<SessionEntity>> = db.sessions().finished()
    val allowedApps: Flow<Set<String>> = db.allowed().all().map { it.toSet() }
    val badges: Flow<List<BadgeEntity>> = quit.badges()
    /** Finished sessions; plants unlock as this grows. */
    val bloomCount: Flow<Int> = sessions.map { l -> l.count { it.status == Status.DONE } }
    val unlocked: Flow<Set<String>> = combine(db.unlocks().all(), quit.badges(), bloomCount) { list, badges, blooms ->
        val earned = badges.map { it.id }.toSet()
        list.map { it.plantId }.toSet() + PlantCatalog.all.filter {
            if (it.badge != null) it.badge in earned else blooms >= it.cost
        }.map { it.id }
    }

    val doses: Flow<List<DoseLogEntity>> = quit.doses()

    suspend fun startSession(s: SessionEntity): Long = db.sessions().insert(s)
    /** [actualMinutes]: for stopwatch sessions, the minutes it really ran (replaces the planned length). */
    suspend fun finishSession(id: Long, success: Boolean, endedAt: Long, reason: String?, actualMinutes: Int? = null): Int {
        val s = db.sessions().get(id) ?: return 0
        if (actualMinutes != null) db.sessions().setMinutes(id, actualMinutes)
        db.sessions().finish(id, if (success) Status.DONE else Status.FAILED, endedAt, reason)
        return actualMinutes ?: s.plannedMinutes
    }
    suspend fun clearWilted() = db.sessions().clearWilted()
    suspend fun removeWilted(id: Long) = db.sessions().removeWilted(id)
    suspend fun failStaleRunning() = db.sessions().failStaleRunning()
    suspend fun finishedSince(since: Long) = db.sessions().finishedSince(since)

    suspend fun setAllowed(pkg: String, allowed: Boolean) =
        if (allowed) db.allowed().add(AllowedAppEntity(pkg)) else db.allowed().remove(pkg)
    suspend fun allowedNow(): Set<String> = db.allowed().allNow().toSet()

    // --- Doses ---

    suspend fun doseStatus(key: String): String? = quit.dose(key)?.status

    /**
     * Marks a dose Taken or Skipped ([status] null clears it). Taking a dose uses tablets from the stock;
     * changing your mind gives them back. Returns the stock change in tablets.
     */
    suspend fun markDose(slot: DoseSlot, status: String?, strength: Double): Double {
        val before = quit.dose(slot.key)
        val tablets = Doses.tablets(slot.mg, strength)
        if (status == null) quit.deleteDose(slot.key)
        else quit.logDose(DoseLogEntity(slot.key, slot.day, slot.mg, tablets, status, System.currentTimeMillis()))
        val wasTaken = before?.status == DoseStatus.TAKEN
        val isTaken = status == DoseStatus.TAKEN
        return when {
            isTaken && !wasTaken -> -tablets
            wasTaken && !isTaken -> before!!.tablets
            else -> 0.0
        }
    }

    /** Awards any badges now earned and returns the new ones. */
    suspend fun awardBadges(today: LocalDate, schedule: DoseSchedule, quitDate: LocalDate?): List<String> {
        val done = db.backup().sessions()
        val zone = java.time.ZoneId.systemDefault()
        val st = com.malluwayne.batbloom.stats.Stats.compute(done, today, zone)
        val earned = Badges.earned(today, schedule, quitDate, facts = Badges.facts(
            done, maxOf(st.bestStreak, st.streakDays), zone,
            doseStreak = Badges.bestDoseStreak(schedule, db.backup().doses().filter { it.status == DoseStatus.TAKEN }.map { it.key }.toSet(), today),
        ))
        return earned.filter { id ->
            quit.award(BadgeEntity(id, System.currentTimeMillis())) != -1L
        }
    }
}
