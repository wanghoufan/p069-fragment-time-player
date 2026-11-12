package com.wanghoufan.fragmentplayer.timer

import android.content.Context
import com.wanghoufan.fragmentplayer.core.model.TimerPhase

internal data class StoredTimerSession(
    val sessionId: String? = null,
    val presetId: Long? = null,
    val presetName: String = "",
    val targetSec: Int = 0,
    val deadlineWall: Long = 0L,
    val remainingMs: Long = 0L,
    val phase: TimerPhase = TimerPhase.IDLE,
    val countdownOnly: Boolean = false,
    val alerted: Boolean = false,
    val alertPosted: Boolean = false,
    val startedWall: Long = 0L,
    val timeFlow: Int = 1
)

internal object TimerSessionStore {

    private const val PREFS_NAME = "timer_session"
    private const val KEY_SESSION_ID = "sessionId"
    private const val KEY_PRESET_ID = "presetId"
    private const val KEY_NAME = "name"
    private const val KEY_TARGET_SEC = "targetSec"
    private const val KEY_DEADLINE_WALL = "deadlineWall"
    private const val KEY_REMAINING_MS = "remainingMs"
    private const val KEY_PHASE = "phase"
    private const val KEY_COUNTDOWN_ONLY = "countdownOnly"
    private const val KEY_ALERTED = "alerted"
    private const val KEY_ALERT_POSTED = "alertPosted"
    private const val KEY_STARTED_WALL = "startedWall"
    private const val KEY_TIME_FLOW = "timeFlow"
    private const val KEY_INTERRUPT_NOTICE = "interrupt_notice"

    private val ACTIVE_KEYS = listOf(
        KEY_SESSION_ID,
        KEY_PRESET_ID,
        KEY_NAME,
        KEY_TARGET_SEC,
        KEY_DEADLINE_WALL,
        KEY_REMAINING_MS,
        KEY_PHASE,
        KEY_COUNTDOWN_ONLY,
        KEY_ALERTED,
        KEY_ALERT_POSTED,
        KEY_STARTED_WALL,
        KEY_TIME_FLOW
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): StoredTimerSession {
        val p = prefs(context)
        val presetId = p.getLong(KEY_PRESET_ID, -1L).takeIf { it >= 0L }
        val phase = runCatching { TimerPhase.valueOf(p.getString(KEY_PHASE, null) ?: "") }
            .getOrDefault(TimerPhase.IDLE)
        return StoredTimerSession(
            sessionId = p.getString(KEY_SESSION_ID, null),
            presetId = presetId,
            presetName = p.getString(KEY_NAME, "") ?: "",
            targetSec = p.getInt(KEY_TARGET_SEC, 0),
            deadlineWall = p.getLong(KEY_DEADLINE_WALL, 0L),
            remainingMs = p.getLong(KEY_REMAINING_MS, 0L),
            phase = phase,
            countdownOnly = p.getBoolean(KEY_COUNTDOWN_ONLY, false),
            alerted = p.getBoolean(KEY_ALERTED, false),
            alertPosted = p.getBoolean(KEY_ALERT_POSTED, false),
            startedWall = p.getLong(KEY_STARTED_WALL, 0L),
            timeFlow = p.getInt(KEY_TIME_FLOW, 1)
        )
    }

    fun save(context: Context, session: StoredTimerSession) {
        prefs(context).edit()
            .putString(KEY_SESSION_ID, session.sessionId)
            .putLong(KEY_PRESET_ID, session.presetId ?: -1L)
            .putString(KEY_NAME, session.presetName)
            .putInt(KEY_TARGET_SEC, session.targetSec)
            .putLong(KEY_DEADLINE_WALL, session.deadlineWall)
            .putLong(KEY_REMAINING_MS, session.remainingMs)
            .putString(KEY_PHASE, session.phase.name)
            .putBoolean(KEY_COUNTDOWN_ONLY, session.countdownOnly)
            .putBoolean(KEY_ALERTED, session.alerted)
            .putBoolean(KEY_ALERT_POSTED, session.alertPosted)
            .putLong(KEY_STARTED_WALL, session.startedWall)
            .putInt(KEY_TIME_FLOW, session.timeFlow)
            .commit()
    }

    fun clearSession(context: Context) {
        val editor = prefs(context).edit()
        ACTIVE_KEYS.forEach { editor.remove(it) }
        editor.commit()
    }

    fun isAlertPosted(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT_POSTED, false)

    fun markAlertPosted(context: Context): Boolean =
        prefs(context).edit().putBoolean(KEY_ALERT_POSTED, true).commit()

    fun resetAlertPosted(context: Context) {
        prefs(context).edit().remove(KEY_ALERT_POSTED).commit()
    }

    fun readNotice(context: Context): String? =
        prefs(context).getString(KEY_INTERRUPT_NOTICE, null)

    fun writeNotice(context: Context, notice: String) {
        prefs(context).edit().putString(KEY_INTERRUPT_NOTICE, notice).commit()
    }

    fun clearNotice(context: Context) {
        prefs(context).edit().remove(KEY_INTERRUPT_NOTICE).commit()
    }
}