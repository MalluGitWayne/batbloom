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
    @Query("UPDATE sessions SET status = :status, endedAt = :endedAt, failReason = :reason WHERE id = :id")
    suspend fun finish(id: Long, status: String, endedAt: Long, reason: String?)
    /** Wilted plants can be cleared from the garden. */
    @Query("DELETE FROM sessions WHERE status = 'FAILED'") suspend fun clearWilted(): Int
    @Query("DELETE FROM sessions WHERE id = :id AND status = 'FAILED'") suspend fun removeWilted(id: Long): Int
    @Query("UPDATE sessions SET plannedMinutes = :minutes WHERE id = :id")
    suspend fun setMinutes(id: Long, minutes: Int)
    @Query("UPDATE sessions SET status = 'FAILED', endedAt = startedAt, failReason = 'interrupted' WHERE status = 'RUNNING'")
    suspend fun failStaleRunning()
    @Query("SELECT * FROM sessions WHERE status != 'RUNNING' ORDER BY startedAt DESC") fun finished(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE status != 'RUNNING' AND startedAt >= :since") suspend fun finishedSince(since: Long): List<SessionEntity>
}

@Dao
interface UnlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(u: UnlockEntity)
    @Query("SELECT * FROM unlocks") fun all(): Flow<List<UnlockEntity>>
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
        DoseLogEntity::class, BadgeEntity::class,
        MedEntity::class, MedLogEntity::class,
    ],
    version = 4,
    // v3 -> v4 only adds the medication tables.
    autoMigrations = [androidx.room.AutoMigration(from = 3, to = 4)],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun unlocks(): UnlockDao
    abstract fun allowed(): AllowedDao
    abstract fun quit(): QuitDao
    abstract fun backup(): BackupDao
    abstract fun meds(): MedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "batbloom.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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

/** v2 → v3: drops the slips and side-effect tables and the coin and craving columns (tables rebuilt, data kept). */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `slips`")
        db.execSQL("DROP TABLE IF EXISTS `side_effects`")
        db.execSQL("CREATE TABLE `sessions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `plannedMinutes` INTEGER NOT NULL, `endedAt` INTEGER, `status` TEXT NOT NULL, `tag` TEXT NOT NULL, `subject` TEXT, `plantId` TEXT NOT NULL, `failReason` TEXT, `round` INTEGER)")
        db.execSQL("INSERT INTO `sessions_new` (id, startedAt, plannedMinutes, endedAt, status, tag, subject, plantId, failReason, round) SELECT id, startedAt, plannedMinutes, endedAt, status, tag, subject, plantId, failReason, round FROM `sessions`")
        db.execSQL("DROP TABLE `sessions`")
        db.execSQL("ALTER TABLE `sessions_new` RENAME TO `sessions`")
        db.execSQL("CREATE TABLE `badges_new` (`id` TEXT NOT NULL, `earnedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `badges_new` (id, earnedAt) SELECT id, earnedAt FROM `badges`")
        db.execSQL("DROP TABLE `badges`")
        db.execSQL("ALTER TABLE `badges_new` RENAME TO `badges`")
    }
}
