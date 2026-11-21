package com.malluwayne.batbloom.focus

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** The live session, shared by the service, the UI and the widget. Lives as long as the process. */
object FocusEngine {
    data class Request(
        val minutes: Int,
        val tag: String,
        val subject: String?,
        val plantId: String,
        /** Non-null for a Pomodoro cycle; then [minutes] is ignored and the plan's focus length is used. */
        val plan: PomodoroPlan? = null,
        val cravingIntensity: Int? = null,
        val trigger: String? = null,
    )

    sealed interface State {
        data object Idle : State
        data class Running(
            val sessionId: Long,
            val request: Request,
            val minutes: Int,
            val startAt: Long,
            val endAt: Long,
            /** Pomodoro round (1-based), or null for a single session. */
            val round: Int? = null,
            val blooms: Int = 0,
            val coins: Int = 0,
            /** Seconds left before the session fails, while the user is in a non-allowed app. */
            val warningSeconds: Int? = null,
            val warningApp: String? = null,
        ) : State {
            val plantId get() = request.plantId
            val tag get() = request.tag
            val subject get() = request.subject
        }
        data class Break(
            val request: Request,
            val startAt: Long,
            val endAt: Long,
            val nextRound: Int,
            val long: Boolean,
            val blooms: Int,
            val coins: Int,
        ) : State
        data class Finished(
            val success: Boolean,
            val request: Request,
            val minutes: Int,
            val coins: Int,
            val reason: String?,
            /** Pomodoro: how many rounds bloomed out of how many planned. */
            val blooms: Int = if (success) 1 else 0,
            val rounds: Int? = null,
        ) : State
    }

    val state = MutableStateFlow<State>(State.Idle)

    val isActive get() = state.value is State.Running || state.value is State.Break

    /** Set by the running service so the alarm receiver can make it check the clock. */
    @Volatile var alarmTick: (() -> Unit)? = null

    @Volatile internal var pending: Request? = null

    fun start(context: Context, request: Request) {
        if (isActive) return
        pending = request
        ContextCompat.startForegroundService(
            context, Intent(context, FocusService::class.java).setAction(FocusService.ACTION_START),
        )
    }

    fun giveUp(context: Context) {
        if (!isActive) return
        context.startService(Intent(context, FocusService::class.java).setAction(FocusService.ACTION_GIVE_UP))
    }

    fun dismiss() {
        if (state.value is State.Finished) state.value = State.Idle
    }
}
