package com.shuvostechworld.sonicmemories.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "sonic_memories_prefs"
        private const val KEY_IS_FIRST_RUN = "is_first_run"
    }

    var isFirstRun: Boolean
        get() = sharedPreferences.getBoolean(KEY_IS_FIRST_RUN, true)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_IS_FIRST_RUN, value).apply()
        }
}
