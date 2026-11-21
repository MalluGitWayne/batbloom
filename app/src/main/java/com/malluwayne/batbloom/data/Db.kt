package com.malluwayne.batbloom.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionEntity): Long
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: Long): SessionEntity?
    @Query("UPDATE sessions SET status = :status, endedAt = :endedAt, coins = :coins, failReason = :reason WHERE id = :id")
    suspend fun finish(id: Long, status: String, endedAt: Long, coins: Int, reason: String?)
    @Query("UPDATE sessions SET cravingIntensity = :intensity, cravingTrigger = :trigger WHERE id = :id")
    suspend fun setCraving(id: Long, intensity: Int?, trigger: String?)
    @Query("UPDATE sessions SET status = 'FAILED', endedAt = startedAt, failReason = 'interrupted' WHERE status = 'RUNNING'")
    suspend fun failStaleRunning()
    @Query("SELECT * FROM sessions WHERE status != 'RUNNING' ORDER BY startedAt DESC") fun finished(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE status != 'RUNNING' AND startedAt >= :since") suspend fun finishedSince(since: Long): List<SessionEntity>
    @Query("SELECT COALESCE(SUM(coins), 0) FROM sessions WHERE status = 'DONE'") fun earned(): Flow<Int>
}

@Dao
interface UnlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(u: UnlockEntity)
    @Query("SELECT * FROM unlocks") fun all(): Flow<List<UnlockEntity>>
    @Query("SELECT COALESCE(SUM(cost), 0) FROM unlocks") fun spent(): Flow<Int>
}

@Dao
interface AllowedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(a: AllowedAppEntity)
    @Query("DELETE FROM allowed_apps WHERE packageName = :pkg") suspend fun remove(pkg: String)
    @Query("SELECT packageName FROM allowed_apps") fun all(): Flow<List<String>>
    @Query("SELECT packageName FROM allowed_apps") suspend fun allNow(): List<String>
}

@Database(entities = [SessionEntity::class, UnlockEntity::class, AllowedAppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun unlocks(): UnlockDao
    abstract fun allowed(): AllowedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "batbloom.db").build()
    }
}
