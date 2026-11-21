package com.malluwayne.batbloom.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object Status {
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val plannedMinutes: Int,
    val endedAt: Long? = null,
    val status: String = Status.RUNNING,
    val tag: String,
    /** FMGE subject when tag is FMGE, else null. */
    val subject: String? = null,
    val plantId: String,
    /** Why it failed: the app that was opened, "gave up", or "interrupted". */
    val failReason: String? = null,
    /** Pomodoro round number (1-based), or null for a single session. */
    val round: Int? = null,
)

@Entity(tableName = "unlocks")
data class UnlockEntity(
    @PrimaryKey val plantId: String,
    val cost: Int,
    val unlockedAt: Long,
)

@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
)
