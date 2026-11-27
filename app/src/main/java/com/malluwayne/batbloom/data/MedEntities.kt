package com.malluwayne.batbloom.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A medication the user takes, like the Health app's Medications: what it is, how it looks and when to take it.
 * [times] holds the scheduled times and amounts as "08:00*1;20:00*0.5" (see [com.malluwayne.batbloom.meds.MedSchedule]).
 */
@Entity(tableName = "meds")
data class MedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Tablet, Capsule, Liquid… see [com.malluwayne.batbloom.meds.MedForm]. */
    val form: String,
    /** Strength per unit (e.g. 500 for 500 mg); 0 when not set. */
    val strength: Double = 0.0,
    val unit: String = "mg",
    /** Tablet shape: Round, Oval, Oblong. */
    val shape: String = "Round",
    val color1: Long = 0xFFFFFFFF,
    val color2: Long = 0xFF7FB8E3,
    val background: Long = 0xFF2E3360,
    val notes: String? = null,
    /** Daily, Weekdays, Interval or AsNeeded. */
    val frequency: String = "Daily",
    /** Days of the week for Weekdays: bit 0 = Monday … bit 6 = Sunday. */
    val weekdays: Int = 0b1111111,
    /** For Interval: every N days, counted from [startDate]. */
    val intervalDays: Int = 2,
    val times: String = "08:00*1",
    /** ISO date (yyyy-MM-dd). */
    val startDate: String,
    val endDate: String? = null,
    /** Units left (tablets, mL…); negative when not tracked. */
    val stock: Double = -1.0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One logged dose. [slot] is the scheduled dose ("2026-11-24T08:00"), or null for an as-needed dose
 * (several can be logged a day). [status] is TAKEN or SKIPPED.
 */
@Entity(tableName = "med_log", indices = [Index(value = ["medId", "slot"], unique = true), Index("actedAt")])
data class MedLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val slot: String?,
    val status: String,
    val quantity: Double,
    val actedAt: Long,
)

@Dao
interface MedDao {
    @Insert suspend fun insert(m: MedEntity): Long
    @Update suspend fun update(m: MedEntity)
    @Query("DELETE FROM meds WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM med_log WHERE medId = :id") suspend fun deleteLogsFor(id: Long)
    @Query("SELECT * FROM meds ORDER BY archived, name COLLATE NOCASE") fun all(): Flow<List<MedEntity>>
    @Query("SELECT * FROM meds") suspend fun allNow(): List<MedEntity>
    @Query("SELECT * FROM meds WHERE id = :id") suspend fun get(id: Long): MedEntity?
    @Query("UPDATE meds SET stock = :stock WHERE id = :id") suspend fun setStock(id: Long, stock: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun log(l: MedLogEntity): Long
    @Query("SELECT * FROM med_log WHERE medId = :medId AND slot = :slot") suspend fun logFor(medId: Long, slot: String): MedLogEntity?
    @Query("SELECT * FROM med_log WHERE id = :id") suspend fun logById(id: Long): MedLogEntity?
    @Query("DELETE FROM med_log WHERE id = :id") suspend fun deleteLog(id: Long)
    @Query("UPDATE med_log SET actedAt = :at WHERE id = :id") suspend fun setLogTime(id: Long, at: Long)
    @Query("SELECT * FROM med_log WHERE actedAt >= :since OR slot >= :sinceSlot ORDER BY actedAt DESC") fun logsSince(since: Long, sinceSlot: String): Flow<List<MedLogEntity>>
    @Query("SELECT * FROM med_log WHERE slot IN (:slots)") suspend fun logsForSlots(slots: List<String>): List<MedLogEntity>
    @Query("SELECT * FROM med_log WHERE medId = :medId ORDER BY actedAt DESC LIMIT :limit") fun history(medId: Long, limit: Int): Flow<List<MedLogEntity>>
    /** Scheduled logs of one medication with slots in [from, to) ("2026-11-01" to "2026-12-01"). */
    /** Every log acted on since [since], newest first (the History screen). */
    @Query("SELECT * FROM med_log WHERE actedAt >= :since ORDER BY actedAt DESC") fun logsAfter(since: Long): Flow<List<MedLogEntity>>
    @Query("SELECT * FROM med_log WHERE medId = :medId AND slot >= :from AND slot < :to") fun logsInRange(medId: Long, from: String, to: String): Flow<List<MedLogEntity>>
    @Query("SELECT * FROM med_log WHERE status = 'TAKEN' AND slot IS NOT NULL") suspend fun takenScheduled(): List<MedLogEntity>
}
