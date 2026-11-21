package com.malluwayne.batbloom.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object DoseStatus {
    const val TAKEN = "TAKEN"
    const val SKIPPED = "SKIPPED"
}

/** One row per scheduled dose the user acted on. [key] is the dose slot, e.g. "2026-11-21-AM". */
@Entity(tableName = "dose_log")
data class DoseLogEntity(
    @PrimaryKey val key: String,
    val day: Int,
    val mg: Double,
    val tablets: Double,
    val status: String,
    val actedAt: Long,
)

/** A cigarette smoked after the quit day. Logged kindly; it never resets the timer. */
@Entity(tableName = "slips")
data class SlipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val count: Int,
    val note: String? = null,
)

@Entity(tableName = "side_effects")
data class SideEffectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,
    /** 1 mild, 2 moderate, 3 severe. */
    val severity: Int,
    val note: String? = null,
)

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val earnedAt: Long,
    val coins: Int,
)

@Dao
interface QuitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun logDose(d: DoseLogEntity)
    @Query("SELECT * FROM dose_log WHERE `key` = :key") suspend fun dose(key: String): DoseLogEntity?
    @Query("DELETE FROM dose_log WHERE `key` = :key") suspend fun deleteDose(key: String)
    @Query("SELECT * FROM dose_log ORDER BY `key` DESC") fun doses(): Flow<List<DoseLogEntity>>

    @Insert suspend fun addSlip(s: SlipEntity)
    @Query("DELETE FROM slips WHERE id = :id") suspend fun deleteSlip(id: Long)
    @Query("SELECT * FROM slips ORDER BY at DESC") fun slips(): Flow<List<SlipEntity>>

    @Insert suspend fun addSideEffect(s: SideEffectEntity)
    @Query("DELETE FROM side_effects WHERE id = :id") suspend fun deleteSideEffect(id: Long)
    @Query("SELECT * FROM side_effects ORDER BY at DESC") fun sideEffects(): Flow<List<SideEffectEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun award(b: BadgeEntity): Long
    @Query("SELECT * FROM badges") fun badges(): Flow<List<BadgeEntity>>
    @Query("SELECT COALESCE(SUM(coins), 0) FROM badges") fun badgeCoins(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sessions WHERE tag = 'Craving' AND status = 'DONE'") suspend fun cravingsBeaten(): Int
}
