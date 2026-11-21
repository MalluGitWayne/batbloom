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
    /** Wilted plants can be cleared from the garden (they earned no coins, so nothing else changes). */
    @Query("DELETE FROM sessions WHERE status = 'FAILED'") suspend fun clearWilted(): Int
    @Query("DELETE FROM sessions WHERE id = :id AND status = 'FAILED'") suspend fun removeWilted(id: Long): Int
    @Query("UPDATE sessions SET plannedMinutes = :minutes WHERE id = :id")
    suspend fun setMinutes(id: Long, minutes: Int)
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

@Database(
    entities = [
        SessionEntity::class, UnlockEntity::class, AllowedAppEntity::class,
        DoseLogEntity::class, SlipEntity::class, SideEffectEntity::class, BadgeEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun unlocks(): UnlockDao
    abstract fun allowed(): AllowedDao
    abstract fun quit(): QuitDao
    abstract fun backup(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "batbloom.db").addMigrations(MIGRATION_1_2).build()
    }
}

/** v1 → v2: adds the quit-smoking tables (dose log, slips, side effects, badges). SQL copied from schemas/2.json. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dose_log` (`key` TEXT NOT NULL, `day` INTEGER NOT NULL, `mg` REAL NOT NULL, `tablets` REAL NOT NULL, `status` TEXT NOT NULL, `actedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `slips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `count` INTEGER NOT NULL, `note` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `side_effects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `severity` INTEGER NOT NULL, `note` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `badges` (`id` TEXT NOT NULL, `earnedAt` INTEGER NOT NULL, `coins` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}
