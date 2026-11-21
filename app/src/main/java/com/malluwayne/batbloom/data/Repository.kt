package com.malluwayne.batbloom.data

import com.malluwayne.batbloom.focus.Rules
import com.malluwayne.batbloom.plants.PlantCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class Repository(private val db: AppDatabase) {
    val sessions: Flow<List<SessionEntity>> = db.sessions().finished()
    val allowedApps: Flow<Set<String>> = db.allowed().all().map { it.toSet() }
    val unlocked: Flow<Set<String>> = db.unlocks().all().map { list ->
        list.map { it.plantId }.toSet() + PlantCatalog.all.filter { it.cost == 0 }.map { it.id }
    }
    val coins: Flow<Int> = combine(db.sessions().earned(), db.unlocks().spent()) { e, s -> e - s }

    suspend fun startSession(s: SessionEntity): Long = db.sessions().insert(s)
    suspend fun finishSession(id: Long, success: Boolean, endedAt: Long, reason: String?): Int {
        val s = db.sessions().get(id) ?: return 0
        val coins = Rules.coinsFor(s.plannedMinutes, success)
        db.sessions().finish(id, if (success) Status.DONE else Status.FAILED, endedAt, coins, reason)
        return coins
    }
    suspend fun setCraving(id: Long, intensity: Int?, trigger: String?) = db.sessions().setCraving(id, intensity, trigger)
    suspend fun failStaleRunning() = db.sessions().failStaleRunning()
    suspend fun finishedSince(since: Long) = db.sessions().finishedSince(since)

    suspend fun unlock(plantId: String, cost: Int) = db.unlocks().insert(UnlockEntity(plantId, cost, System.currentTimeMillis()))

    suspend fun setAllowed(pkg: String, allowed: Boolean) =
        if (allowed) db.allowed().add(AllowedAppEntity(pkg)) else db.allowed().remove(pkg)
    suspend fun allowedNow(): Set<String> = db.allowed().allNow().toSet()
}
