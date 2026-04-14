package com.example.cricarena.ui.fantasyteam

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers the last match used for fantasy. Bottom navigation does not pass [match_id],
 * so [FantasyTeamFragment] falls back to this after Home → Join has set it.
 * Persisted so it survives process death (cold start, reinstall from Studio).
 */
object FantasyMatchSession {

    private const val PREFS_NAME = "fantasy_match_session"
    private const val KEY_LAST_MATCH_ID = "last_match_id"

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * Idempotent; call from [android.app.Activity.onCreate] or before first [remember]/[lastMatchId] use.
     */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    val lastMatchId: String
        get() = prefs?.getString(KEY_LAST_MATCH_ID, "").orEmpty()

    fun remember(matchId: String) {
        if (matchId.isBlank()) return
        val p = prefs ?: return
        p.edit().putString(KEY_LAST_MATCH_ID, matchId).apply()
    }
}
