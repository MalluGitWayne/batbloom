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

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val earnedAt: Long,
)

@Dao
interface QuitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun logDose(d: DoseLogEntity)
    @Query("SELECT * FROM dose_log WHERE `key` = :key") suspend fun dose(key: String): DoseLogEntity?
    @Query("DELETE FROM dose_log WHERE `key` = :key") suspend fun deleteDose(key: String)
    @Query("SELECT * FROM dose_log ORDER BY `key` DESC") fun doses(): Flow<List<DoseLogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun award(b: BadgeEntity): Long
    @Query("SELECT * FROM badges") fun badges(): Flow<List<BadgeEntity>>
}
