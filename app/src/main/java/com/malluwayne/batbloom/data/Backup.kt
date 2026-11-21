package com.malluwayne.batbloom.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Everything Batbloom stores, in one file you can keep and restore (new phone, reinstall). */
data class BackupData(
    val sessions: List<SessionEntity>,
    val unlocks: List<UnlockEntity>,
    val allowed: List<AllowedAppEntity>,
    val doses: List<DoseLogEntity>,
    val badges: List<BadgeEntity>,
    val prefs: Map<String, Any?>,
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM sessions WHERE status != 'RUNNING'") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM unlocks") suspend fun unlocks(): List<UnlockEntity>
    @Query("SELECT * FROM allowed_apps") suspend fun allowed(): List<AllowedAppEntity>
    @Query("SELECT * FROM dose_log") suspend fun doses(): List<DoseLogEntity>
    @Query("SELECT * FROM badges") suspend fun badges(): List<BadgeEntity>

    @Insert suspend fun putSessions(l: List<SessionEntity>)
    @Insert suspend fun putUnlocks(l: List<UnlockEntity>)
    @Insert suspend fun putAllowed(l: List<AllowedAppEntity>)
    @Insert suspend fun putDoses(l: List<DoseLogEntity>)
    @Insert suspend fun putBadges(l: List<BadgeEntity>)
}

object Backup {
    /** 2: no coins, cravings, slips or side effects. Older files still restore (extra fields are ignored). */
    const val FORMAT = 2
    const val PREFS_FILE = "batbloom"

    suspend fun collect(context: Context, db: AppDatabase): BackupData {
        val d = db.backup()
        val sp = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        return BackupData(d.sessions(), d.unlocks(), d.allowed(), d.doses(), d.badges(), sp.all)
    }

    /** Replaces all data with the backup's. Settings are written too; the app restarts to load them. */
    suspend fun restore(context: Context, db: AppDatabase, b: BackupData) {
        db.withTransaction {
            db.clearAllTables()
            val d = db.backup()
            d.putSessions(b.sessions); d.putUnlocks(b.unlocks); d.putAllowed(b.allowed)
            d.putDoses(b.doses); d.putBadges(b.badges)
        }
        val e = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear()
        b.prefs.forEach { (k, v) ->
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, v)
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
        e.commit()
    }

    // --- JSON (pure, unit tested) ---

    fun toJson(b: BackupData): String = JSONObject().apply {
        put("app", "batbloom")
        put("format", FORMAT)
        put("sessions", JSONArray(b.sessions.map { s ->
            JSONObject().put("id", s.id).put("startedAt", s.startedAt).put("plannedMinutes", s.plannedMinutes)
                .put("endedAt", s.endedAt ?: JSONObject.NULL).put("status", s.status).put("tag", s.tag)
                .put("subject", s.subject ?: JSONObject.NULL).put("plantId", s.plantId)
                .put("failReason", s.failReason ?: JSONObject.NULL).put("round", s.round ?: JSONObject.NULL)
        }))
        put("unlocks", JSONArray(b.unlocks.map { JSONObject().put("plantId", it.plantId).put("cost", it.cost).put("unlockedAt", it.unlockedAt) }))
        put("allowed", JSONArray(b.allowed.map { it.packageName }))
        put("doses", JSONArray(b.doses.map {
            JSONObject().put("key", it.key).put("day", it.day).put("mg", it.mg).put("tablets", it.tablets).put("status", it.status).put("actedAt", it.actedAt)
        }))
        put("badges", JSONArray(b.badges.map { JSONObject().put("id", it.id).put("earnedAt", it.earnedAt) }))
        put("prefs", JSONObject().apply {
            b.prefs.forEach { (k, v) ->
                val (t, value) = when (v) {
                    is Boolean -> "b" to v
                    is Int -> "i" to v
                    is Long -> "l" to v
                    is Float -> "f" to v.toDouble()
                    is String -> "s" to v
                    is Set<*> -> "set" to JSONArray(v.map { it.toString() })
                    else -> return@forEach
                }
                put(k, JSONObject().put("t", t).put("v", value))
            }
        })
    }.toString(2)

    class BadBackup(msg: String) : Exception(msg)

    fun fromJson(text: String): BackupData {
        val j = runCatching { JSONObject(text) }.getOrElse { throw BadBackup("This isn't a Batbloom backup file.") }
        if (j.optString("app") != "batbloom") throw BadBackup("This isn't a Batbloom backup file.")
        if (j.optInt("format") > FORMAT) throw BadBackup("This backup is from a newer Batbloom. Update the app first.")
        fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k)
        fun JSONObject.int(k: String) = if (isNull(k) || !has(k)) null else getInt(k)
        fun JSONObject.long(k: String) = if (isNull(k) || !has(k)) null else getLong(k)
        fun arr(k: String): List<JSONObject> = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
        val prefs = mutableMapOf<String, Any?>()
        j.optJSONObject("prefs")?.let { p ->
            p.keys().forEach { k ->
                val o = p.getJSONObject(k)
                prefs[k] = when (o.getString("t")) {
                    "b" -> o.getBoolean("v")
                    "i" -> o.getInt("v")
                    "l" -> o.getLong("v")
                    "f" -> o.getDouble("v").toFloat()
                    "s" -> o.getString("v")
                    "set" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    else -> null
                }
            }
        }
        return BackupData(
            sessions = arr("sessions").map {
                SessionEntity(
                    it.getLong("id"), it.getLong("startedAt"), it.getInt("plannedMinutes"), it.long("endedAt"), it.getString("status"),
                    it.getString("tag"), it.str("subject"), it.getString("plantId"), it.str("failReason"), it.int("round"),
                )
            },
            unlocks = arr("unlocks").map { UnlockEntity(it.getString("plantId"), it.getInt("cost"), it.getLong("unlockedAt")) },
            allowed = j.optJSONArray("allowed")?.let { a -> (0 until a.length()).map { AllowedAppEntity(a.getString(it)) } } ?: emptyList(),
            doses = arr("doses").map {
                DoseLogEntity(it.getString("key"), it.getInt("day"), it.getDouble("mg"), it.getDouble("tablets"), it.getString("status"), it.getLong("actedAt"))
            },
            badges = arr("badges").map { BadgeEntity(it.getString("id"), it.getLong("earnedAt")) },
            prefs = prefs,
        )
    }
}
