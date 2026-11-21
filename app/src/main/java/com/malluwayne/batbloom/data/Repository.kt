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
    val unlocked: Flow<Set<String>> = combine(db.unlocks().all(), quit.badges()) { list, badges ->
        val earned = badges.map { it.id }.toSet()
        list.map { it.plantId }.toSet() +
            PlantCatalog.all.filter { (it.cost == 0 && it.badge == null) || (it.badge != null && it.badge in earned) }.map { it.id }
    }
    val coins: Flow<Int> = combine(db.sessions().earned(), quit.badgeCoins(), db.unlocks().spent()) { e, b, s -> e + b - s }

    val doses: Flow<List<DoseLogEntity>> = quit.doses()
    val slips: Flow<List<SlipEntity>> = quit.slips()
    val sideEffects: Flow<List<SideEffectEntity>> = quit.sideEffects()

    suspend fun startSession(s: SessionEntity): Long = db.sessions().insert(s)
    /** [actualMinutes]: for stopwatch sessions, the minutes it really ran (replaces the planned length). */
    suspend fun finishSession(id: Long, success: Boolean, endedAt: Long, reason: String?, actualMinutes: Int? = null): Int {
        val s = db.sessions().get(id) ?: return 0
        if (actualMinutes != null) db.sessions().setMinutes(id, actualMinutes)
        val coins = Rules.coinsFor(actualMinutes ?: s.plannedMinutes, success)
        db.sessions().finish(id, if (success) Status.DONE else Status.FAILED, endedAt, coins, reason)
        return coins
    }
    suspend fun setCraving(id: Long, intensity: Int?, trigger: String?) = db.sessions().setCraving(id, intensity, trigger)
    suspend fun clearWilted() = db.sessions().clearWilted()
    suspend fun removeWilted(id: Long) = db.sessions().removeWilted(id)
    suspend fun failStaleRunning() = db.sessions().failStaleRunning()
    suspend fun finishedSince(since: Long) = db.sessions().finishedSince(since)

    suspend fun unlock(plantId: String, cost: Int) = db.unlocks().insert(UnlockEntity(plantId, cost, System.currentTimeMillis()))

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

    // --- Slips and side effects ---

    suspend fun addSlip(count: Int, note: String?) {
        quit.addSlip(SlipEntity(at = System.currentTimeMillis(), count = count, note = note))
        com.malluwayne.batbloom.widget.SmokeFreeWidget.update(com.malluwayne.batbloom.BatbloomApp.instance)
    }
    suspend fun deleteSlip(id: Long) {
        quit.deleteSlip(id)
        com.malluwayne.batbloom.widget.SmokeFreeWidget.update(com.malluwayne.batbloom.BatbloomApp.instance)
    }
    suspend fun addSideEffect(kind: String, severity: Int, note: String?) =
        quit.addSideEffect(SideEffectEntity(at = System.currentTimeMillis(), kind = kind, severity = severity, note = note))
    suspend fun deleteSideEffect(id: Long) = quit.deleteSideEffect(id)

    /** Awards any badges now earned and returns the new ones. */
    suspend fun awardBadges(today: LocalDate, schedule: DoseSchedule, quitDate: LocalDate?): List<String> {
        val earned = Badges.earned(today, schedule, quitDate, quit.cravingsBeaten())
        return earned.filter { id ->
            quit.award(BadgeEntity(id, System.currentTimeMillis(), Badges.byId(id).coins)) != -1L
        }
    }
}
